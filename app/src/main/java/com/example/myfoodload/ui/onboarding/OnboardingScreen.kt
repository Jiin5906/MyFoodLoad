package com.example.myfoodload.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import com.example.myfoodload.MyFoodLoadApp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingPage(
        icon = Icons.Default.PlayCircle,
        title = "유튜브 좋아요에서\n발견한 맛집",
        description = "좋아요한 먹방 영상을 분석해\n나도 몰랐던 내 취향 맛집을 찾아드려요.",
    ),
    OnboardingPage(
        icon = Icons.Default.LocationOn,
        title = "지금 내 위치에서\n가장 가까운 맛집",
        description = "현재 위치를 기준으로\n걸어갈 수 있는 맛집을 추천해드려요.",
    ),
    OnboardingPage(
        icon = Icons.Default.Star,
        title = "좋아요할수록\n더 정확해지는 추천",
        description = "영상에 좋아요를 누를수록\n나만의 취향 프로필이 더욱 정교해져요.",
    ),
)

/**
 * E-3: 온보딩 화면.
 *
 * - HorizontalPager로 3페이지 구성
 * - DataStore에 onboarding_completed 저장 후 AUTH 화면으로 이동
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyFoodLoadApp
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState { ONBOARDING_PAGES.size }
    val isLastPage = pagerState.currentPage == ONBOARDING_PAGES.lastIndex

    val finishOnboarding: () -> Unit = {
        scope.launch {
            app.container.tokenManager.markOnboardingCompleted()
            onFinish()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Skip 버튼 (마지막 페이지엔 숨김)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, top = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!isLastPage) {
                    TextButton(onClick = finishOnboarding) {
                        Text(
                            "건너뛰기",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 페이저
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                OnboardingPageContent(page = ONBOARDING_PAGES[page])
            }

            // 페이지 인디케이터
            Row(
                modifier = Modifier.padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(ONBOARDING_PAGES.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        label = "dot_width_$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(height = 8.dp, width = width)
                            .clip(CircleShape)
                            .background(
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }

            // 하단 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 이전 버튼 (첫 페이지엔 숨김)
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("이전")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                // 다음 / 시작하기 버튼
                Button(
                    onClick = {
                        if (isLastPage) {
                            finishOnboarding()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isLastPage) "시작하기" else "다음")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
