package com.fxMedia.patientDataAssistantRokid

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
import androidx.compose.foundation.border
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
import com.fxMedia.patientDataAssistantRokid.service.WakeWordService
import com.fxMedia.patientDataAssistantRokid.ui.theme.RokidGlassesTheme
import com.fxMedia.patientDataAssistantRokid.viewmodel.GlassesViewModel
import com.fxMedia.patientDataAssistantRokid.viewmodel.GlassesUIState
import com.fxMedia.patientDataAssistantRokid.viewmodel.AppScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow

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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val viewModel = glassesViewModel ?: return super.onKeyDown(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                viewModel.onNavigateUp()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                viewModel.onNavigateDown()
                true
            }
            else -> super.onKeyDown(keyCode, event)
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
                is AppScreen.Response -> {
                    ResponseScreen(
                        uiState = uiState,
                        scrollEvent = viewModel.scrollEvent,
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
fun ResponseScreen(
    uiState: GlassesUIState,
    scrollEvent: Flow<Int>,
    onDone: () -> Unit
) {
    val rokidWhite = Color.White// STOPPPP EDITING THIS

    // Reset page index whenever a new response arrives
    var currentPageIndex by remember(uiState.aiResponse) { mutableIntStateOf(0) }

    // Logic to split text into pages (4 lines per page)
    val pages = remember(uiState.aiResponse) {
        val lines = mutableListOf<String>()
        uiState.aiResponse.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) return@forEach
            var currentLine = ""
            val maxChars = 38 
            paragraph.split(" ").forEach { word ->
                if ((currentLine.length + word.length + 1) > maxChars) {
                    lines.add(currentLine.trim())
                    currentLine = word
                } else {
                    currentLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine.trim())
        }
        lines.chunked(4)
    }
    
    if (pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No response", color = rokidWhite)
        }
        return
    }

    LaunchedEffect(pages.size) {
        scrollEvent.collectLatest { direction ->
            currentPageIndex = (currentPageIndex + direction).coerceIn(0, pages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDone() }
    ) {
        // Main Text Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 40.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            pages[currentPageIndex].forEach { line ->
                Text(
                    text = line,
                    color = rokidWhite, // Use green as in screenshot
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Start
                )
            }
        }

        // Right Scroll Bar
        if (pages.size > 1) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(150.dp)
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .background(rokidWhite.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            ) {
                val barHeight = 150f / pages.size
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight.dp)
                        .offset(y = (currentPageIndex * barHeight).dp)
                        .background(rokidWhite, RoundedCornerShape(2.dp))
                )
            }
        }

        // Bottom Page Indicator (Green Bubble)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .background(rokidWhite.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                .border(1.5.dp, rokidWhite.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${currentPageIndex + 1}/${pages.size}",
                color = rokidWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
            }
        }
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

    Box(
        modifier = Modifier
            .size(12.dp)
            .background(Color.Red.copy(alpha = alpha), RoundedCornerShape(6.dp))
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
            .clickable { onScreenTap() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = uiState.displayText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.hintText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )
        }

        if (showDeviceSelector) {
            DeviceSelectorDialog(
                devices = uiState.availableDevices,
                onDeviceSelected = onDeviceSelected,
                onDismiss = onDismissDeviceSelector
            )
        }
    }
}

@Composable
fun DeviceSelectorDialog(
    devices: List<BluetoothDevice>,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Phone to Connect") },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text("No paired devices found. Please pair in Settings.")
                } else {
                    devices.forEach { device ->
                        @Suppress("MissingPermission")
                        TextButton(
                            onClick = { onDeviceSelected(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(device.name ?: "Unknown Device")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TranslateMainScreenContent(
    isConnected: Boolean,
    isListening: Boolean,
    isProcessing: Boolean,
    displayText: String,
    hintText: String,
    connectedDeviceName: String?,
    availableDevices: List<BluetoothDevice>,
    showDeviceSelector: Boolean,
    onScreenTap: () -> Unit,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onDismissSelector: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onScreenTap() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = hintText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            
            if (isProcessing) {
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = Color(0xFF00FF00))
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isConnected) Color(0xFF00FF00) else Color.Red,
                        RoundedCornerShape(4.dp)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) "Connected: ${connectedDeviceName ?: "Phone"}" else "Disconnected",
                color = Color.White,
                fontSize = 12.sp
            )
        }
        
        if (showDeviceSelector) {
            DeviceSelectorDialog(
                devices = availableDevices,
                onDeviceSelected = onDeviceSelected,
                onDismiss = onDismissSelector
            )
        }
    }
}

@Composable
fun AppVersionDisplay(modifier: Modifier = Modifier) {
    Text(
        text = "v1.0.4",
        color = Color.White.copy(alpha = 0.3f),
        fontSize = 12.sp,
        modifier = modifier
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ResponseScreenPreview() {
    RokidGlassesTheme {
        ResponseScreen(
            uiState = GlassesUIState(
                aiResponse = "Technological evolution and future of AI glasses.\n\n1. The Convergence of Miniaturization and Intelligence"
            ),
            scrollEvent = emptyFlow(),
            onDone = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun MainScreenPreview() {
    RokidGlassesTheme {
        TranslateMainScreenContent(
            isConnected = true,
            isListening = false,
            isProcessing = false,
            displayText = "Connected",
            hintText = "Tap to record",
            connectedDeviceName = "Rokid Phone",
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
fun RecordingScreenPreview() {
    RokidGlassesTheme {
        RecordingScreen(
            displayText = "Listening...",
            isListening = true,
            isProcessing = false,
            onDone = {}
        )
    }
}
