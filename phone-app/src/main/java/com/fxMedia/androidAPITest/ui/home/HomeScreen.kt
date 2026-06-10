package com.fxMedia.androidAPITest.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

        // Manual Annotation Card
        ManualAnnotationCard(onSend = onSendAnnotation)

        Spacer(modifier = Modifier.height(8.dp))

        // Text reserve from API
        Text(
            text = "Latest API Response:",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF88B0C4)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
        ) {
            Text(
                text = transcripts.firstOrNull() ?: "Waiting for data...",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
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
                    text = "API Token Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = if (isLoggedIn) "Token Valid" else "No Token / Expired",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLoggedIn) Color(0xFF81C784) else Color(0xFFE57373)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = onLogin,
                enabled = !isLoggedIn && !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF88B0C4)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(if (isLoggedIn) "Authorized" else "Login")
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
                    text = if (connectionState == ConnectionState.CONNECTED) "Connected to" else "Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = if (connectionState == ConnectionState.CONNECTED) (deviceName ?: "Glasses") else "Disconnected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connectionState == ConnectionState.CONNECTED) Color(0xFF81C784) else Color(0xFFE57373)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Button(
                onClick = if (connectionState == ConnectionState.CONNECTED) onDisconnect else onConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (connectionState == ConnectionState.CONNECTED) Color.Gray.copy(alpha = 0.2f) else Color(0xFF88B0C4)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Text(if (connectionState == ConnectionState.CONNECTED) "Disconnect" else "Connect")
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Manual Annotation",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF88B0C4)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type annotation here...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedTextColor = Color.White,
                    focusedTextColor = Color.White,
                    cursorColor = Color(0xFF88B0C4),
                    focusedBorderColor = Color(0xFF88B0C4),
                    unfocusedBorderColor = Color.Gray
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text)
                                text = ""
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (text.isNotBlank()) Color(0xFF88B0C4) else Color.Gray
                        )
                    }
                }
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
