package com.fxMedia.RokidAPI.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rokid glasses dedicated theme
 * Optimized for AR display: high contrast, dark background, minimalist colors
 */

private val GlassesColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),          // Light blue - primary interactive elements
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFD0E4FF),
    
    secondary = Color(0xFF90CAF9),         // Light blue - success/confirm state
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1976D2),
    onSecondaryContainer = Color(0xFFBBDEFB),
    
    tertiary = Color(0xFFFFB74D),          // Orange - warning/attention
    onTertiary = Color.Black,
    
    error = Color(0xFFEF5350),             // Red - error
    onError = Color.White,
    
    background = Color.Black,              // Pure black background
    onBackground = Color.White,
    
    surface = Color.Black,                 // Pure black surface
    onSurface = Color.White,
    
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun RokidGlassesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GlassesColorScheme,
        typography = GlassesTypography,
        content = content
    )
}

val GlassesTypography = Typography(
    // Font sizes optimized for AR display
    displayLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    ),
    displayMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    ),
    headlineLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Medium
    ),
    headlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
)

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun RokidGlassesThemePreview() {
    RokidGlassesTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Display Large",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Headline Medium",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Body Large: Optimized for AR display with high contrast and dark background.",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { }) {
                        Text("Primary Button")
                    }
                    
                    FilledTonalButton(onClick = { }) {
                        Text("Secondary")
                    }
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Surface Variant",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "This is how a card looks in this theme.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
