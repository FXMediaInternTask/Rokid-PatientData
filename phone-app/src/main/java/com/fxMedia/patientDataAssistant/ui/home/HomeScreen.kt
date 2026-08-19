package com.fxMedia.patientDataAssistant.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fxMedia.patientDataAssistant.BuildConfig
import com.fxMedia.patientDataAssistant.R
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.patientDataAssistant.ui.theme.RokidPhoneTheme

@Composable
fun HomeScreen(
    transcripts: List<String>,
    connectionState: ConnectionState,
    connectedGlassesName: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSendAnnotation: (String) -> Unit,
    isLoggedIn: Boolean = false,
    isLoginLoading: Boolean = false,
    onLogin: () -> Unit = {},
    onTestAzure: () -> Unit = {},
    isAzureValid: Boolean = false,
    isAzureChecking: Boolean = false,
    onResetConversation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    HomeScreenContent(
        transcripts = transcripts,
        onSendAnnotation = onSendAnnotation,
        connectionState = connectionState,
        connectedGlassesName = connectedGlassesName,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        isLoggedIn = isLoggedIn,
        isLoginLoading = isLoginLoading,
        onLogin = onLogin,
        onTestAzure = onTestAzure,
        isAzureValid = isAzureValid,
        isAzureChecking = isAzureChecking,
        onResetConversation = onResetConversation,
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
    isLoggedIn: Boolean = false,
    isLoginLoading: Boolean = false,
    onLogin: () -> Unit = {},
    onTestAzure: () -> Unit = {},
    isAzureValid: Boolean = false,
    isAzureChecking: Boolean = false,
    onResetConversation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Welcome Section
            Text(
                text = "Welcome to Rokid Assistant",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Ask AI for assistance during patient interactions",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.line_seperator_horizontal),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.line_seperator_vertical),
                    contentDescription = null,
                    modifier = Modifier.height(64.dp)
                )

                // Mic Button
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 64.dp)
                        .clickable { /* Handle Mic */ },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mic_container),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    Image(
                        painter = painterResource(id = R.drawable.mic_default),
                        contentDescription = "Mic",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.line_seperator_vertical),
                    contentDescription = null,
                    modifier = Modifier.height(64.dp).padding(horizontal = 4.dp)
                )

                // Connection Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable { if (connectionState == ConnectionState.CONNECTED) onDisconnect() else onConnect() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.wifi_and_chatbot_container),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "Paired"
                                ConnectionState.CONNECTING -> "Pairing"
                                else -> "Pair"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Image(
                            painter = painterResource(
                                id = if (connectionState == ConnectionState.CONNECTED) R.drawable.checklist_on else R.drawable.checklist_off
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.line_seperator_vertical),
                    contentDescription = null,
                    modifier = Modifier.height(64.dp).padding(horizontal = 4.dp)
                )

                // Auth Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clickable(enabled = !isLoginLoading) { onLogin() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.wifi_and_chatbot_container),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    
                    if (isLoginLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.chatbot_icon),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isLoggedIn) "Active" else "Login",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Image(
                                painter = painterResource(
                                    id = if (isLoggedIn) R.drawable.checklist_on else R.drawable.checklist_off
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.line_seperator_vertical),
                    contentDescription = null,
                    modifier = Modifier.height(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Image(
                painter = painterResource(id = R.drawable.line_seperator_horizontal),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Conversation Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.conversation_box),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Conversation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = onResetConversation, modifier = Modifier.size(24.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.delete_icon),
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val listState = rememberLazyListState()
                    
                    LaunchedEffect(transcripts.size) {
                        if (transcripts.isNotEmpty()) {
                            listState.animateScrollToItem(transcripts.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(transcripts) { message ->
                            ChatBubble(message)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input Section
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.writing_field),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = { innerTextField ->
                            if (textInput.isEmpty()) {
                                Text("Write here...", color = Color.Gray, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .clickable {
                            if (textInput.isNotBlank()) {
                                onSendAnnotation(textInput)
                                textInput = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.send_button),
                        contentDescription = "Send",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                    Text(
                        text = "SEND",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Version
            Text(
                text = "Version: ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }

    }
}

@Composable
private fun ChatBubble(message: String) {
    val isUser = message.startsWith("You:", ignoreCase = true)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .wrapContentHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Image(
                painter = painterResource(
                    id = if (isUser) R.drawable.user_chatbox else R.drawable.ai_chatbox
                ),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeScreenPreview() {
    RokidPhoneTheme {
        HomeScreenContent(
            transcripts = listOf(
                "You: hello what is the patient name?",
                "AI: The patient's name is Emaline Bte Hamza."
            ),
            connectionState = ConnectionState.DISCONNECTED,
            isLoggedIn = true
        )
    }
}
