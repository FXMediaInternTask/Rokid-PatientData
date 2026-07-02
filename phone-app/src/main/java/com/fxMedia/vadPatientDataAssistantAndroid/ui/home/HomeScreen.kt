package com.fxMedia.vadPatientDataAssistantAndroid.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.vadPatientDataAssistantAndroid.ui.theme.RokidPhoneTheme
import com.fxMedia.vadPatientDataAssistantAndroid.viewmodel.AudioOutput
import com.fxMedia.vadPatientDataAssistantAndroid.viewmodel.MicSource
import com.fxMedia.vadPatientDataAssistantAndroid.data.log.LogEntry
import com.fxMedia.vadPatientDataAssistantAndroid.data.log.LogLevel
import com.fxMedia.vadPatientDataAssistantAndroid.R

@Composable
fun HomeScreen(
    transcripts: List<String>,
    connectionState: ConnectionState,
    connectedGlassesName: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSendAnnotation: (String) -> Unit,
    micSource: MicSource = MicSource.PHONE,
    onToggleMic: () -> Unit = {},
    audioOutput: AudioOutput = AudioOutput.PHONE,
    onToggleOutput: () -> Unit = {},
    isLiveActive: Boolean = false,
    onToggleLive: () -> Unit = {},
    isLoggedIn: Boolean = false,
    isLoginLoading: Boolean = false,
    onLogin: () -> Unit = {},
    onTestAzure: () -> Unit = {},
    isAzureValid: Boolean = false,
    isAzureChecking: Boolean = false,
    onResetConversation: () -> Unit = {},
    statusMessage: String? = null,
    logs: List<LogEntry> = emptyList(),
    appVersion: String = "1.0.0",
    modifier: Modifier = Modifier
) {
    HomeScreenContent(
        transcripts = transcripts,
        onSendAnnotation = onSendAnnotation,
        connectionState = connectionState,
        connectedGlassesName = connectedGlassesName,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        micSource = micSource,
        onToggleMic = onToggleMic,
        audioOutput = audioOutput,
        onToggleOutput = onToggleOutput,
        isLiveActive = isLiveActive,
        onToggleLive = onToggleLive,
        isLoggedIn = isLoggedIn,
        isLoginLoading = isLoginLoading,
        onLogin = onLogin,
        onTestAzure = onTestAzure,
        isAzureValid = isAzureValid,
        isAzureChecking = isAzureChecking,
        onResetConversation = onResetConversation,
        statusMessage = statusMessage,
        logs = logs,
        appVersion = appVersion,
        modifier = modifier
    )
}

@Composable
fun HomeScreenContent(
    transcripts: List<String> = emptyList(),
    onSendAnnotation: (String) -> Unit = {},
    connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    connectedGlassesName: String? = null,
    onConnect: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    micSource: MicSource = MicSource.PHONE,
    onToggleMic: () -> Unit = {},
    audioOutput: AudioOutput = AudioOutput.PHONE,
    onToggleOutput: () -> Unit = {},
    isLiveActive: Boolean = false,
    onToggleLive: () -> Unit = {},
    isLoggedIn: Boolean = false,
    isLoginLoading: Boolean = false,
    onLogin: () -> Unit = {},
    onTestAzure: () -> Unit = {},
    isAzureValid: Boolean = false,
    isAzureChecking: Boolean = false,
    onResetConversation: () -> Unit = {},
    statusMessage: String? = null,
    logs: List<LogEntry> = emptyList(),
    appVersion: String = "1.0.0",
    modifier: Modifier = Modifier
) {
    var visibleMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusMessage) {
        visibleMessage = statusMessage
        if (statusMessage != null) {
            kotlinx.coroutines.delay(8000)
            if (visibleMessage == statusMessage) {
                visibleMessage = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
    ) {
        // 1. Header Section
        HeaderSection()

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Action Boxes Row (Mic, Connection, Auth)
        ActionRow(
            isLiveActive = isLiveActive,
            onToggleLive = onToggleLive,
            connectionState = connectionState,
            connectedGlassesName = connectedGlassesName,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            isLoggedIn = isLoggedIn,
            onLogin = onLogin,
            isLoginLoading = isLoginLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Status Message
        AnimatedVisibility(
            visible = visibleMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            visibleMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF88B0C4),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // 4. Conversation Box
        ConversationSection(
            transcripts = transcripts,
            onResetConversation = onResetConversation,
            modifier = Modifier.weight(1f)
        )

        // 5. System Log (conditional)
        val hasErrors = logs.any { it.level == LogLevel.ERROR }
        if (hasErrors) {
            Spacer(modifier = Modifier.height(12.dp))
            SystemLogSection(logs = logs)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Input Section (Manual Text Input)
        InputRow(onSend = onSendAnnotation)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Version: $appVersion",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun HeaderSection() {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Welcome to Rokid Assistant",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        Text(
            text = "Ask AI for assistance during patient interactions",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))
        Image(
            painter = painterResource(id = R.drawable.line_seperator_horizontal),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
private fun ActionRow(
    isLiveActive: Boolean,
    onToggleLive: () -> Unit,
    connectionState: ConnectionState,
    connectedGlassesName: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    isLoginLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VerticalSeparator()

        // Mic Box
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .paint(painterResource(id = R.drawable.mic_container), contentScale = ContentScale.FillBounds)
                .clickable { onToggleLive() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = if (isLiveActive) R.drawable.mic_on else R.drawable.mic_default),
                contentDescription = "Mic",
                modifier = Modifier.size(24.dp)
            )
        }

        VerticalSeparator()

        // Connection Box
        val isConnected = connectionState == ConnectionState.CONNECTED
        val isConnecting = connectionState == ConnectionState.CONNECTING
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .paint(painterResource(id = R.drawable.wifi_and_chatbot_container), contentScale = ContentScale.FillBounds)
                .clickable { if (isConnected || isConnecting) onDisconnect() else onConnect() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(id = R.drawable.wifi_icon), null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isConnected -> connectedGlassesName ?: "Connected"
                        isConnecting -> "Connecting..."
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Image(
                    painter = painterResource(id = if (isConnected) R.drawable.checklist_on else R.drawable.checklist_off),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        VerticalSeparator()

        // Auth Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .paint(painterResource(id = R.drawable.wifi_and_chatbot_container), contentScale = ContentScale.FillBounds)
                .clickable { onLogin() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(id = R.drawable.chatbot_icon), null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLoggedIn) "Authenticated" else "Auth Required",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLoginLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Image(
                        painter = painterResource(id = if (isLoggedIn) R.drawable.checklist_on else R.drawable.checklist_off),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        VerticalSeparator()
    }
}

@Composable
private fun VerticalSeparator() {
    Image(
        painter = painterResource(id = R.drawable.line_seperator_vertical),
        contentDescription = null,
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        contentScale = ContentScale.FillHeight
    )
}

@Composable
private fun ConversationSection(
    transcripts: List<String>,
    onResetConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .paint(painterResource(id = R.drawable.conversation_box), contentScale = ContentScale.FillBounds)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conversation",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                IconButton(onClick = onResetConversation, modifier = Modifier.size(20.dp)) {
                    Image(painterResource(id = R.drawable.delete_icon), "Clear", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val scrollState = rememberScrollState()
            LaunchedEffect(transcripts.size) {
                if (transcripts.isNotEmpty()) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (transcripts.isEmpty()) {
                    Text(
                        text = "No history yet",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    // Display transcripts in standard chronological order (Oldest top, Newest bottom)
                    // Use asReversed() because PhoneViewModel prepends new messages
                    transcripts.asReversed().forEach { text ->
                        ChatMessageBubble(text)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemLogSection(logs: List<LogEntry>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .paint(painterResource(id = R.drawable.conversation_box), contentScale = ContentScale.FillBounds)
            .padding(12.dp)
    ) {
        Column {
            Text("System Log", style = MaterialTheme.typography.labelSmall, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            
            val listState = rememberLazyListState()
            LaunchedEffect(logs.size) {
                if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(logs) { entry ->
                    Text(
                        text = entry.toDisplayString(),
                        style = TextStyle(fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = when (entry.level) {
                            LogLevel.ERROR -> Color(0xFFE57373)
                            LogLevel.WARN -> Color(0xFFFFB74D)
                            else -> Color.Gray
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(text: String) {
    val isUser = text.startsWith("You:", ignoreCase = true)
    
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleRes = if (isUser) R.drawable.user_chatbox else R.drawable.ai_chatbox
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Image(
                painter = painterResource(id = bubbleRes),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Text(
                text = formatMarkdown(text.trim()),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun InputRow(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .paint(painterResource(id = R.drawable.writing_field), contentScale = ContentScale.FillBounds)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color(0xFF81C784)),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text("Write here...", color = Color.Gray, fontSize = 14.sp)
                    }
                    innerTextField()
                }
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .paint(painterResource(id = R.drawable.send_button), contentScale = ContentScale.FillBounds)
                .clickable {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("SEND", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun formatMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val prefix = when {
            text.startsWith("You:", ignoreCase = true) -> "You:"
            text.startsWith("AI:", ignoreCase = true) -> "AI:"
            else -> null
        }
        
        val contentToProcess = if (prefix != null) {
            withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                append(prefix)
            }
            text.substring(prefix.length)
        } else {
            text
        }

        val parts = contentToProcess.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeScreenPreview() {
    RokidPhoneTheme {
        HomeScreenContent(
            transcripts = listOf("You: Hi, Good afternoon!", "AI: Good afternoon! How can I help?"),
            connectionState = ConnectionState.CONNECTED,
            connectedGlassesName = "Rokid Max",
            isLiveActive = true,
            isLoggedIn = true
        )
    }
}
