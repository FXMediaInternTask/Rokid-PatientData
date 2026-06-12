package com.fxMedia.RokidAPI

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fxMedia.RokidAPI.service.WakeWordService
import com.fxMedia.RokidAPI.ui.theme.RokidGlassesTheme
import com.fxMedia.RokidAPI.viewmodel.GlassesViewModel
import com.fxMedia.RokidAPI.viewmodel.GlassesUIState
import com.fxMedia.RokidAPI.viewmodel.AppScreen

class MainActivity : ComponentActivity() {

    private var glassesViewModel: GlassesViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) startServices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
        }

        checkPermissions()

        setContent {
            RokidGlassesTheme {
                val viewModel: GlassesViewModel = viewModel(
                    factory = GlassesViewModel.Factory(this)
                )
                glassesViewModel = viewModel

                TranslateMainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val viewModel = glassesViewModel ?: return super.onKeyUp(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                val holdMs = (event?.eventTime ?: 0L) - (event?.downTime ?: 0L)
                if (holdMs < 500L) {
                    viewModel.onPrimaryTap()
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startServices()
        }
    }

    private fun startServices() {
        if (!WakeWordService.isRunning) {
            val serviceIntent = Intent(this, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun TranslateMainScreen(viewModel: GlassesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val appScreen by viewModel.appScreen.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedContent(
            targetState = appScreen,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                is AppScreen.Main -> {
                    if (!uiState.isConnected) {
                        ConnectionScreen(
                            uiState = uiState,
                            onScreenTap = { viewModel.onPrimaryTap() },
                            onDeviceSelected = { device -> viewModel.connectToDevice(device) },
                            showDeviceSelector = uiState.showDeviceSelector,
                            onDismissDeviceSelector = { viewModel.dismissDeviceSelector() }
                        )
                    } else {
                        TranslateMainScreenContent(
                            isConnected = uiState.isConnected,
                            isListening = uiState.isListening,
                            isProcessing = uiState.isProcessing,
                            displayText = uiState.displayText,
                            hintText = uiState.hintText,
                            connectedDeviceName = uiState.connectedDeviceName,
                            cxrConnectedPhoneName = uiState.cxrConnectedPhoneName,
                            availableDevices = uiState.availableDevices,
                            showDeviceSelector = uiState.showDeviceSelector,
                            onScreenTap = { viewModel.onPrimaryTap() },
                            onDeviceSelected = { device -> viewModel.connectToDevice(device) },
                            onDismissSelector = { viewModel.dismissDeviceSelector() }
                        )
                    }
                }
                is AppScreen.Recording -> {
                    RecordingScreen(
                        displayText = uiState.displayText,
                        isListening = uiState.isListening,
                        isProcessing = uiState.isProcessing,
                        onDone = { viewModel.onPrimaryTap() }
                    )
                }
            }
        }

        AppVersionDisplay(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        )
    }
}

@Composable
fun RecordingScreen(
    displayText: String, 
    isListening: Boolean,
    isProcessing: Boolean,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDone() }
    ) {
        // Bottom gradient for text background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (isListening) {
                RecordingIndicator()
            } else if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color(0xFF64B5F6),
                    strokeWidth = 3.dp
                )
            } else {
                Text(
                    text = "Tap to dismiss",
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Text(
            text = if (isListening) "Tap to finish" else "AI Assistant",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
        )
    }
}

@Composable
fun RecordingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(0xFFF44336).copy(alpha = alpha), RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "RECORDING",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun AppVersionDisplay(modifier: Modifier = Modifier) {
    Text(
        text = "v${BuildConfig.VERSION_NAME}",
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        modifier = modifier
    )
}

@Composable
fun ConnectionScreen(
    uiState: GlassesUIState,
    onScreenTap: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    showDeviceSelector: Boolean,
    onDismissDeviceSelector: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onScreenTap() }
    ) {
        StatusIndicator(
            isConnected = uiState.isConnected,
            isListening = uiState.isListening,
            deviceName = uiState.connectedDeviceName,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        MainDisplayArea(
            displayText = uiState.displayText,
            isProcessing = uiState.isProcessing,
            modifier = Modifier.align(Alignment.Center)
        )

        HintText(
            hint = uiState.hintText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        if (showDeviceSelector) {
            DeviceSelectorDialog(
                devices = uiState.availableDevices,
                cxrConnectedPhoneName = uiState.cxrConnectedPhoneName,
                onDeviceSelected = onDeviceSelected,
                onDismiss = onDismissDeviceSelector
            )
        }
    }
}

@Composable
fun TranslateMainScreenContent(
    isConnected: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    displayText: String,
    hintText: String,
    connectedDeviceName: String?,
    cxrConnectedPhoneName: String?,
    availableDevices: List<BluetoothDevice>,
    showDeviceSelector: Boolean,
    onScreenTap: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismissSelector: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onScreenTap() }
    ) {
        StatusIndicator(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            isConnected = isConnected,
            isListening = isListening,
            deviceName = connectedDeviceName
        )

        MainDisplayArea(
            modifier = Modifier.align(Alignment.Center),
            displayText = displayText,
            isProcessing = isProcessing
        )

        HintText(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            hint = hintText
        )

        if (showDeviceSelector) {
            DeviceSelectorDialog(
                devices = availableDevices,
                cxrConnectedPhoneName = cxrConnectedPhoneName,
                onDeviceSelected = onDeviceSelected,
                onDismiss = onDismissSelector
            )
        }
    }
}

@Composable
fun StatusIndicator(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    isListening: Boolean,
    deviceName: String? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(
                color = if (isConnected) Color(0xFF64B5F6) else Color(0xFFFF5722),
                label = if (isConnected) "Connected" else "Disconnected"
            )

            AnimatedVisibility(
                visible = isListening,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                StatusDot(
                    color = Color(0xFFF44336),
                    label = "Recording"
                )
            }
        }

        if (isConnected && deviceName != null) {
            Text(
                text = deviceName,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun StatusDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = RoundedCornerShape(50))
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun MainDisplayArea(
    modifier: Modifier = Modifier,
    displayText: String,
    isProcessing: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = Color(0xFF64B5F6),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = displayText,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HintText(
    modifier: Modifier = Modifier,
    hint: String
) {
    Text(
        text = hint,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun DeviceSelectorDialog(
    devices: List<BluetoothDevice>,
    cxrConnectedPhoneName: String? = null,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedDevices = remember(devices, cxrConnectedPhoneName) {
        if (cxrConnectedPhoneName != null) {
            devices.sortedByDescending {
                @Suppress("MissingPermission")
                it.name?.equals(cxrConnectedPhoneName, ignoreCase = true) == true
            }
        } else {
            devices
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1A),
        title = { Text(text = "Select Device", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sortedDevices.isEmpty()) {
                    Text(text = "No paired devices", color = Color.Gray)
                }
                sortedDevices.forEach { device ->
                    @Suppress("MissingPermission")
                    val name = device.name ?: "Unknown"
                    val isRecommended = cxrConnectedPhoneName != null &&
                            name.equals(cxrConnectedPhoneName, ignoreCase = true)

                    Button(
                        onClick = { onDeviceSelected(device) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecommended) Color(0xFF1E3A5F) else Color(0xFF2A2A2A)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, color = Color.White)
                            if (isRecommended) {
                                Text(
                                    text = "★ Recommended",
                                    color = Color(0xFF64B5F6),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64B5F6))
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  PREVIEWS
// ═══════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewTranslateMainScreen() {
    RokidGlassesTheme {
        TranslateMainScreenContent(
            isConnected = true,
            isListening = false,
            isProcessing = false,
            displayText = "Tap to record",
            hintText = "Tap right of glasses",
            connectedDeviceName = "My Phone",
            cxrConnectedPhoneName = "My Phone",
            availableDevices = emptyList(),
            showDeviceSelector = false,
            onScreenTap = {},
            onDeviceSelected = {},
            onDismissSelector = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewConnectionScreen() {
    RokidGlassesTheme {
        ConnectionScreen(
            uiState = GlassesUIState(
                isConnected = false,
                displayText = "Not connected",
                hintText = "Tap to connect"
            ),
            onScreenTap = {},
            onDeviceSelected = {},
            showDeviceSelector = false,
            onDismissDeviceSelector = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun PreviewRecordingScreen() {
    RokidGlassesTheme {
        RecordingScreen(
            displayText = "Testing recording UI...",
            isListening = true,
            isProcessing = false,
            onDone = {}
        )
    }
}
