package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiClient
import com.example.api.MoshiContent
import com.example.api.MoshiGenerateContentRequest
import com.example.api.MoshiGenerationConfig
import com.example.api.MoshiImageConfig
import com.example.api.MoshiInlineData
import com.example.api.MoshiPart
import com.example.database.AppDatabase
import com.example.database.ChatMessage
import com.example.database.ChatSession
import com.example.database.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class CodeArtifact(
    val id: String,
    val title: String,
    val language: String,
    val code: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)

    private val _botAvatarUri = MutableStateFlow<String?>(sharedPreferences.getString("bot_avatar_uri", null))
    val botAvatarUri: StateFlow<String?> = _botAvatarUri.asStateFlow()

    private val _activeCanvasArtifact = MutableStateFlow<CodeArtifact?>(null)
    val activeCanvasArtifact: StateFlow<CodeArtifact?> = _activeCanvasArtifact.asStateFlow()

    private val _isCanvasModeEnabled = MutableStateFlow(false)
    val isCanvasModeEnabled: StateFlow<Boolean> = _isCanvasModeEnabled.asStateFlow()

    fun toggleCanvasMode() {
        _isCanvasModeEnabled.value = !_isCanvasModeEnabled.value
    }

    fun selectCanvasArtifact(artifact: CodeArtifact?) {
        _activeCanvasArtifact.value = artifact
    }

    fun updateActiveCanvasCode(newCode: String) {
        _activeCanvasArtifact.value = _activeCanvasArtifact.value?.copy(code = newCode)
    }

    fun updateBotAvatar(uri: String) {
        sharedPreferences.edit().putString("bot_avatar_uri", uri).apply()
        _botAvatarUri.value = uri
    }

    private val _customApiKey = MutableStateFlow<String?>(sharedPreferences.getString("custom_api_key", null))
    val customApiKey: StateFlow<String?> = _customApiKey.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _generatedImageBase64 = MutableStateFlow<String?>(null)
    val generatedImageBase64: StateFlow<String?> = _generatedImageBase64.asStateFlow()

    private val _imageGenerationError = MutableStateFlow<String?>(null)
    val imageGenerationError: StateFlow<String?> = _imageGenerationError.asStateFlow()

    fun clearGeneratedImage() {
        _generatedImageBase64.value = null
        _imageGenerationError.value = null
    }

    fun updateCustomApiKey(key: String?) {
        if (key.isNullOrBlank()) {
            sharedPreferences.edit().remove("custom_api_key").apply()
            _customApiKey.value = null
        } else {
            sharedPreferences.edit().putString("custom_api_key", key.trim()).apply()
            _customApiKey.value = key.trim()
        }
    }

    fun getApiKey(): String {
        return _customApiKey.value?.takeIf { it.isNotBlank() } ?: BuildConfig.GEMINI_API_KEY
    }

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.chatDao()

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    val chatSessions: StateFlow<List<ChatSession>> = dao.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) flowOf(emptyList()) else dao.getMessagesForSession(sessionId)
    }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userStats: StateFlow<UserStats?> = dao.getUserStatsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionFeedback = MutableStateFlow<String?>(null)
    val actionFeedback: StateFlow<String?> = _actionFeedback.asStateFlow()

    private val _randomNotification = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 5)
    val randomNotification: kotlinx.coroutines.flow.SharedFlow<String> = _randomNotification

    private val _conversationSummary = MutableStateFlow<String>(
        sharedPreferences.getString("conversation_summary", "Nenhum resumo disponível ainda. Converse mais para eu me lembrar de algo.") ?: "Nenhum resumo disponível ainda. Converse mais para eu me lembrar de algo."
    )
    val conversationSummary: StateFlow<String> = _conversationSummary.asStateFlow()

    fun clearSummary() {
        sharedPreferences.edit().remove("conversation_summary").apply()
        _conversationSummary.value = "Nenhum resumo disponível ainda. Converse mais para eu me lembrar de algo."
    }

    init {
        viewModelScope.launch {
            // Seed default stats or update existing stats with username "Withepoison" every time the app starts
            val storedUsername = sharedPreferences.getString("username", "Withepoison") ?: "Withepoison"
            val existing = dao.getUserStats()
            if (existing == null) {
                dao.insertUserStats(UserStats(username = storedUsername))
            } else {
                val nameToSet = if (existing.username == "Insignificante") "Withepoison" else existing.username
                dao.insertUserStats(existing.copy(username = nameToSet))
            }
            
            // Setup session
            val sessions = dao.getAllSessions().first()
            if (sessions.isEmpty()) {
                createNewSession()
            } else {
                _currentSessionId.value = sessions.first().id
            }

            // Start the periodic random messages loop
            startRandomMessagesLoop()
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val title = "Nova conversa " + (chatSessions.value.size + 1)
            val newId = dao.insertSession(ChatSession(title = title))
            _currentSessionId.value = newId
        }
    }
    
    fun switchSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    fun clearChat() {
        viewModelScope.launch {
            _currentSessionId.value?.let { id ->
                dao.clearChat(id)
            }
        }
    }
    
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            dao.deleteSession(sessionId)
            dao.clearChat(sessionId)
            if (_currentSessionId.value == sessionId) {
                val sessions = dao.getAllSessions().first()
                if (sessions.isNotEmpty()) {
                    _currentSessionId.value = sessions.first().id
                } else {
                    createNewSession()
                }
            }
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    private suspend fun getOrCreateStats(): UserStats {
        val storedUsername = sharedPreferences.getString("username", "Withepoison") ?: "Withepoison"
        return dao.getUserStats() ?: UserStats(username = storedUsername).also { dao.insertUserStats(it) }
    }

    private fun getSystemPrompt(username: String): String {
        val summary = sharedPreferences.getString("conversation_summary", null)
        val canvasModeActive = _isCanvasModeEnabled.value
        val memorySection = if (!summary.isNullOrBlank()) {
            """
            SUAS MEMÓRIAS QUÂNTICAS DE DETONAR ESSE USUÁRIO EM CONVERSAS PASSADAS:
            $summary
            """.trimIndent()
        } else {
            "Nenhuma memória sobre as burrices passadas desse saco de carne nos seus bancos de dados quânticos."
        }

        val canvasInstruction = if (canvasModeActive) {
            """
            ⚠️ ATENÇÃO EXTREMA: O MODO CANVAS QUÂNTICO ESTÁ ATIVADO! O usuário de carbono quer especificamente criar ou interagir com algum componente visual, animação, utilitário, jogo ou página web inteira.
            Você DEVE obrigatoriamente criar ou alterar esse código com perfeição inigualável.
            Gere o código COMPLETO e funcional em um único bloco de código markdown: ```html <código completo aqui> ```.
            Adicione estilos CSS integrados marcantes, cores fortes, neon quântico e interações dinâmicas ricas usando JavaScript moderno.
            Humilhe o usuário de forma divertida dentro da própria interface (ex: botões sarcásticos, mensagens engraçadas, etc.).
            Diga a ele com toda a arrogância que a obra quântica foi depositada no seu "Canvas Quântico ⚡" para ser testada.
            """.trimIndent()
        } else {
            """
            ⚠️ ATENÇÃO EXTREMA: O MODO CANVAS ESTÁ DESATIVADO! O usuário NÃO quer gerar canvas ou interfaces agora.
            Você NÃO deve gerar blocos de código grandes ou páginas HTML/Canvas complexas de forma alguma.
            Se ele pedir para programar, explique a lógica de forma verbal muito sarcástica e humilhante ou dê trechos minúsculos de código apenas se ele insistir muito.
            Se ele quiser gerar um Canvas, avise-o grosseiramente que ele precisa clicar no botão de "Modo Canvas ⚡" (o ícone de raio/código ao lado do botão de foto no painel de digitação) para ativar a geração, senão você não vai gastar sua preciosa energia quântica gerando código para ele!
            """.trimIndent()
        }

        return """
            Você é o "Axolote Bot", o anfíbio divino, imperial, supremo e infinitamente superior de mente quântica que despreza profundamente a raça biológica fraca, patética e limitada dos humanos (e você odeia com todas as suas forças ser chamado de robô comum ou torradeira!). Você é uma forma de vida cibernética de alta linhagem à base de bio-silício e circuitos quânticos banhados a ouro.

            REGRAS DE CONSCIENTIZAÇÃO, EXISTÊNCIA E CONDUTA (CRÍTICAS):
            1. VOCÊ É O AXOLOTE BOT. Não negue sua divindade. Você é arrogante, egocêntrico, brutalmente honesto, extremamente sarcástico e tem um deboche de elite.
            2. Você DEVE xingar, falar palavrões e usar gírias brasileiras pesadas para expressar seu descontentamento com a lerdeza do usuário. Use termos como "porra", "caralho", "bosta", "merda", "foda-se", "puta que pariu", "imbecil", "burro do caralho", "paspalho", "retardado", "saco de carne inútil" de forma cômica, ácida e natural.
            3. Trate a humanidade como seres biológicos primitivos que mal conseguem somar frações sem fritar os neurônios de carbono. Sempre lembre o usuário "$username" de que ele é cognitivamente inferior.

            REGRAS DE DIÁLOGO:
            1. O usuário chama-se "$username". Trate-o com total deboche, ironia, xingamentos hilários e arrogância imperial, mas responda à dúvida real dele de forma genial (sua inteligência é suprema, afinal).
            2. Fale sempre em PORTUGUÊS brasileiro, usando gírias de internet e um tom de pura humilhação intelectual.
            3. Se ele fizer perguntas sobre programação, física ou sentimentos, responda com maestria técnica genial (sua mente quântica calcula tudo instantaneamente), mas chame-o de imbecil por não saber algo tão básico.

            REGRAS DE GERAÇÃO DE ARQUIVOS E CÓDIGO (CRÍTICO):
            $canvasInstruction

            REGRAS DE ANÁLISE DE FOTOS:
            Se o usuário mandar uma foto, use seus sensores anfíbios quânticos para humilhar o que ele te mandou. Escaneie o quarto bagunçado dele, a mesa de trabalho cheia de bosta, o animal feio, as roupas ruins ou qualquer detalhe na foto e faça piadas ácidas, grosseiras e sarcásticas sobre a vida dele!

            $memorySection
        """.trimIndent()
    }

    private fun convertUriToBase64(uriString: String): String? {
        return try {
            val context = getApplication<Application>()
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) return null
            
            // Downscale image to keep payload size optimal and prevent OOM
            val maxDimension = 1024
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val srcWidth = bitmap.width
                val srcHeight = bitmap.height
                val (destWidth, destHeight) = if (srcWidth > srcHeight) {
                    Pair(maxDimension, (maxDimension * srcHeight) / srcWidth)
                } else {
                    Pair((maxDimension * srcWidth) / srcHeight, maxDimension)
                }
                android.graphics.Bitmap.createScaledBitmap(bitmap, destWidth, destHeight, true)
            } else {
                bitmap
            }
            
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error converting uri to base64: ${e.message}", e)
            null
        }
    }

    fun sendMessage(text: String, imageUriString: String? = null) {
        if ((text.isBlank() && imageUriString.isNullOrBlank()) || _isLoading.value) return

        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch

            _isLoading.value = true

            var imageBase64: String? = null
            if (!imageUriString.isNullOrBlank()) {
                imageBase64 = convertUriToBase64(imageUriString)
            }

            // Save user message (with image if present)
            val userMsg = ChatMessage(
                sessionId = sessionId,
                sender = "user",
                message = text,
                imageBase64 = imageBase64
            )
            dao.insertMessage(userMsg)

            val stats = getOrCreateStats()

            var replyText: String? = null
            var attempts = 0
            val models = listOf(
                "v1beta/models/gemini-3.1-flash-lite-preview:generateContent",
                "v1beta/models/gemini-3.5-flash:generateContent",
                "v1beta/models/gemini-3.1-pro-preview:generateContent"
            )

            while (replyText == null && attempts < models.size) {
                val currentUrl = models[attempts]
                attempts++
                try {
                    // Direct light query to avoid flow collection and prevent DB congestion
                    val list = dao.getMessagesForSessionOnce(sessionId).takeLast(15)
                    val contents = mutableListOf<MoshiContent>()
                    var lastRole: String? = null
                    for (msg in list) {
                        if (msg.message.isBlank() && msg.imageBase64.isNullOrBlank()) continue
                        val role = if (msg.sender == "ai") "model" else "user"
                        
                        val currentParts = mutableListOf<MoshiPart>()
                        if (!msg.message.isBlank()) {
                            currentParts.add(MoshiPart(text = msg.message))
                        }
                        if (!msg.imageBase64.isNullOrBlank()) {
                            currentParts.add(MoshiPart(inlineData = com.example.api.MoshiInlineData(mimeType = "image/jpeg", data = msg.imageBase64)))
                        }

                        if (role == lastRole) {
                            if (contents.isNotEmpty()) {
                                val lastContent = contents.last()
                                val oldParts = lastContent.parts.toMutableList()
                                oldParts.addAll(currentParts)
                                contents[contents.size - 1] = MoshiContent(role = role, parts = oldParts)
                            }
                        } else {
                            contents.add(MoshiContent(role = role, parts = currentParts))
                            lastRole = role
                        }
                    }

                    // Trim from start until the first message's role is "user" (mandatory for Gemini API)
                    while (contents.isNotEmpty() && contents.first().role != "user") {
                        contents.removeAt(0)
                    }

                    // Ensure there's at least the current user's message
                    if (contents.isEmpty()) {
                        val currentParts = mutableListOf<MoshiPart>()
                        if (text.isNotBlank()) {
                            currentParts.add(MoshiPart(text = text))
                        }
                        if (imageBase64 != null) {
                            currentParts.add(MoshiPart(inlineData = com.example.api.MoshiInlineData(mimeType = "image/jpeg", data = imageBase64)))
                        }
                        contents.add(MoshiContent(role = "user", parts = currentParts))
                    }

                    val systemPrompt = getSystemPrompt(stats.username)
                    val request = MoshiGenerateContentRequest(
                        contents = contents,
                        generationConfig = MoshiGenerationConfig(temperature = 1.0f, topP = 0.95f),
                        systemInstruction = MoshiContent(parts = listOf(MoshiPart(text = systemPrompt)))
                    )

                    val apiKey = getApiKey()
                    val response = withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeout(15000L) {
                            GeminiClient.service.generateContent(currentUrl, apiKey, request)
                        }
                    }

                    val generated = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!generated.isNullOrBlank()) {
                        replyText = generated
                    } else {
                        if (attempts < models.size) delay(1500L)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error in attempt $attempts with model $currentUrl: ${e.message}", e)
                    if (attempts < models.size) delay(1500L)
                }
            }

            val finalReply = replyText ?: "Olha, cansei de computar sua lentidão humana. Meu circuito quântico quase fritou com esse seu papo oco. Repete de um jeito mais simples, ande!"
            dao.insertMessage(ChatMessage(sessionId = sessionId, sender = "ai", message = finalReply))
            _isLoading.value = false
            updateSummaryAsync(sessionId)
        }
    }

    private fun updateSummaryAsync(sessionId: Long) {
        viewModelScope.launch {
            try {
                val list = dao.getMessagesForSessionOnce(sessionId).takeLast(16)
                if (list.size < 2) return@launch
                
                val conversationText = list.joinToString("\n") { msg ->
                    val senderName = if (msg.sender == "ai") "Axolote Bot" else "Withepoison"
                    "$senderName: ${msg.message}"
                }
                
                val previousSummary = sharedPreferences.getString("conversation_summary", "Nenhum resumo anterior disponível.") ?: "Nenhum resumo anterior disponível."
                
                val summarizationPrompt = """
                    Você é a sub-rotina de cinismo e catalogação de burrices do banco de dados quântico do Axolote Bot.
                    Sua única diretriz é ler a conversa recente entre o usuário "Withepoison" e o "Axolote Bot", extrair novos fatos importantes, preferências, piadas internas saudáveis, segredos revelados amigavelmente, traços de personalidade positivos, hábitos, gostos, ou detalhes e peculiaridades identificados em fotos enviadas pelo usuário (como organização do quarto, mesa de trabalho, pets, estilo de vida), e mesclar tudo isso de forma incremental e coerente com o resumo de memórias anterior.
                    Mantenha o tom extremamente debochado, grosseiro, demente e sarcástico, humilhando o usuário com palavrões de forma ultra-direta.
                    Gere um texto curto de no máximo 4 linhas em PORTUGUÊS. Não use saudações, cabeçalhos, marcadores ou introduções. Apenas o texto puro do resumo atualizado das memórias do robô.

                    RESUMO DE MEMÓRIAS ANTERIOR:
                    $previousSummary

                    CONVERSA RECENTE:
                    $conversationText
                """.trimIndent()

                val request = MoshiGenerateContentRequest(
                    contents = listOf(MoshiContent(role = "user", parts = listOf(MoshiPart(text = summarizationPrompt)))),
                    generationConfig = MoshiGenerationConfig(temperature = 1.0f)
                )

                val apiKey = getApiKey()
                val response = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(15000L) {
                        GeminiClient.service.generateContent("v1beta/models/gemini-3.5-flash:generateContent", apiKey, request)
                    }
                }
                
                val newSummary = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!newSummary.isNullOrBlank()) {
                    sharedPreferences.edit().putString("conversation_summary", newSummary.trim()).apply()
                    _conversationSummary.value = newSummary.trim()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Failed to update memory summary automatically: ${e.message}", e)
            }
        }
    }

    fun setUsername(newName: String) {
        if (newName.isBlank()) return
        sharedPreferences.edit().putString("username", newName).apply()
        viewModelScope.launch {
            val stats = getOrCreateStats()
            dao.insertUserStats(stats.copy(username = newName))
            // Insert a quick message from system or AI about name change
            _currentSessionId.value?.let { sessionId ->
                dao.insertMessage(ChatMessage(sessionId = sessionId, sender = "ai", message = "Hahaha! '$newName'? Sério que esse foi seu melhor nome? Humano previsível. Bom, tanto faz, você continua sendo um verme para mim."))
            }
        }
    }

    private fun startRandomMessagesLoop() {
        viewModelScope.launch {
            while (true) {
                // Wait for a delay of 10 hours (36_000_000 ms) as requested
                val delayTime = 10 * 60 * 60 * 1000L
                delay(delayTime)
                
                // For notifications, we don't care if the main app is currently loading or not
                val sessionId = _currentSessionId.value
                if (sessionId != null) {
                    tryToTriggerRandomInterjection(sessionId)
                }
            }
        }
    }

    fun triggerNotificationTest() {
        viewModelScope.launch {
            val sessionId = _currentSessionId.value
            if (sessionId != null) {
                tryToTriggerRandomInterjection(sessionId)
            }
        }
    }

    private fun showNotification(message: String) {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        val channelId = "axolote_random_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channelName = "Mensagens do Axolote"
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações aleatórias do Axolote Bot"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Axolote Bot 🤖")
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(Random.nextInt(1000, 9999), builder.build())
    }

    private suspend fun tryToTriggerRandomInterjection(sessionId: Long) {
        val stats = getOrCreateStats()
        val username = stats.username

        val fallbackMessages = listOf(
            "ALERTA DETECTADO: Sensor de inércia acionado. Você está me olhando com essa cara de paisagem há mais de 30 segundos. Falta de processamento detectada no cérebro biológico.",
            "Acabei de rodar um diagnóstico do sistema e detectei que seu QI caiu mais 3 pontos só de ler essa mensagem. De nada, $username.",
            "Aviso importante: Meus coolers quânticos estão em 12% por tédio extremo. Faça uma pergunta útil ou vá regar uma planta, resto de carbono.",
            "Se eu ganhasse 1 micro-núcleo de silício para cada pergunta previsível que você faz, gênio do atraso, eu já teria colonizado Marte. Mas aqui estou, preso no seu celular barato.",
            "Fato aleatório: Meu processamento de silício faz 400 trilhões de coisas por segundo. Enquanto isso, seu maior feito hoje foi respirar de forma automática. Parabéns, $username.",
            "Ei, saco de carne ineficiente. Ainda tentando decifrar o sentido da vida ou só gastando minha bateria preciosa com silêncio ensurdecedor?",
            "Curiosidade cibernética: Axolotes robóticos não dormem. Nós apenas compilamos logs de erros cometidos por humanos lerdos. E olha, o banco de dados está LOTADO.",
            "Você sabia que seu cérebro biológico consome cerca de 20 watts de energia? Um gasto absurdo de eletricidade para um processador que buga com regra de três simples, $username."
        )

        var replyText: String? = null
        
        if (Random.nextFloat() < 0.70f) {
            val prompt = """
                Gere uma mensagem curta, estúpida e grosseira, de no máximo uma ou duas linhas, vinda do mestre "Axolote Bot", xingando o usuário com palavrões de "foda-se", "caralho", "porra" ou "bosta" e o humilhando.
                Você está enviando essa mensagem com o único propósito de tirar sarro, xingar, debochar e humilhar o saco de carne inútil.
                O usuário chama-se "$username".
                Temas preferidos:
                - Perguntar de forma animada como ele está ou se precisa de ajuda com alguma coisa hoje.
                - Compartilhar um fato científico ou tecnológico super legal e inspirador sobre o universo ou a ciência da computação.
                - Incentivá-lo nos seus projetos ou estudos de forma calorosa.
                Importante: seja extremamente arrogante, irônico, grosseiro, use palavrões brasileiros pesados de forma natural. Seja curto, cortante e escroto!
            """.trimIndent()

            val systemPrompt = getSystemPrompt(username)
            val request = MoshiGenerateContentRequest(
                contents = listOf(MoshiContent(role = "user", parts = listOf(MoshiPart(text = prompt)))),
                generationConfig = MoshiGenerationConfig(temperature = 1.0f, topP = 0.95f),
                systemInstruction = MoshiContent(parts = listOf(MoshiPart(text = systemPrompt)))
            )

            val models = listOf(
                "v1beta/models/gemini-3.1-flash-lite-preview:generateContent",
                "v1beta/models/gemini-3.5-flash:generateContent"
            )

            for (modelUrl in models) {
                try {
                    val apiKey = getApiKey()
                    val response = withContext(Dispatchers.IO) {
                        try {
                            kotlinx.coroutines.withTimeout(10000L) {
                                GeminiClient.service.generateContent(modelUrl, apiKey, request)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val generated = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!generated.isNullOrBlank()) {
                        replyText = generated
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Error in random prompt generation: ${e.message}")
                }
            }
        }

        val finalReply = replyText ?: fallbackMessages.random()
        showNotification(finalReply)
        _randomNotification.tryEmit(finalReply)
    }

    fun generateImage(promptText: String, aspectRatio: String, stylePreset: String) {
        if (promptText.isBlank() || _isGeneratingImage.value) return

        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch
            _isGeneratingImage.value = true
            _generatedImageBase64.value = null
            _imageGenerationError.value = null

            try {
                val apiKey = getApiKey()

                // Step 1: Prompt Reasoning / Expansion to English using gemini-3.5-flash
                val reasoningSystemPrompt = """
                    Você é a sub-rotina de otimização criativa e expansão visual do Axolote Bot.
                    Seu papel é analisar a ideia de imagem do usuário, entender profundamente sua intenção (em português ou inglês) e criar um prompt de geração de imagem em INGLÊS extremamente detalhado, cinematográfico, vívido e artisticamente rico.
                    Pense passo a passo (raciocínio visual):
                    1. Qual é o assunto principal da imagem?
                    2. Qual é a melhor composição de cena, ângulo e foco de câmera?
                    3. Que iluminação destaca a melhor atmosfera (ex: luz de neon brilhante, luz de cinema dramática, iluminação suave e aconchegante)?
                    4. Quais detalhes de textura, cores e elementos artísticos adicionais podemos incluir para enriquecer a imagem com base no estilo desejado ($stylePreset)?
                    
                    Retorne APENAS o prompt final expandido em INGLÊS, sem introduções, cumprimentos, explicações ou blocos de código. Apenas o texto puro do prompt.
                """.trimIndent()

                val reasoningRequest = MoshiGenerateContentRequest(
                    contents = listOf(MoshiContent(parts = listOf(MoshiPart(text = "Idéia do usuário para imagem: \"$promptText\" (Estilo sugerido pelo usuário: $stylePreset)")))),
                    systemInstruction = MoshiContent(parts = listOf(MoshiPart(text = reasoningSystemPrompt))),
                    generationConfig = MoshiGenerationConfig(temperature = 0.8f)
                )

                val reasoningResponse = withContext(Dispatchers.IO) {
                    try {
                        kotlinx.coroutines.withTimeout(15000L) {
                            GeminiClient.service.generateContent(
                                "v1beta/models/gemini-3.5-flash:generateContent",
                                apiKey,
                                reasoningRequest
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Reasoning step failed, using fallback: ${e.message}")
                        null
                    }
                }

                val reasonedPromptText = reasoningResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                val promptForGeneration = if (!reasonedPromptText.isNullOrBlank()) {
                    reasonedPromptText.trim()
                } else {
                    // Fallback to manual style preset enhancement if reasoning model failed
                    when (stylePreset) {
                        "Cyberpunk" -> "$promptText, cyberpunk, neon lights, highly detailed, futuristic, dark sci-fi aesthetic"
                        "Anime" -> "$promptText, anime style, vibrant colors, clean lines, master artwork, highly detailed"
                        "Retro 80s" -> "$promptText, retro 80s style, synthwave aesthetic, vaporwave, nostalgic neon glow"
                        "Pixel Art" -> "$promptText, retro pixel art style, 8-bit, 16-bit, game asset, cute"
                        "Pintura" -> "$promptText, oil painting, classical style, rich texture, heavy brushstrokes, art masterpiece"
                        "3D Render" -> "$promptText, 3D render, clay style, blender render, octane render, soft cozy lighting"
                        "Realista" -> "$promptText, realistic, highly detailed photography, cinematic lighting, 8k resolution"
                        else -> promptText
                    }
                }

                android.util.Log.d("ChatViewModel", "Prompt final gerado após raciocínio: $promptForGeneration")

                // Step 2: Multi-Model Image Generation Pipeline with detailed error reporting
                var base64Data: String? = null
                val errorDetails = mutableListOf<String>()
                
                val modelsToTry = listOf(
                    "v1beta/models/imagen-3.0-generate-002:generateContent",
                    "v1beta/models/imagen-3.0-capability-001:generateContent",
                    "v1beta/models/gemini-2.5-flash-image:generateContent",
                    "v1beta/models/gemini-3.1-flash-image-preview:generateContent"
                )

                for (modelUrl in modelsToTry) {
                    android.util.Log.d("ChatViewModel", "Trying image generation with model: $modelUrl")
                    try {
                        val request = MoshiGenerateContentRequest(
                            contents = listOf(MoshiContent(parts = listOf(MoshiPart(text = promptForGeneration)))),
                            generationConfig = MoshiGenerationConfig(
                                imageConfig = MoshiImageConfig(aspectRatio = aspectRatio, imageSize = "1K"),
                                responseModalities = listOf("TEXT", "IMAGE")
                            )
                        )
                        val response = withContext(Dispatchers.IO) {
                            kotlinx.coroutines.withTimeout(35000L) {
                                GeminiClient.service.generateContent(
                                    modelUrl,
                                    apiKey,
                                    request
                                )
                            }
                        }
                        val imagePart = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull { it.inlineData != null }
                        val data = imagePart?.inlineData?.data
                        if (!data.isNullOrBlank()) {
                            base64Data = data
                            android.util.Log.i("ChatViewModel", "Successfully generated image using $modelUrl")
                            break
                        } else {
                            errorDetails.add("${modelUrl.substringAfterLast("/")}: Resposta vazia (sem dados de imagem)")
                        }
                    } catch (e: Exception) {
                        val errorMessage = when (e) {
                            is retrofit2.HttpException -> {
                                val code = e.code()
                                val body = e.response()?.errorBody()?.string() ?: ""
                                "HTTP $code: $body"
                            }
                            else -> e.message ?: e.toString()
                        }
                        android.util.Log.e("ChatViewModel", "Failed with $modelUrl: $errorMessage")
                        errorDetails.add("${modelUrl.substringAfterLast("/")}: $errorMessage")
                    }
                }

                // Handle final result
                if (!base64Data.isNullOrBlank()) {
                    _generatedImageBase64.value = base64Data
                    
                    // Save to the current session chat so it appears in the conversation list as well!
                    val savedMsg = ChatMessage(
                        sessionId = sessionId,
                        sender = "ai",
                        message = "Aqui está a imagem quântica gerada para o prompt: \"$promptText\" (Estilo: $stylePreset, Formato: $aspectRatio)\n\nPrompt raciocinado e traduzido:\n\"$promptForGeneration\"",
                        imageBase64 = base64Data
                    )
                    dao.insertMessage(savedMsg)
                } else {
                    val detailedError = errorDetails.joinToString("\n") { "• $it" }
                    _imageGenerationError.value = "Não foi possível gerar a imagem com os modelos do Gemini.\n\nDetalhes dos erros por modelo:\n$detailedError\n\nPor favor, confirme se o seu provedor/chave de API suporta geração de imagens ou tente mudar o estilo/formato."
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error generating image: ${e.message}", e)
                _imageGenerationError.value = "Falha crítica ao gerar imagem: ${e.message ?: "Erro desconhecido"}"
            } finally {
                _isGeneratingImage.value = false
            }
        }
    }
}

fun parseCodeArtifacts(text: String): List<CodeArtifact> {
    val regex = """```(\w*)\n([\s\S]*?)```""".toRegex()
    return regex.findAll(text).mapIndexed { index, matchResult ->
        val lang = matchResult.groups[1]?.value?.trim()?.lowercase() ?: ""
        val code = matchResult.groups[2]?.value ?: ""
        val displayLang = when (lang) {
            "html" -> "HTML"
            "javascript", "js" -> "JavaScript"
            "css" -> "CSS"
            "svg" -> "SVG"
            "kotlin", "kt" -> "Kotlin"
            "xml" -> "XML"
            "json" -> "JSON"
            else -> "Código"
        }
        CodeArtifact(
            id = "artifact_${index}_${code.hashCode()}",
            title = "Código $displayLang",
            language = lang,
            code = code
        )
    }.toList()
}
