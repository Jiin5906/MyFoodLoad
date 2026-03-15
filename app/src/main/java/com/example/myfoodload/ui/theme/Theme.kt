package com.example.myfoodload.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FoodLightColorScheme = lightColorScheme(
    primary = FoodOrange,
    secondary = FoodAmber,
    tertiary = FoodBrown,
    background = FoodCream,
    surface = FoodCream,
)

@Composable
fun MyFoodLoadTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FoodLightColorScheme,
        typography = Typography,
        content = content,
    )
}
