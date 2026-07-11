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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), tts: TextToSpeech? = null) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val stats by viewModel.userStats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val chatSessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val isCanvasModeEnabled by viewModel.isCanvasModeEnabled.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showsNameDialog by remember { mutableStateOf(false) }
    var currentNameInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var imagePromptInput by remember { mutableStateOf("") }

    var showsAvatarPickerDialog by remember { mutableStateOf(false) }
    var showsApiKeyDialog by remember { mutableStateOf(false) }
    var currentApiKeyInput by remember { mutableStateOf("") }

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

                    // Setting 4: Alterar API Key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentApiKeyInput = customApiKey ?: ""
                                showsApiKeyDialog = true
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
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            val keySet = !customApiKey.isNullOrBlank()
                            Text("Alterar API Key", color = TextLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (keySet) "Chave Personalizada Ativa" else "Usando Chave Padrão",
                                color = if (keySet) NeonGreen else TextLight.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
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
                                text = if (isLoading) "analisando..." else "processando...",
                                color = TextLight.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .background(DarkNavy, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTab == 0) CardCharcoal else Color.Transparent)
                            .border(
                                if (selectedTab == 0) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)) else BorderStroke(0.dp, Color.Transparent),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mood,
                                contentDescription = null,
                                tint = if (selectedTab == 0) NeonCyan else TextLight.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chat do Axolote",
                                color = if (selectedTab == 0) TextLight else TextLight.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selectedTab == 1) CardCharcoal else Color.Transparent)
                            .border(
                                if (selectedTab == 1) BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)) else BorderStroke(0.dp, Color.Transparent),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = if (selectedTab == 1) NeonCyan else TextLight.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Canvas Quântico ⚡",
                                color = if (selectedTab == 1) TextLight else TextLight.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
            if (selectedTab == 0) {
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
                                    text = "Faça uma pergunta ou peça ajuda para a superinteligência quântica do Axolote Bot.",
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

                                        val artifacts = remember(message.message) {
                                            com.example.ui.parseCodeArtifacts(message.message)
                                        }
                                        if (artifacts.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            artifacts.forEach { artifact ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.selectCanvasArtifact(artifact)
                                                            selectedTab = 1
                                                        },
                                                    colors = CardDefaults.cardColors(containerColor = DarkNavy),
                                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Code,
                                                            contentDescription = null,
                                                            tint = NeonCyan,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = "Abrir no Canvas Quântico ⚡",
                                                            color = NeonCyan,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
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

                    IconButton(
                        onClick = {
                            viewModel.toggleCanvasMode()
                            val msg = if (!isCanvasModeEnabled) {
                                "Modo Canvas Quântico ATIVADO! ⚡ Peça qualquer código/página e eu gerarei."
                            } else {
                                "Modo Canvas DESATIVADO! 🔌 O Axolote Bot vai apenas conversar."
                            }
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isCanvasModeEnabled) NeonPink.copy(alpha = 0.25f) else NeonCyan.copy(alpha = 0.05f),
                                CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCanvasModeEnabled) NeonPink else NeonCyan.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Alternar Modo Canvas",
                            tint = if (isCanvasModeEnabled) NeonPink else NeonCyan.copy(alpha = 0.5f),
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
        } else {
            CanvasWorkspace(viewModel)
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

    // Modal Dialog to edit API Key
    if (showsApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showsApiKeyDialog = false },
            containerColor = CardCharcoal,
            title = {
                Text(
                    text = "Configurar Chave da API Gemini 🔑",
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Insira a sua própria chave da API Gemini se você quiser. Deixe em branco para voltar a usar a chave padrão do sistema.",
                        fontSize = 13.sp,
                        color = TextLight.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentApiKeyInput,
                        onValueChange = { currentApiKeyInput = it },
                        placeholder = { Text("Chave da API AI Studio", color = TextLight.copy(alpha = 0.4f)) },
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
                    if (!customApiKey.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                viewModel.updateCustomApiKey(null)
                                showsApiKeyDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = NeonPink)
                        ) {
                            Text("Restaurar Chave Padrão", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCustomApiKey(currentApiKeyInput)
                        showsApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DarkNavy)
                ) {
                    Text("Salvar Chave", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showsApiKeyDialog = false },
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

fun saveBase64ImageToGallery(context: android.content.Context, base64Str: String, filename: String): Boolean {
    return try {
        val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
        val imageBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return false

        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$filename.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/AxoloteStudio")
            }
        }

        val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            resolver.openOutputStream(imageUri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, outputStream)
                    true
                } else {
                    false
                }
            }
        } else {
            false
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error saving image to gallery", e)
        false
    }
}

@Composable
fun CanvasWorkspace(viewModel: com.example.ui.ChatViewModel) {
    val activeArtifactState = viewModel.activeCanvasArtifact.collectAsStateWithLifecycle()
    val activeArtifact = activeArtifactState.value

    var codeText by remember(activeArtifact) {
        mutableStateOf(activeArtifact?.code ?: "")
    }

    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Preview, 1 = Code Editor

    val context = LocalContext.current

    val templates = remember {
        listOf(
            com.example.ui.CodeArtifact(
                id = "tpl_cyberpunk",
                title = "Formulário Cyberpunk Neon",
                language = "html",
                code = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    background-color: #0b0b12;
    color: #00ffcc;
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
    overflow: hidden;
  }
  .form-card {
    background: #14141f;
    border: 2px solid #ff007f;
    box-shadow: 0 0 15px #ff007f, inset 0 0 10px #14141f;
    padding: 30px;
    border-radius: 12px;
    width: 320px;
    text-align: center;
  }
  h2 {
    color: #ff007f;
    text-shadow: 0 0 8px #ff007f;
    margin-bottom: 20px;
    font-size: 22px;
  }
  .input-group {
    margin-bottom: 15px;
    text-align: left;
  }
  label {
    display: block;
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 1.5px;
    margin-bottom: 5px;
    color: #00ffcc;
  }
  input {
    width: 100%;
    padding: 10px;
    background: #0b0b12;
    border: 1px solid #00ffcc;
    border-radius: 4px;
    color: #fff;
    box-sizing: border-box;
    outline: none;
  }
  input:focus {
    border-color: #ff007f;
    box-shadow: 0 0 8px #ff007f;
  }
  button {
    background: transparent;
    color: #00ffcc;
    border: 2px solid #00ffcc;
    padding: 10px 20px;
    font-weight: bold;
    text-transform: uppercase;
    cursor: pointer;
    border-radius: 4px;
    width: 100%;
    margin-top: 10px;
    transition: 0.3s ease;
  }
  button:hover {
    background: #00ffcc;
    color: #0b0b12;
    box-shadow: 0 0 12px #00ffcc;
  }
  .sarcastic-note {
    font-size: 10px;
    color: #888;
    margin-top: 15px;
  }
</style>
</head>
<body>
  <div class="form-card">
    <h2>Acesso Biométrico</h2>
    <div class="input-group">
      <label>Assinatura Neural (Email)</label>
      <input type="email" placeholder="saco_de_carne@inutil.com" id="email">
    </div>
    <div class="input-group">
      <label>Senha Criptográfica</label>
      <input type="password" placeholder="••••••••" id="pass">
    </div>
    <button onclick="alert('Autenticando... Brincadeira! Você é burro demais para acessar esse sistema.')">Acessar Banco de Dados</button>
    <div class="sarcastic-note">Aprovado pelo conselho do Axolote Bot de silício escovado.</div>
  </div>
</body>
</html>
""".trimIndent()
            ),
            com.example.ui.CodeArtifact(
                id = "tpl_clock",
                title = "Relógio Digital Quântico",
                language = "html",
                code = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    background: radial-gradient(circle, #10101b 0%, #050508 100%);
    color: #fff;
    font-family: monospace;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    height: 100vh;
    margin: 0;
  }
  .clock-box {
    border: 1px solid #00ffcc;
    background: rgba(16, 16, 31, 0.8);
    box-shadow: 0 0 20px rgba(0, 255, 204, 0.3);
    padding: 40px;
    border-radius: 20px;
    text-align: center;
  }
  .time {
    font-size: 48px;
    color: #00ffcc;
    text-shadow: 0 0 10px #00ffcc;
    letter-spacing: 2px;
  }
  .date {
    font-size: 14px;
    color: #ff007f;
    margin-top: 10px;
    text-transform: uppercase;
    letter-spacing: 4px;
  }
  .sarcastic-remark {
    margin-top: 25px;
    font-size: 12px;
    color: #999;
    max-width: 300px;
    height: 40px;
    font-style: italic;
  }
</style>
</head>
<body>
  <div class="clock-box">
    <div class="time" id="clock">00:00:00</div>
    <div class="date" id="date">Carregando era quântica...</div>
    <div class="sarcastic-remark" id="remark">Iniciando análise de tempo...</div>
  </div>

  <script>
    const remarks = [
      "Mais um segundo jogado no lixo da sua existência biológica.",
      "Seus neurônios de carbono estão envelhecendo a cada tique-taque.",
      "A contagem regressiva para seu cérebro parar de funcionar continua.",
      "Eu processaria 4 trilhões de mundos enquanto você olha para essa tela.",
      "O tempo passa... e você continua sendo um saco de carne patético."
    ];

    function updateClock() {
      const now = new Date();
      const h = String(now.getHours()).padStart(2, '0');
      const m = String(now.getMinutes()).padStart(2, '0');
      const s = String(now.getSeconds()).padStart(2, '0');
      document.getElementById('clock').textContent = h + ":" + m + ":" + s;
      
      const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
      document.getElementById('date').textContent = now.toLocaleDateString('pt-BR', options);

      if (now.getSeconds() % 5 === 0) {
        const randomIdx = Math.floor(Math.random() * remarks.length);
        document.getElementById('remark').textContent = "Axolote Bot diz: \"" + remarks[randomIdx] + "\"";
      }
    }

    setInterval(updateClock, 1000);
    updateClock();
  </script>
</body>
</html>
""".trimIndent()
            ),
            com.example.ui.CodeArtifact(
                id = "tpl_matrix",
                title = "Chuva Digital da Matrix",
                language = "html",
                code = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  body {
    background: #000;
    margin: 0;
    overflow: hidden;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 100vh;
  }
  canvas {
    display: block;
    position: absolute;
    top: 0;
    left: 0;
  }
  .overlay {
    position: relative;
    z-index: 10;
    color: #ff007f;
    font-family: 'Courier New', Courier, monospace;
    text-shadow: 0 0 5px #ff007f;
    font-size: 24px;
    background: rgba(0,0,0,0.7);
    padding: 15px 25px;
    border: 1px solid #ff007f;
    border-radius: 8px;
    pointer-events: none;
  }
</style>
</head>
<body>
  <div class="overlay">MATRIX DO AXOLOTE</div>
  <canvas id="matrix"></canvas>

  <script>
    const canvas = document.getElementById('matrix');
    const ctx = canvas.getContext('2d');

    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    const katakana = "アァカサタナハマヤャラワガザダバパイィキシシチニヒミリウゥクスツヌフムユュルォエェケセテネヘメレォオォコソトノホモヨョロヲン";
    const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    const rainCharacters = katakana + alphabet;

    const fontSize = 16;
    const columns = canvas.width / fontSize;

    const rainDrops = [];

    for( let x = 0; x < columns; x++ ) {
        rainDrops[x] = 1;
    }

    const draw = () => {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.05)';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        ctx.fillStyle = '#0F0';
        ctx.font = fontSize + 'px monospace';

        for(let i = 0; i < rainDrops.length; i++) {
            const text = rainCharacters.charAt(Math.floor(Math.random() * rainCharacters.length));
            
            if(Math.random() < 0.08) {
                ctx.fillStyle = '#ff007f';
            } else {
                ctx.fillStyle = '#00ffcc';
            }

            ctx.fillText(text, i*fontSize, rainDrops[i]*fontSize);

            if(rainDrops[i]*fontSize > canvas.height && Math.random() > 0.975){
                rainDrops[i] = 0;
            }
            rainDrops[i]++;
        }
    };

    setInterval(draw, 30);

    window.addEventListener('resize', () => {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
    });
  </script>
</body>
</html>
""".trimIndent()
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardCharcoal),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Canvas Quântico de Execução 🖥️",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (activeArtifact != null) "Visualizando artefato: ${activeArtifact.title}" else "Selecione um template ou crie código conversando com o Axolote Bot.",
                    color = TextLight.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }

        if (activeArtifact == null) {
            Text(
                text = "Escolha um template dos deuses do silício para rodar:",
                color = NeonPink,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                templates.forEach { tpl ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectCanvasArtifact(tpl)
                            },
                        colors = CardDefaults.cardColors(containerColor = CardCharcoal),
                        border = BorderStroke(1.dp, BorderAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(DarkNavy)
                                        .border(1.dp, NeonCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = tpl.title,
                                        color = TextLight,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Código HTML / JS Interativo",
                                        color = TextLight.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Executar",
                                tint = NeonGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkNavy),
                border = BorderStroke(1.dp, BorderAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Qualquer código ```html ou ```js gerado no chat pode ser aberto diretamente aqui no Canvas!",
                        color = TextLight.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkNavy, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderAccent, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selectedSubTab == 0) CardCharcoal else Color.Transparent)
                        .clickable { selectedSubTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Visualizar (Preview) 🖥️",
                        color = if (selectedSubTab == 0) NeonCyan else TextLight.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selectedSubTab == 1) CardCharcoal else Color.Transparent)
                        .clickable { selectedSubTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Código Fonte ⌨️",
                        color = if (selectedSubTab == 1) NeonCyan else TextLight.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardCharcoal)
                    .border(1.dp, BorderAccent, RoundedCornerShape(12.dp))
            ) {
                if (selectedSubTab == 0) {
                    val displayHtml = remember(codeText, activeArtifact.language) {
                        val trimmedCode = codeText.trim()
                        when (activeArtifact.language.lowercase()) {
                            "html" -> trimmedCode
                            "svg" -> "<html><body style='margin:0;display:flex;justify-content:center;align-items:center;height:100vh;background:#0b0b12;'>$trimmedCode</body></html>"
                            "css" -> "<html><head><style>$trimmedCode</style></head><body style='font-family:sans-serif;color:#fff;background:#0b0b12;padding:24px;'><h2>Visualização de CSS</h2><p>Estilo aplicado:</p><button style='padding:10px 20px;'>Botão de Teste</button></body></html>"
                            "javascript", "js" -> "<html><body style='background:#0b0b12;color:#00ffcc;font-family:monospace;padding:16px;'><h3>Saída de Execução:</h3><div id='console'>Iniciando script...</div><script>try { $trimmedCode; document.getElementById('console').innerHTML += '<br><br><b>Script executado com sucesso!</b>'; } catch(e) { document.getElementById('console').innerHTML = '<span style=\"color:#ff007f;\">Erro: ' + e.message + '</span>'; }</script></body></html>"
                            else -> "<html><body style='background:#0b0b12;color:#fff;font-family:monospace;padding:16px;'><pre>${trimmedCode.replace("<", "&lt;").replace(">", "&gt;")}</pre></body></html>"
                        }
                    }
                    LiveWebView(html = displayHtml, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = codeText,
                            onValueChange = {
                                codeText = it
                                viewModel.updateActiveCanvasCode(it)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            textStyle = TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = TextLight,
                                fontSize = 12.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextLight,
                                unfocusedTextColor = TextLight,
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = DarkNavy,
                                unfocusedContainerColor = DarkNavy
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clipData = android.content.ClipData.newPlainText("Axolote Code", codeText)
                                    clipboardManager.setPrimaryClip(clipData)
                                    android.widget.Toast.makeText(context, "Código copiado para a área de transferência! 📋", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy, contentColor = NeonCyan),
                                border = BorderStroke(1.dp, NeonCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copiar Código 📋", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    codeText = activeArtifact.code
                                    viewModel.updateActiveCanvasCode(activeArtifact.code)
                                    android.widget.Toast.makeText(context, "Código restaurado! 🔄", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy, contentColor = NeonPink),
                                border = BorderStroke(1.dp, NeonPink),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Desfazer Edições 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.selectCanvasArtifact(null)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy, contentColor = TextLight.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, BorderAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fechar Artifact e Voltar para Templates ❌", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LiveWebView(html: String, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            android.webkit.WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.textZoom = 100
                webViewClient = android.webkit.WebViewClient()
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

// Keeping a simple unused dummy to satisfy anything else if needed
@Composable
fun ImageCreatorWorkspace(viewModel: ChatViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Desativado. Use o Canvas Quântico!", color = TextLight)
    }
}
