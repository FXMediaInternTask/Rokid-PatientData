package com.fxMedia.androidAPITest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fxMedia.androidAPITest.ui.home.HomeScreen
import com.fxMedia.androidAPITest.ui.navigation.NavRoutes
import com.fxMedia.androidAPITest.ui.theme.RokidPhoneTheme
import com.fxMedia.androidAPITest.viewmodel.PhoneViewModel

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkPermissions()
        
        setContent {
            RokidPhoneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PhoneMainScreen()
                }
            }
        }
    }
    
    private fun checkPermissions() {
        val requiredPermissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ))
        }
        
        requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }
}

@Composable
fun PhoneMainScreen(
    viewModel: PhoneViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    Box(modifier = Modifier.fillMaxSize()){
        Image(
            painter = painterResource(R.drawable.image_background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .padding(start = 16.dp, top = 5.dp),
                    contentScale = ContentScale.FillWidth
                )
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = NavRoutes.HOME,
                modifier = Modifier.padding(padding)
            ) {
                composable(NavRoutes.HOME) {
                    HomeScreen(
                        transcripts = uiState.transcripts,
                        connectionState = uiState.connectionState,
                        connectedGlassesName = uiState.connectedGlassesName,
                        onConnect = { viewModel.startScanning() },
                        onDisconnect = { viewModel.disconnect() },
                        onSendAnnotation = { viewModel.sendTestAnnotation(it) },
                        micSource = uiState.micSource,
                        onToggleMic = { viewModel.toggleMicSource() },
                        audioOutput = uiState.audioOutput,
                        onToggleOutput = { viewModel.toggleAudioOutput() },
                        isLiveActive = uiState.isElevenLabsLiveActive,
                        onToggleLive = { viewModel.toggleElevenLabsLive() },
                        isLoggedIn = uiState.isLoggedIn,
                        isLoginLoading = uiState.isLoginLoading,
                        onLogin = { viewModel.performLogin() },
                        onTestAzure = { viewModel.testAzureSTT() },
                        isAzureValid = uiState.isAzureValid,
                        isAzureChecking = uiState.isAzureChecking,
                        onResetConversation = { viewModel.resetConversation() },
                        statusMessage = uiState.statusMessage
                    )
                }
            }
        }
    }
}
