package com.example.myfoodload.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material.icons.filled.SoupKitchen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myfoodload.shared.dto.CategoryType
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// 카테고리 유틸리티 확장 함수
// ─────────────────────────────────────────────────────────────────────────────

fun CategoryType.toKoreanLabel(): String = when (this) {
    CategoryType.KOREAN -> "한식"
    CategoryType.JAPANESE -> "일식"
    CategoryType.CHINESE -> "중식"
    CategoryType.WESTERN -> "양식"
    CategoryType.CAFE -> "카페"
    CategoryType.STREET_FOOD -> "분식"
    CategoryType.UNKNOWN -> "기타"
}

/** Material Icons Extended 아이콘으로 카테고리를 표현 */
fun CategoryType.toIcon(): ImageVector = when (this) {
    CategoryType.KOREAN -> Icons.Default.RiceBowl        // 밥그릇 — 한식
    CategoryType.JAPANESE -> Icons.Default.SetMeal        // 정식 트레이 — 일식
    CategoryType.CHINESE -> Icons.Default.RamenDining     // 면 그릇+젓가락 — 짜장면
    CategoryType.WESTERN -> Icons.Default.LocalDining     // 포크&나이프 — 양식
    CategoryType.CAFE -> Icons.Default.LocalCafe          // 커피잔 — 카페
    CategoryType.STREET_FOOD -> Icons.Default.SoupKitchen // 그릇 — 떡볶이
    CategoryType.UNKNOWN -> Icons.Default.Restaurant      // 식당 — 기타
}

// ─────────────────────────────────────────────────────────────────────────────
// 포맷 유틸리티
// ─────────────────────────────────────────────────────────────────────────────

fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.roundToInt()}m"
    else -> "${"%.1f".format(meters / 1000)}km"
}

fun formatViewCount(count: Long): String = when {
    count >= 100_000_000 -> "${"%.0f".format(count / 100_000_000.0)}억회"
    count >= 10_000 -> "${"%.1f".format(count / 10_000.0)}만회"
    else -> "%,d회".format(count)
}

// ─────────────────────────────────────────────────────────────────────────────
// 카테고리 필터 가로 스크롤 (LazyRow)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CategoryFilterRow(
    selectedCategory: CategoryType?,
    onCategorySelect: (CategoryType?) -> Unit,
) {
    val categories = CategoryType.entries.filter { it != CategoryType.STREET_FOOD }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "전체" 칩
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelect(null) },
                label = {
                    Text("전체", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

        // 카테고리별 칩 — Material Icon + 라벨 텍스트
        items(categories) { cat ->
            FilterChip(
                selected = selectedCategory == cat,
                onClick = { onCategorySelect(cat) },
                label = {
                    Text(cat.toKoreanLabel(), style = MaterialTheme.typography.labelMedium)
                },
                leadingIcon = {
                    Icon(
                        imageVector = cat.toIcon(),
                        contentDescription = cat.toKoreanLabel(),
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

    }
}
