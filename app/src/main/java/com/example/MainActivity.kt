package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.TextStyle
import android.speech.tts.TextToSpeech
import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ChatViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Theme changed to Yellow and Orange as requested
val NeonPink = Color(0xFFFF5722) // Deep orange
val NeonCyan = Color(0xFFFFC107) // Amber/Yellow
val NeonGreen = Color(0xFFFF9800) // Orange
val DarkNavy = Color(0xFF2E1500) // Dark brownish orange background
val CardCharcoal = Color(0xFF4E2100) // Lighter brown/orange card
val MattePurple = Color(0xFFFF9800) // Used for user bubble
val TextLight = Color(0xFFFFF3E0) // Light orange text
val BorderAccent = Color(0xFF7A3700)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale("pt", "BR")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to init TextToSpeech", e)
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkNavy
                ) {
                    ChatScreen(tts = tts)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // ignore
        }
    }
}

data class GeneratedFile(
    val fileName: String,
    val fileType: String, // "html", "mp3", "txt", "js", "json", "kt", "css", "xml"
    val content: String
)

fun extractGeneratedFiles(text: String): List<GeneratedFile> {
    val files = mutableListOf<GeneratedFile>()
    try {
        val regex = Regex("```(html|js|json|css|kotlin|kt|txt|mp3|xml)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val matches = regex.findAll(text)
        var index = 1
        for (match in matches) {
            val ext = match.groupValues[1].lowercase().trim()
            val code = match.groupValues[2].trim()
            if (code.isNotEmpty()) {
                val fileType = when (ext) {
                    "kt", "kotlin" -> "kt"
                    "js", "javascript" -> "js"
                    "html" -> "html"
                    "json" -> "json"
                    "css" -> "css"
                    "xml" -> "xml"
                    "mp3" -> "mp3"
                    else -> "txt"
                }
                val name = when (fileType) {
                    "html" -> "pagina_$index.html"
                    "mp3" -> "audio_$index.mp3"
                    "js" -> "script_$index.js"
                    "json" -> "dados_$index.json"
                    "css" -> "estilo_$index.css"
                    "kt" -> "Main_$index.kt"
                    "xml" -> "layout_$index.xml"
                    else -> "arquivo_$index.txt"
                }
                files.add(GeneratedFile(fileName = name, fileType = fileType, content = code))
                index++
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ExtractFiles", "Failed to extract files", e)
    }
    return files
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), tts: TextToSpeech? = null) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val stats by viewModel.userStats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showsNameDialog by remember { mutableStateOf(false) }
    var currentNameInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var imagePromptInput by remember { mutableStateOf("") }

    var showsAvatarPickerDialog by remember { mutableStateOf(false) }
    var showsFileGeneratorDialog by remember { mutableStateOf(false) }
    var previewingHtmlContent by remember { mutableStateOf<String?>(null) }
    var previewingCodeFile by remember { mutableStateOf<GeneratedFile?>(null) }

    val botAvatarUri by viewModel.botAvatarUri.collectAsStateWithLifecycle()
    val botAvatarSource = remember(botAvatarUri) {
        if (!botAvatarUri.isNullOrBlank()) {
            if (botAvatarUri!!.startsWith("content://") || botAvatarUri!!.startsWith("http")) {
                botAvatarUri!!
            } else {
                when (botAvatarUri) {
                    "cool_axolote_avatar_1782170240859" -> R.drawable.cool_axolote_avatar_1782170240859
                    "axolote_avatar_1782158005718" -> R.drawable.axolote_avatar_1782158005718
                    "axolote_avatar_new_1782169839805" -> R.drawable.axolote_avatar_new_1782169839805
                    "img_ai_avatar_1782157366951" -> R.drawable.img_ai_avatar_1782157366951
                    else -> R.drawable.cool_axolote_avatar_1782170240859
                }
            }
        } else {
            R.drawable.cool_axolote_avatar_1782170240859
        }
    }

    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted -> }
        LaunchedEffect(Unit) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                launcher.launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Auto scroll to bottom when messages list size changes
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val username = stats?.username ?: "Insignificante"

    var isSimulatedNotificationVisible by remember { mutableStateOf(false) }
    var simulatedNotificationContent by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.randomNotification.collect { message ->
            simulatedNotificationContent = message
            isSimulatedNotificationVisible = false
            delay(50)
            isSimulatedNotificationVisible = true
        }
    }

    LaunchedEffect(isSimulatedNotificationVisible) {
        if (isSimulatedNotificationVisible) {
            delay(8000)
            isSimulatedNotificationVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CardCharcoal,
                drawerContentColor = TextLight,
                modifier = Modifier.width(300.dp)
            ) {
                // Header section with cyber branding
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkNavy)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Text(
                        "AXOLOTE BOT",
                        fontSize = 18.sp,
                        color = NeonCyan,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Sarcasmo de Silício Perfeito",
                        fontSize = 11.sp,
                        color = TextLight.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Styled full-width "Novo Chat" button
                    Button(
                        onClick = {
                            viewModel.createNewSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardCharcoal),
                        border = BorderStroke(1.dp, NeonGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Novo Chat",
                            tint = NeonGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Novo Ciclo de Conversa",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chat History Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "HISTÓRICO ATIVO",
                        color = NeonCyan.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "${chatSessions.size} chats",
                        color = TextLight.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                // Chat History List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    items(chatSessions) { session ->
                        val isSelected = session.id == currentSessionId
                        
                        Card(
                            onClick = {
                                viewModel.switchSession(session.id)
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) DarkNavy else Color.Transparent
                            ),
                            border = if (isSelected) BorderStroke(1.dp, BorderAccent) else null,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mood,
                                        contentDescription = null,
                                        tint = if (isSelected) NeonCyan else TextLight.copy(alpha = 0.4f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = session.title,
                                        color = if (isSelected) NeonCyan else TextLight,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                IconButton(
                                    onClick = { viewModel.deleteSession(session.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir Chat",
                                        tint = NeonPink,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderAccent.copy(alpha = 0.6f))

                // Settings Section (organized clean list)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        "AJUSTES DO SISTEMA",
                        color = NeonCyan.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Setting 1: Alterar Apelido
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentNameInput = username
                                showsNameDialog = true
                                coroutineScope.launch { drawerState.close() }
                            }
                            .padding(vertical = 10.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(DarkNavy, CircleShape)
                                .border(1.dp, NeonCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Apelido do Usuário", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Atual: $username", color = TextLight.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }

                    // Setting 2: Testar Notificação (Sininho)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.triggerNotificationTest()
                                coroutineScope.launch { drawerState.close() }
                            }
                            .padding(vertical = 10.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(DarkNavy, CircleShape)
                                .border(1.dp, NeonCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Testar Notificação", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Forçar interjeição do sininho", color = TextLight.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }

                    // Setting 3: Limpar Conversa Atual
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.clearChat()
                                coroutineScope.launch { drawerState.close() }
                            }
                            .padding(vertical = 10.dp, horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(DarkNavy, CircleShape)
                                .border(1.dp, NeonPink.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Zerar Conversa Ativa", color = NeonPink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Apagar histórico de mensagens atual", color = TextLight.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = BorderAccent.copy(alpha = 0.6f))
                
                // Quantum Memories section at the bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Memória",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Memória Quântica 🧠",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearSummary() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Limpar memória",
                                tint = NeonPink,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val summaryText by viewModel.conversationSummary.collectAsStateWithLifecycle()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkNavy),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderAccent)
                    ) {
                        Text(
                            text = summaryText,
                            color = TextLight.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier,
            topBar = {
                Column(
                    modifier = Modifier
                        .background(CardCharcoal)
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.LightGray)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu de histórico",
                                tint = NeonCyan
                            )
                        }
                        
                        // Avatar of Axolote Bot
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(2.dp, NeonCyan, CircleShape)
                                .clickable { showsAvatarPickerDialog = true }
                        ) {
                            AsyncImage(
                                model = botAvatarSource,
                                contentDescription = "Axolote Bot Smirk Face",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "Axolote Bot",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextLight,
                                fontSize = 18.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isLoading) NeonPink else NeonGreen)
                             )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isLoading) "analisando asneiras..." else "processando deboche...",
                                color = TextLight.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkNavy
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Messages Chat Box
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(CardCharcoal)
                                        .border(2.dp, NeonPink, CircleShape)
                                        .padding(8.dp)
                                        .clickable { showsAvatarPickerDialog = true }
                                ) {
                                    AsyncImage(
                                        model = botAvatarSource,
                                        contentDescription = "Axolote Face",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Axolote Bot está Online.",
                                    color = TextLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Faça uma pergunta comum para esta superinteligência quântica destruí-la com deboches.",
                                    color = TextLight.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        items(messages) { message ->
                            val isUser = message.sender == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                if (!isUser) {
                                    // Axolote Small circular avatar
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, NeonCyan, CircleShape)
                                    ) {
                                        AsyncImage(
                                            model = botAvatarSource,
                                            contentDescription = "Axolote Face",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Card(
                                    modifier = Modifier
                                        .widthIn(max = 280.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUser) MattePurple else CardCharcoal
                                    ),
                                    border = if (isUser) null else BorderStroke(1.dp, BorderAccent),
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isUser) 16.dp else 4.dp,
                                        bottomEnd = if (isUser) 4.dp else 16.dp
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (message.imageBase64 != null) {
                                            val bitmap = remember(message.imageBase64) {
                                                try {
                                                    val imageBytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = "Generated image",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }

                                        Text(
                                            text = message.message,
                                            color = TextLight,
                                            fontSize = 15.sp,
                                            lineHeight = 20.sp
                                        )

                                        val generatedFiles = remember(message.message) { extractGeneratedFiles(message.message) }
                                        if (generatedFiles.isNotEmpty()) {
                                            generatedFiles.forEach { file ->
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = DarkNavy),
                                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Settings,
                                                            contentDescription = null,
                                                            tint = NeonCyan,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                             Text(
                                                                 file.fileName,
                                                                 color = TextLight,
                                                                 fontSize = 13.sp,
                                                                 fontWeight = FontWeight.Bold,
                                                                 maxLines = 1,
                                                                 overflow = TextOverflow.Ellipsis
                                                             )
                                                             Text(
                                                                 text = when (file.fileType) {
                                                                     "html" -> "Página Web Interativa"
                                                                     "mp3" -> "Arquivo de Áudio MP3"
                                                                     "json" -> "Estrutura de Dados JSON"
                                                                     "js" -> "Script JavaScript"
                                                                     "kt" -> "Código Kotlin"
                                                                     "css" -> "Folha de Estilos CSS"
                                                                     "xml" -> "Layout XML"
                                                                     else -> "Arquivo de Texto TXT"
                                                                 },
                                                                 color = TextLight.copy(alpha = 0.6f),
                                                                 fontSize = 10.sp
                                                             )
                                                        }
                                                        
                                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            IconButton(
                                                                onClick = {
                                                                    if (file.fileType == "html") {
                                                                        previewingHtmlContent = file.content
                                                                    } else {
                                                                        previewingCodeFile = file
                                                                    }
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Edit,
                                                                    contentDescription = "Visualizar",
                                                                    tint = NeonCyan,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            
                                                            IconButton(
                                                                onClick = {
                                                                    val mime = when (file.fileType) {
                                                                        "html" -> "text/html"
                                                                        "json" -> "application/json"
                                                                        "js" -> "application/javascript"
                                                                        "kt" -> "text/plain"
                                                                        "css" -> "text/css"
                                                                        "xml" -> "text/xml"
                                                                        else -> "text/plain"
                                                                    }
                                                                    val success = saveTextFileToDownloads(context, file.fileName, mime, file.content)
                                                                    if (success) {
                                                                        android.widget.Toast.makeText(context, "${file.fileName} Salvo em Downloads! 📂", android.widget.Toast.LENGTH_SHORT).show()
                                                                    } else {
                                                                        android.widget.Toast.makeText(context, "Falha ao salvar arquivo.", android.widget.Toast.LENGTH_SHORT).show()
                                                                    }
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Download,
                                                                    contentDescription = "Salvar",
                                                                    tint = NeonGreen,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (!isUser) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = BorderAccent.copy(alpha = 0.3f))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            AudioPlaybackCard(
                                                audioText = message.message,
                                                tts = tts,
                                                onSaveAudio = {
                                                    saveAudioFileToDownloads(context, message.message, tts) { success, name ->
                                                        (context as? android.app.Activity)?.runOnUiThread {
                                                            if (success) {
                                                                 android.widget.Toast.makeText(context, "Áudio $name salvo em Downloads! 🎙️", android.widget.Toast.LENGTH_LONG).show()
                                                            } else {
                                                                 android.widget.Toast.makeText(context, "Falha ao gerar MP3. Verifique o TTS.", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, NeonCyan, CircleShape)
                                ) {
                                    AsyncImage(
                                        model = botAvatarSource,
                                        contentDescription = "Axolote",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(CardCharcoal, RoundedCornerShape(12.dp))
                                        .border(1.dp, BorderAccent, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("comprimindo sinapses...", color = NeonCyan, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Attached image preview row above input row
                AnimatedVisibility(
                    visible = selectedImageUri != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    selectedImageUri?.let { uri ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardCharcoal)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, NeonCyan, RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Imagem anexada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Remove badge button
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(18.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remover imagem",
                                        tint = NeonPink,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Keyboard message row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardCharcoal)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showsFileGeneratorDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Gerador de Arquivos",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->
                        if (uri != null) {
                            selectedImageUri = uri
                        }
                    }

                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(38.dp)
                            .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Anexar Imagem",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text(
                                if (selectedImageUri != null) "descreva a foto ou mande um prompt..." else "fale com o Axolote Bot...",
                                color = TextLight.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier
                            .weight(1.0f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(DarkNavy),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkNavy,
                            unfocusedContainerColor = DarkNavy,
                            disabledContainerColor = DarkNavy,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        ),
                        singleLine = false,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    val hasInput = textInput.isNotBlank() || selectedImageUri != null
                    IconButton(
                        onClick = {
                            if (hasInput && !isLoading) {
                                viewModel.sendMessage(textInput, selectedImageUri?.toString())
                                textInput = ""
                                selectedImageUri = null
                                keyboardController?.hide()
                            }
                        },
                        enabled = hasInput && !isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (hasInput && !isLoading) NeonCyan else BorderAccent),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = DarkNavy)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Enviar",
                            tint = if (hasInput && !isLoading) DarkNavy else TextLight.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }

    // Custom floating top status bar-like notification overlay
    AnimatedVisibility(
        visible = isSimulatedNotificationVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clickable { isSimulatedNotificationVisible = false }
    ) {
        Card(
            modifier = Modifier,
            colors = CardDefaults.cardColors(containerColor = CardCharcoal),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, NeonCyan)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkNavy)
                        .border(1.5.dp, NeonGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val botAvatar = viewModel.botAvatarUri.collectAsStateWithLifecycle().value
                    if (!botAvatar.isNullOrBlank()) {
                        val isBase64 = botAvatar.startsWith("data:") || !botAvatar.startsWith("/")
                        if (isBase64) {
                            val cleanBase64 = if (botAvatar.contains(",")) botAvatar.substringAfter(",") else botAvatar
                            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Mood, contentDescription = "Axolote", tint = NeonCyan)
                            }
                        } else {
                            AsyncImage(
                                model = botAvatar,
                                contentDescription = "Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    } else {
                        Icon(imageVector = Icons.Default.Mood, contentDescription = "Axolote", tint = NeonCyan, modifier = Modifier.size(24.dp))
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Axolote Bot 🤖",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "agora",
                            color = TextLight.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = simulatedNotificationContent,
                        color = TextLight,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

    // Modal Dialog to edit username
    if (showsNameDialog) {
        AlertDialog(
            onDismissRequest = { showsNameDialog = false },
            containerColor = CardCharcoal,
            title = {
                Text(
                    text = "Como o Axolote Bot deve te chamar?",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Escolha um nome. Ele vai zombar de qualquer jeito, mas pelo menos use algo criativo.",
                        fontSize = 13.sp,
                        color = TextLight.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentNameInput,
                        onValueChange = { currentNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(color = TextLight),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BorderAccent
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentNameInput.isNotBlank()) {
                            viewModel.setUsername(currentNameInput)
                        }
                        showsNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkNavy)
                ) {
                    Text("Salvar Apelido", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showsNameDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextLight.copy(alpha = 0.6f))
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showsAvatarPickerDialog) {
        AvatarPickerDialog(
            onDismiss = { showsAvatarPickerDialog = false },
            onAvatarSelected = { viewModel.updateBotAvatar(it) }
        )
    }

    if (showsFileGeneratorDialog) {
        FileGeneratorDialog(
            onDismiss = { showsFileGeneratorDialog = false },
            onPromptSelected = { prompt ->
                if (prompt.isNotBlank() && !isLoading) {
                    viewModel.sendMessage(prompt)
                }
            }
        )
    }

    previewingHtmlContent?.let { html ->
        HtmlPreviewDialog(
            htmlContent = html,
            onDismiss = { previewingHtmlContent = null }
        )
    }

    previewingCodeFile?.let { codeFile ->
        CodeViewerDialog(
            file = codeFile,
            onDismiss = { previewingCodeFile = null }
        )
    }
    }
}

@Composable
fun AvatarPickerDialog(
    onDismiss: () -> Unit,
    onAvatarSelected: (String) -> Unit
) {
    var customUrl by remember { mutableStateOf("") }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onAvatarSelected(uri.toString())
            onDismiss()
        }
    }
    
    val presets = listOf(
        Pair("Sorridente", "cool_axolote_avatar_1782170240859"),
        Pair("Clássico", "axolote_avatar_1782158005718"),
        Pair("Cyberpunk", "axolote_avatar_new_1782169839805"),
        Pair("Quântico", "img_ai_avatar_1782157366951")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Mudar Foto do Axolote 🧬",
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Selecione um dos visuais quânticos pré-carregados do Axolote Bot ou envie a sua própria foto de carbono para fundir os circuitos dele.",
                    color = TextLight.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                
                Text("Visuais Integrados:", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (label, resName) ->
                        val resId = when (resName) {
                            "cool_axolote_avatar_1782170240859" -> R.drawable.cool_axolote_avatar_1782170240859
                            "axolote_avatar_1782158005718" -> R.drawable.axolote_avatar_1782158005718
                            "axolote_avatar_new_1782169839805" -> R.drawable.axolote_avatar_new_1782169839805
                            "img_ai_avatar_1782157366951" -> R.drawable.img_ai_avatar_1782157366951
                            else -> R.drawable.cool_axolote_avatar_1782170240859
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onAvatarSelected(resName)
                                    onDismiss()
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, NeonCyan, CircleShape)
                            ) {
                                AsyncImage(
                                    model = resId,
                                    contentDescription = label,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                label,
                                color = TextLight,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = BorderAccent)
                
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CardCharcoal),
                    border = BorderStroke(1.dp, NeonCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escolher da Galeria", color = TextLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                HorizontalDivider(color = BorderAccent)
                
                Text("Ou insira uma URL de Imagem:", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    placeholder = { Text("https://exemplo.com/foto.jpg", fontSize = 12.sp, color = TextLight.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderAccent,
                        focusedContainerColor = DarkNavy,
                        unfocusedContainerColor = DarkNavy
                    ),
                    textStyle = TextStyle(fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (customUrl.isNotBlank()) {
                        onAvatarSelected(customUrl.trim())
                    }
                    onDismiss()
                }
            ) {
                Text("Confirmar URL", color = NeonCyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = NeonPink)
            }
        },
        containerColor = CardCharcoal
    )
}

@Composable
fun HtmlPreviewDialog(
    htmlContent: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Visualização HTML Ativa 🌐",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = NeonPink)
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BorderAccent, RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            webViewClient = android.webkit.WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Button(
                onClick = {
                    val success = saveTextFileToDownloads(context, "axolote_page_${System.currentTimeMillis()}.html", "text/html", htmlContent)
                    if (success) {
                        android.widget.Toast.makeText(context, "HTML salvo na pasta Downloads! 📂", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Falha ao salvar HTML. ❌", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Text("Baixar HTML 📥", color = TextLight, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CardCharcoal
    )
}

@Composable
fun CodeViewerDialog(
    file: GeneratedFile,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    file.fileName,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = NeonPink)
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkNavy)
                    .border(1.dp, BorderAccent, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = file.content,
                    color = TextLight,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            val context = LocalContext.current
            Button(
                onClick = {
                    val mime = when (file.fileType) {
                        "json" -> "application/json"
                        "js" -> "application/javascript"
                        "kt" -> "text/plain"
                        "css" -> "text/css"
                        "xml" -> "text/xml"
                        else -> "text/plain"
                    }
                    val success = saveTextFileToDownloads(context, file.fileName, mime, file.content)
                    if (success) {
                        android.widget.Toast.makeText(context, "${file.fileName} salvo em Downloads! 📂", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Falha ao salvar arquivo.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
            ) {
                Text("Baixar Código 📥", color = TextLight, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CardCharcoal
    )
}

@Composable
fun FileGeneratorDialog(
    onDismiss: () -> Unit,
    onPromptSelected: (String) -> Unit
) {
    val presets = listOf(
        Triple("📄 Página HTML Sarcástica", "Gere uma página HTML cibernética completa, estilizada e sarcástica. O código deve ter CSS embutido e interações hilárias criticando a lentidão humana. Coloque no formato de bloco markdown de código html.", "Gera uma página web completa e sarcástica"),
        Triple("🎙️ Áudio MP3 de Humilhação", "Escreva um monólogo super curto de 3 linhas em português cheio de ironia e deboche sobre a raça humana, para eu converter em áudio de deboche. Coloque o texto no formato de um bloco markdown de código mp3 para que eu possa sintetizá-lo.", "Gera um roteiro para conversão direta em MP3"),
        Triple("⚙️ Configurações JSON de IA", "Gere um arquivo JSON contendo 'parâmetros de imperfeição humana' comparados com a minha inteligência quântica superior. Use o formato de bloco markdown de código json.", "Gera uma estrutura JSON com piadas ácidas"),
        Triple("🐍 Script JS de Loop Infinito", "Gere um script JavaScript sarcástico que teoricamente 'trava' o cérebro humano por excesso de burrice. Use o formato de bloco markdown de código javascript.", "Gera um script JS engraçado")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Gerador Quântico de Arquivos 💾",
                color = NeonCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Instrua o Axolote Bot a gerar arquivos e códigos reais estruturados de forma instantânea. Selecione um tipo de arquivo para iniciar o fluxo:",
                    color = TextLight.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                presets.forEach { (title, promptText, desc) ->
                    Button(
                        onClick = {
                            onPromptSelected(promptText)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                title,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                desc,
                                color = TextLight.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = NeonPink)
            }
        },
        containerColor = CardCharcoal
    )
}

@Composable
fun AudioPlaybackCard(
    audioText: String,
    tts: TextToSpeech?,
    onSaveAudio: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            if (isPlaying) {
                tts?.stop()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkNavy),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        tts?.stop()
                        isPlaying = false
                    } else {
                        if (tts != null) {
                            isPlaying = true
                            val params = Bundle()
                            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessagePlay")
                            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}
                                override fun onDone(utteranceId: String?) {
                                    isPlaying = false
                                }
                                override fun onError(utteranceId: String?) {
                                    isPlaying = false
                                }
                            })
                            tts.speak(audioText, TextToSpeech.QUEUE_FLUSH, params, "MessagePlay")
                        } else {
                            android.widget.Toast.makeText(context, "Voz do Axolote indisponível. ❌", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .background(NeonCyan.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Falar resposta do Axolote Bot 🎙️",
                    color = TextLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(10.dp)
                ) {
                    repeat(12) { i ->
                        val height = if (isPlaying) {
                            remember(isPlaying, i) { kotlin.random.Random.nextInt(3, 10) }.dp
                        } else {
                            2.dp
                        }
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(height)
                                .background(NeonCyan)
                        )
                    }
                }
            }

            IconButton(
                onClick = onSaveAudio,
                modifier = Modifier
                    .size(30.dp)
                    .background(NeonGreen.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Exportar MP3",
                    tint = NeonGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

fun saveTextFileToDownloads(context: Context, fileName: String, mimeType: String, textContent: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(textContent.toByteArray())
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        android.util.Log.e("SaveFile", "Error saving text file", e)
        false
    }
}

fun saveAudioFileToDownloads(context: Context, textToSynthesize: String, tts: TextToSpeech?, onComplete: (Boolean, String?) -> Unit) {
    if (tts == null) {
        onComplete(false, null)
        return
    }
    try {
        val tempFile = java.io.File(context.cacheDir, "axolote_temp_${System.currentTimeMillis()}.wav")
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ExportAudio")
        
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "ExportAudio") {
                    val finalName = "axolote_audio_${System.currentTimeMillis()}.wav"
                    val success = copyFileToDownloads(context, tempFile, finalName, "audio/wav")
                    try {
                        tempFile.delete()
                    } catch (e: Exception) {}
                    onComplete(success, finalName)
                }
            }
            override fun onError(utteranceId: String?) {
                try {
                    tempFile.delete()
                } catch (e: Exception) {}
                onComplete(false, null)
            }
        })
        
        tts.synthesizeToFile(textToSynthesize, params, tempFile, "ExportAudio")
    } catch (e: Exception) {
        android.util.Log.e("SaveAudio", "Error saving audio file", e)
        onComplete(false, null)
    }
}

fun copyFileToDownloads(context: Context, sourceFile: java.io.File, destFileName: String, mimeType: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, destFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        android.util.Log.e("CopyFile", "Error copying to downloads", e)
        false
    }
}
