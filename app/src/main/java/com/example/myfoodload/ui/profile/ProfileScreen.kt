package com.example.myfoodload.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfoodload.MyFoodLoadApp
import com.example.myfoodload.shared.dto.FoodTagDto
import com.example.myfoodload.shared.dto.PriceRange
import com.example.myfoodload.shared.dto.UserPreferenceDto
import kotlin.math.roundToInt

/**
 * Phase 10: 내 취향 분석 화면.
 *
 * - LLM이 분석한 FoodTag 점수를 LinearProgressIndicator로 시각화
 * - 분위기(ambiance) 태그를 SuggestionChip으로 표시
 * - 가격대 선호도 표시
 * - 분석 결과 없으면 재분석 안내
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyFoodLoadApp
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(app.container.llmAnalysisApiService),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 취향 분석") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ProfileUiState.Loaded -> {
                ProfileContent(
                    preferences = state.preferences,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is ProfileUiState.Empty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "아직 취향 분석 결과가 없습니다",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "유튜브 좋아요 영상을 가져온 후 분석을 진행해주세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadPreferences() }) {
                        Text("새로고침")
                    }
                }
            }
            is ProfileUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadPreferences() }) {
                        Text("다시 시도")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    preferences: UserPreferenceDto,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // 음식 취향 태그 섹션
        if (preferences.foodTags.isNotEmpty()) {
            SectionTitle("음식 취향")
            val sorted = preferences.foodTags
                .filter { it.tag != "맛집" }
                .sortedByDescending { it.score }
            sorted.take(8).forEach { tag ->
                FoodTagRow(tag)
            }
        }

        // 분위기 태그 섹션
        if (preferences.ambianceTags.isNotEmpty()) {
            SectionTitle("선호 분위기")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                preferences.ambianceTags.forEach { ambiance ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(ambiance, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        // 가격대 섹션
        preferences.priceRange?.let { priceRange ->
            SectionTitle("선호 가격대")
            Text(
                text = priceRange.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Medium,
            )
        }

        // 분석 신뢰도 — 제거됨 (Phase 5 클린업)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FoodTagRow(tag: FoodTagDto) {
    val progress = (tag.score / 1.0).toFloat().coerceIn(0f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = tag.tag,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp),
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "${(progress * 100).roundToInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
    }
}

private fun PriceRange.displayName(): String = when (this) {
    PriceRange.LOW -> "저렴한 편 (1만원 미만)"
    PriceRange.MEDIUM -> "보통 (1~3만원)"
    PriceRange.HIGH -> "고급 (3만원 이상)"
    PriceRange.UNKNOWN -> "분석 불가"
}
