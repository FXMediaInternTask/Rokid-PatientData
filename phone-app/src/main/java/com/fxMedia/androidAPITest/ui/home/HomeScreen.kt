package com.fxMedia.androidAPITest.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fxMedia.rokidcommon.protocol.ConnectionState
import com.fxMedia.androidAPITest.ui.theme.RokidPhoneTheme

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Simple Title
        Text(
            text = "Rokid Assistant",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // Connection Status
        ConnectionStatusCard(
            connectionState = connectionState,
            deviceName = connectedGlassesName,
            onConnect = onConnect,
            onDisconnect = onDisconnect
        )

        // API Login Card
        LoginCard(
            isLoggedIn = isLoggedIn,
            isLoading = isLoginLoading,
            onLogin = onLogin
        )

        // Azure STT Status & Test Card
        AzureStatusCard(
            isValid = isAzureValid,
            isChecking = isAzureChecking,
            onTest = onTestAzure
        )

        // Manual Annotation Card
        ManualAnnotationCard(onSend = onSendAnnotation)

        Spacer(modifier = Modifier.height(8.dp))

        // Latest API Response
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Conversation:",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF88B0C4)
            )
            IconButton(
                onClick = onResetConversation,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Reset Conversation",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
        ) {
            if (transcripts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(text = "No history yet", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(transcripts) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Divider(
                            color = Color.White.copy(alpha = 0.05f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginCard(
    isLoggedIn: Boolean,
    isLoading: Boolean,
    onLogin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chatbot Auth",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isLoggedIn) Color(0xFF81C784) else Color(0xFFE57373),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isLoggedIn) "Authenticated" else "Not Logged In",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLoggedIn) Color.White else Color(0xFFE57373)
                    )
                }
            }
            
            Button(
                onClick = onLogin,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoggedIn) Color.White.copy(alpha = 0.1f) else Color(0xFF88B0C4)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(if (isLoggedIn) "Refresh" else "Login")
                }
            }
        }
    }
}

@Composable
private fun AzureStatusCard(
    isValid: Boolean,
    isChecking: Boolean,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Azure STT Service",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF0078D4))
                        } else {
                            Icon(
                                imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isValid) Color(0xFF81C784) else Color(0xFFE57373),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isValid) "Connected" else "Key/Region Invalid",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
                
                Button(
                    onClick = onTest,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0078D4)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Wav")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    deviceName: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bluetooth Glasses",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = if (connectionState == ConnectionState.CONNECTED) (deviceName ?: "Connected") else "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connectionState == ConnectionState.CONNECTED) Color(0xFF81C784) else Color.White
                )
            }
            
            Button(
                onClick = if (connectionState == ConnectionState.CONNECTED) onDisconnect else onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState == ConnectionState.CONNECTED) Color.Red.copy(alpha = 0.6f) else Color(0xFF88B0C4)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (connectionState == ConnectionState.CONNECTED) "Stop" else "Connect")
            }
        }
    }
}

@Composable
private fun ManualAnnotationCard(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Manual Text Test...", color = Color.Gray, fontSize = 14.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    cursorColor = Color(0xFF88B0C4),
                    focusedBorderColor = Color(0xFF88B0C4),
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text)
                                text = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF88B0C4))
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeScreenPreview() {
    RokidPhoneTheme {
        HomeScreenContent(
            transcripts = listOf("Text example from the API service"),
            onSendAnnotation = {}
        )
    }
}
