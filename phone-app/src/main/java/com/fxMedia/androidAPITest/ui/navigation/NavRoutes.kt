package com.fxMedia.androidAPITest.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.fxMedia.androidAPITest.R

/**
 * Navigation destinations for the app
 * Simplified for Manual Annotation flow
 */
object NavRoutes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LOG_VIEWER = "log_viewer"
    const val TTS_SETTINGS = "tts_settings"
}

/**
 * Bottom navigation items configuration
 */
enum class BottomNavDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val labelResId: Int
) {
    HOME(
        route = NavRoutes.HOME,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        labelResId = R.string.nav_home
    ),
    SETTINGS(
        route = NavRoutes.SETTINGS,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        labelResId = R.string.nav_settings
    )
}
