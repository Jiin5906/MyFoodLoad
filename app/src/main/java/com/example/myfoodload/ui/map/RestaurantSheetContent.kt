package com.example.myfoodload.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto

@Composable
fun RestaurantSheetContent(
    currentTab: MapTab,
    onTabSelect: (MapTab) -> Unit,
    uiState: MapUiState,
    filteredRestaurants: List<RecommendedRestaurantDto>,
    sortedRestaurants: List<RecommendedRestaurantDto>,
    totalCount: Int,
    excludeVisited: Boolean = false,
    onExcludeVisitedToggle: () -> Unit = {},
    trendingUiState: MapUiState,
    selectedRestaurant: RecommendedRestaurantDto?,
    selectedCategory: CategoryType?,
    onCategorySelect: (CategoryType?) -> Unit,
    onRestaurantClick: (Long) -> Unit,
    onRestaurantSelect: (RecommendedRestaurantDto?) -> Unit,
    onRetry: () -> Unit,
    onTrendingRetry: () -> Unit,
    syncUiState: SyncUiState = SyncUiState.Idle,
    onYouTubeSync: () -> Unit = {},
    onSyncDismiss: () -> Unit = {},
    onSwitchToTrending: () -> Unit = {},
    onPhoneClick: ((String) -> Unit)? = null,
) {
    Column {
        // 드래그 핸들
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 5.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        shape = CircleShape,
                    ),
            )
        }

        // 선택된 맛집 요약 카드 (peek 영역에 표시, 탭 위에 배치)
        if (selectedRestaurant != null) {
            SelectedRestaurantDetailCard(
                recommendation = selectedRestaurant,
                onDismiss = { onRestaurantSelect(null) },
                onDetailClick = { onRestaurantClick(selectedRestaurant.restaurant.id) },
                onPhoneClick = selectedRestaurant.restaurant.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    if (onPhoneClick != null) ({ onPhoneClick(phone) }) else null
                },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }

        // 탭: 내 취향 맛집 | 핫한 맛집 (항상 표시)
        TabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                if (currentTab.ordinal < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[currentTab.ordinal]),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            divider = {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            },
        ) {
            Tab(
                selected = currentTab == MapTab.PERSONALIZED,
                onClick = { onTabSelect(MapTab.PERSONALIZED) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        "내 취향 맛집",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (currentTab == MapTab.PERSONALIZED) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
            Tab(
                selected = currentTab == MapTab.TRENDING,
                onClick = { onTabSelect(MapTab.TRENDING) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        "핫한 맛집",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (currentTab == MapTab.TRENDING) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }

        // 카테고리 필터 (항상 표시)
        CategoryFilterRow(
            selectedCategory = selectedCategory,
            onCategorySelect = onCategorySelect,
        )

        // 탭별 콘텐츠 (항상 표시 — Expanded 시 리스트 보임)
        when (currentTab) {
            MapTab.PERSONALIZED ->
                PersonalizedContent(
                    uiState = uiState,
                    filteredRestaurants = filteredRestaurants,
                    sortedRestaurants = sortedRestaurants,
                    totalCount = totalCount,
                    selectedRestaurant = selectedRestaurant,
                    selectedCategory = selectedCategory,
                    onRestaurantClick = onRestaurantClick,
                    onRestaurantSelect = onRestaurantSelect,
                    onRetry = onRetry,
                    syncUiState = syncUiState,
                    onYouTubeSync = onYouTubeSync,
                    onSyncDismiss = onSyncDismiss,
                    onSwitchToTrending = onSwitchToTrending,
                    onClearCategory = { onCategorySelect(null) },
                )

            MapTab.TRENDING -> {
                val trendingList = (trendingUiState as? MapUiState.Loaded)?.restaurants ?: emptyList()
                val filtered = if (selectedCategory == null) trendingList
                    else trendingList.filter { it.restaurant.category == selectedCategory }
                TrendingContent(
                    trendingUiState = trendingUiState,
                    sortedRestaurants = filtered,
                    selectedRestaurant = selectedRestaurant,
                    selectedCategory = selectedCategory,
                    onRestaurantClick = onRestaurantClick,
                    onRestaurantSelect = onRestaurantSelect,
                    onRetry = onTrendingRetry,
                    onClearCategory = { onCategorySelect(null) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 내 취향 맛집 콘텐츠
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PersonalizedContent(
    uiState: MapUiState,
    filteredRestaurants: List<RecommendedRestaurantDto>,
    sortedRestaurants: List<RecommendedRestaurantDto>,
    totalCount: Int,
    selectedRestaurant: RecommendedRestaurantDto?,
    selectedCategory: CategoryType?,
    onRestaurantClick: (Long) -> Unit,
    onRestaurantSelect: (RecommendedRestaurantDto?) -> Unit,
    onRetry: () -> Unit,
    syncUiState: SyncUiState = SyncUiState.Idle,
    onYouTubeSync: () -> Unit = {},
    onSyncDismiss: () -> Unit = {},
    onSwitchToTrending: () -> Unit = {},
    onClearCategory: () -> Unit = {},
) {
    when (uiState) {
        is MapUiState.Loading, is MapUiState.Idle -> SkeletonRestaurantList()

        is MapUiState.Loaded -> {
            if (filteredRestaurants.isEmpty()) {
                when {
                    selectedCategory != null && totalCount > 0 ->
                        EmptyState(
                            message = "주변에 ${selectedCategory.toKoreanLabel()} 맛집이 없어요",
                            icon = Icons.Default.SearchOff,
                            actionLabel = "모든 카테고리 보기",
                            onAction = onClearCategory,
                        )
                    totalCount == 0 && syncUiState is SyncUiState.Complete ->
                        EmptyState(
                            message = "아직 취향을 분석할 맛집 영상이 없어요",
                            icon = Icons.Outlined.TravelExplore,
                            actionLabel = "핫한 맛집 보러가기",
                            onAction = onSwitchToTrending,
                        )
                    totalCount == 0 ->
                        YouTubeSyncBanner(
                            syncUiState = syncUiState,
                            onSync = onYouTubeSync,
                            onDismiss = onSyncDismiss,
                        )
                    else ->
                        EmptyState(
                            message = "선택한 카테고리 맛집이 없어요",
                            icon = Icons.Default.SearchOff,
                            actionLabel = "모든 카테고리 보기",
                            onAction = onClearCategory,
                        )
                }
            } else {
                RestaurantList(
                    sortedRestaurants = sortedRestaurants,
                    selectedRestaurant = selectedRestaurant,
                    onRestaurantClick = onRestaurantClick,
                    onRestaurantSelect = onRestaurantSelect,
                )
            }
        }

        is MapUiState.Error ->
            ErrorState(message = uiState.message, onRetry = onRetry)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 핫한 맛집 콘텐츠
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TrendingContent(
    trendingUiState: MapUiState,
    sortedRestaurants: List<RecommendedRestaurantDto>,
    selectedRestaurant: RecommendedRestaurantDto?,
    selectedCategory: CategoryType?,
    onRestaurantClick: (Long) -> Unit,
    onRestaurantSelect: (RecommendedRestaurantDto?) -> Unit,
    onRetry: () -> Unit,
    onClearCategory: () -> Unit = {},
) {
    when (trendingUiState) {
        is MapUiState.Loading, is MapUiState.Idle -> SkeletonRestaurantList()

        is MapUiState.Loaded -> {
            if (sortedRestaurants.isEmpty()) {
                EmptyState(
                    message = if (selectedCategory != null) {
                        "주변에 ${selectedCategory.toKoreanLabel()} 인기 맛집이 없어요"
                    } else {
                        "주변 인기 맛집을 찾지 못했어요"
                    },
                    icon = Icons.Default.SearchOff,
                    actionLabel = if (selectedCategory != null) "모든 카테고리 보기" else null,
                    onAction = if (selectedCategory != null) onClearCategory else null,
                )
            } else {
                RestaurantList(
                    sortedRestaurants = sortedRestaurants,
                    selectedRestaurant = selectedRestaurant,
                    onRestaurantClick = onRestaurantClick,
                    onRestaurantSelect = onRestaurantSelect,
                )
            }
        }

        is MapUiState.Error ->
            ErrorState(message = trendingUiState.message, onRetry = onRetry)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 공통 상태 UI 컴포넌트
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    message: String,
    icon: ImageVector = Icons.Default.RestaurantMenu,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text("다시 시도")
        }
    }
}
