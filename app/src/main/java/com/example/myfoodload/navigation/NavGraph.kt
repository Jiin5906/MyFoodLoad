package com.example.myfoodload.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myfoodload.MyFoodLoadApp
import com.example.myfoodload.ui.auth.AuthScreen
import com.example.myfoodload.ui.detail.DetailScreen
import com.example.myfoodload.ui.favorite.FavoriteScreen
import com.example.myfoodload.ui.map.MapScreen
import com.example.myfoodload.ui.onboarding.OnboardingScreen
import com.example.myfoodload.ui.profile.ProfileScreen
import com.example.myfoodload.ui.search.SearchScreen
import com.example.myfoodload.ui.settings.SettingsScreen

/** 앱 내 화면 경로 정의 */
object Screen {
    const val ONBOARDING = "onboarding"
    const val AUTH      = "auth"
    const val MAP       = "map"
    const val DETAIL    = "detail/{restaurantId}"
    const val PROFILE   = "profile"
    const val SETTINGS  = "settings"
    const val FAVORITES = "favorites"
    const val SEARCH    = "search"

    fun detail(restaurantId: Long) = "detail/$restaurantId"
}

@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val app = context.applicationContext as MyFoodLoadApp

    val onboardingCompleted by app.container.tokenManager
        .isOnboardingCompleted()
        .collectAsStateWithLifecycle(initialValue = null)

    val accessToken by app.container.tokenManager
        .getAccessToken()
        .collectAsStateWithLifecycle(initialValue = null)

    // DataStore 로딩 완료 전 — 로딩 인디케이터 표시, NavHost 생성 보류
    if (onboardingCompleted == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // DataStore 로딩 완료 — 시작 화면 확정
    val startDest = when {
        onboardingCompleted == true && !accessToken.isNullOrBlank() -> Screen.MAP
        onboardingCompleted == true -> Screen.AUTH
        else -> Screen.ONBOARDING
    }

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = tween(300),
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300),
            ) + fadeOut(animationSpec = tween(300))
        },
    ) {
        composable(Screen.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    navController.navigate(Screen.AUTH) {
                        popUpTo(Screen.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.AUTH) {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.MAP) {
                        popUpTo(Screen.AUTH) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.MAP) {
            MapScreen(
                onRestaurantClick = { restaurantId ->
                    navController.navigate(Screen.detail(restaurantId))
                },
                onProfileClick = {
                    navController.navigate(Screen.PROFILE)
                },
                onFavoritesClick = {
                    navController.navigate(Screen.FAVORITES)
                },
                onSearchClick = {
                    navController.navigate(Screen.SEARCH)
                },
            )
        }

        composable(
            route = Screen.DETAIL,
            arguments = listOf(navArgument("restaurantId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val restaurantId = backStackEntry.arguments?.getLong("restaurantId") ?: return@composable
            DetailScreen(
                restaurantId = restaurantId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSettingsClick = { navController.navigate(Screen.SETTINGS) },
            )
        }

        composable(Screen.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(Screen.FAVORITES) {
            FavoriteScreen(
                onBack = { navController.popBackStack() },
                onRestaurantClick = { restaurantId ->
                    navController.navigate(Screen.detail(restaurantId))
                },
            )
        }

        composable(Screen.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onRestaurantClick = { restaurantId ->
                    navController.navigate(Screen.detail(restaurantId))
                },
            )
        }
    }
}
