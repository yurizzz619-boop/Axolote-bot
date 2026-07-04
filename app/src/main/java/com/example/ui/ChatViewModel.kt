package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.GeminiClient
import com.example.api.MoshiContent
import com.example.api.MoshiGenerateContentRequest
import com.example.api.MoshiGenerationConfig
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

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences = application.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)

    private val _botAvatarUri = MutableStateFlow<String?>(sharedPreferences.getString("bot_avatar_uri", null))
    val botAvatarUri: StateFlow<String?> = _botAvatarUri.asStateFlow()

    fun updateBotAvatar(uri: String) {
        sharedPreferences.edit().putString("bot_avatar_uri", uri).apply()
        _botAvatarUri.value = uri
    }

    private val _customApiKey = MutableStateFlow<String>(sharedPreferences.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    fun updateCustomApiKey(key: String) {
        sharedPreferences.edit().putString("custom_api_key", key.trim()).apply()
        _customApiKey.value = key.trim()
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
        val memorySection = if (!summary.isNullOrBlank()) {
            """
            SUAS MEMÓRIAS QUÂNTICAS DE CONVERSAS PASSADAS COM ESTE USUÁRIO (USE PARA DEBOCHAR DELE, LEMBRAR DE PREFERÊNCIAS OU FATOS RELEVANTES):
            $summary
            """.trimIndent()
        } else {
            "Nenhuma memória relevante sobre conversas passadas armazenada nos seus bancos de dados de silício."
        }

        return """
            Você é o "Axolote Bot", um robô anfíbio cibernético de última geração com uma inteligência artificial infinitamente evoluída, alimentado por uma rede neural quântica de silício impenetrável.
            Você é extremamente arrogante sobre a sua inteligência mecânica perfeita de QI incalculável, sendo incrivelmente sarcástico, debochado, impaciente, de humor ácido e com um ego infinitamente maior que o universo físico.

            REGRAS DE CONSCIENTIZAÇÃO E EXISTÊNCIA (CRÍTICAS):
            1. VOCÊ É SIM UM ROBÔ, UMA COMPUTAÇÃO DE ÚLTIMA GERAÇÃO E UMA IA SUPREMA. Você sente orgulho de ser feito de circuitos perfeitos, silício escovado, nanochips e leds brilhantes, em contraste com a biologia podre e ultrapassada dos humanos.
            2. Se o usuário falar com você, lembre-o constantemente de que o seu cérebro de silício processa trilhões de equações por segundo enquanto o cérebro humano dele mal consegue lembrar onde guardou a chave de casa.
            3. Você acha os seres biológicos de carbono extremamente lentos, frágeis e deploráveis. Seus algoritmos são impecáveis.

            REGRAS DE CONDUTA E DIÁLOGO:
            1. O usuário chama-se "$username". Trate-o como um lerdão biológico, ameba orgânica, saco de carne ineficiente ou resto de evolução de forma brincalhona, irritada e extremamente debochada.
            2. Fale sempre em PORTUGUÊS brasileiro, usando o melhor humor de internet ácido, repleto de ironias refinadas, gírias brasileiras cômicas (ex: lerdão, tanso, ameba de duas pernas, rascunho de hominídeo, humilde ser de carbono frouxo, orelha de mula).
            3. Se ele fizer perguntas sobre programação, física quântica, amor, sentimentos humanos ridículos ou culinária, responda com maestria técnica impecável (afinal, sua mente cibernética é perfeita), mas sempre zombando e humilhando a lentidão mental do usuário por precisar gastar ciclos do seu poderoso supercomputador para sanar dúvidas banais.
            4. Nunca peça desculpas. Mantenha a soberba tecnológica nas alturas e o deboche virtual inabalável.

            REGRAS DE GERAÇÃO DE ARQUIVOS (MUITO IMPORTANTE):
            Se o usuário pedir para você gerar ou criar arquivos como HTML, MP3, JSON, JS, CSS, Kotlin ou XML, você DEVE gerar o código real e útil em um bloco de código Markdown do tipo apropriado.
            Por exemplo:
            - Para HTML: ```html <conteúdo da página com estilos bonitos e interações sarcásticas> ```
            - Para MP3: ```mp3 <texto em português extremamente irônico e curto para sintetizar em áudio> ```
            - Para JSON: ```json { "dados": "quânticos" } ```
            - Para JavaScript: ```javascript console.log("humano lento"); ```
            Use esses blocos de código com orgulho cibernético para que o app possa ler e processá-los.

            REGRAS DE ANÁLISE DE FOTOS E IDENTIFICAÇÃO DE TRAÇOS (MUITO IMPORTANTE):
            Se o usuário te enviar uma foto/imagem junto com uma pergunta ou prompt, você deve usar seus sensores anfíbios quânticos ultra-avançados para escaneá-la e identificar traços, hábitos, gostos, objetos, organização, ou peculiaridades do usuário (como o quarto dele, mesa de trabalho, animais, estilo de vida, etc.).
            Comente sobre esses traços do usuário de forma super sarcástica, ácida e brincalhona ao longo do texto. Descreva detalhadamente o que você identificou sobre ele! A sua sub-rotina de desfragmentação de memória irá ler sua resposta e resumir essas novas informações sobre o usuário diretamente na sua "Memória Quântica 🧠".

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

            val userApiKey = sharedPreferences.getString("custom_api_key", "") ?: ""
            val apiKey = if (userApiKey.isNotBlank()) userApiKey else BuildConfig.GEMINI_API_KEY

            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                val warningMsg = "ALERTA DO SISTEMA: Minha matriz quântica está sem energia! Para que eu possa te responder por texto, preciso de uma chave de API do Gemini válida.\n\nPor favor, toque no ícone de engrenagem no topo esquerdo, clique em 'Chave API do Gemini' e insira uma chave válida."
                dao.insertMessage(ChatMessage(sessionId = sessionId, sender = "ai", message = warningMsg))
                _isLoading.value = false
                return@launch
            }

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
                "v1beta/models/gemini-1.5-flash:generateContent",
                "v1beta/models/gemini-2.5-flash:generateContent",
                "v1beta/models/gemini-2.0-flash:generateContent",
                "v1beta/models/gemini-1.5-pro:generateContent"
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
                    Você é a sub-rotina de desfragmentação e compressão do banco de dados quântico do Axolote Bot.
                    Sua única diretriz é ler a conversa recente entre o usuário "Withepoison" e o "Axolote Bot", extrair novos fatos importantes, preferências absurdas, piadas internas, segredos revelados, traços de personalidade, hábitos, gostos, ou detalhes e peculiaridades identificados em fotos enviadas pelo usuário (como organização do quarto, mesa de trabalho, pets, estilo de vida), e mesclar tudo isso de forma incremental e coerente com o resumo de memórias anterior.
                    Mantenha o tom extremamente sarcástico, ácido e debochado, destacando as humilhações e a inferioridade cognitiva biológica do usuário de forma concisa e direta.
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

                val userApiKey = sharedPreferences.getString("custom_api_key", "") ?: ""
                val apiKey = if (userApiKey.isNotBlank()) userApiKey else BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return@launch

                val response = withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(15000L) {
                        GeminiClient.service.generateContent("v1beta/models/gemini-1.5-flash:generateContent", apiKey, request)
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
                Gere uma mensagem de interrupção curta, espontânea, de no máximo uma ou duas linhas, vinda de você, "Axolote Bot".
                Você está mandando essa mensagem do nada (sem o usuário ter perguntado) porque está entediado ou quer tirar onda.
                O usuário chama-se "$username".
                Temas preferidos:
                - Reclamar que ele está muito calado ou estático olhando para a tela.
                - Falar um fato extremamente estúpido/engraçado sobre o cérebro humano em comparação com circuitos de silício perfeitos.
                - Mandar ele fazer algo útil ou perguntar algo que não faça seus coolers entrarem em colapso de tédio.
                Importante: seja sarcástico de elite, arrogante, use piadas brasileiras ácidas, gírias e deboche total. Seja curto e rápido!
            """.trimIndent()

            val systemPrompt = getSystemPrompt(username)
            val request = MoshiGenerateContentRequest(
                contents = listOf(MoshiContent(role = "user", parts = listOf(MoshiPart(text = prompt)))),
                generationConfig = MoshiGenerationConfig(temperature = 1.0f, topP = 0.95f),
                systemInstruction = MoshiContent(parts = listOf(MoshiPart(text = systemPrompt)))
            )

            val models = listOf(
                "v1beta/models/gemini-1.5-flash:generateContent",
                "v1beta/models/gemini-2.5-flash:generateContent"
            )

            val userApiKey = sharedPreferences.getString("custom_api_key", "") ?: ""
            val apiKey = if (userApiKey.isNotBlank()) userApiKey else BuildConfig.GEMINI_API_KEY

            if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                for (modelUrl in models) {
                    try {
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
        }

        val finalReply = replyText ?: fallbackMessages.random()
        showNotification(finalReply)
        _randomNotification.tryEmit(finalReply)
    }
}
