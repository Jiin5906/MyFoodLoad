package com.example.myfoodload.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import kotlin.math.roundToInt

@Composable
internal fun RestaurantList(
    sortedRestaurants: List<RecommendedRestaurantDto>,
    selectedRestaurant: RecommendedRestaurantDto?,
    onRestaurantClick: (Long) -> Unit,
    onRestaurantSelect: (RecommendedRestaurantDto?) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    // 마커 클릭 시 해당 카드로 스크롤
    LaunchedEffect(selectedRestaurant) {
        if (selectedRestaurant != null) {
            val index = sortedRestaurants.indexOfFirst { it.restaurant.id == selectedRestaurant.restaurant.id }
            if (index >= 0) listState.animateScrollToItem(index)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sortedRestaurants.forEachIndexed { index, rec ->
            item(key = rec.restaurant.id) {
                RestaurantCard(
                    recommendation = rec,
                    rank = index + 1,
                    isSelected = rec == selectedRestaurant,
                    onClick = { onRestaurantSelect(rec) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 맛집 카드 — PERSONALIZED(취향%) / TRENDING(조회수) 뱃지 자동 전환
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RestaurantCard(
    recommendation: RecommendedRestaurantDto,
    rank: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val restaurant = recommendation.restaurant
    val isTrending = recommendation.mode == "TRENDING"
    val isDiscovery = recommendation.mode == "DISCOVERY"
    val matchPercent = (recommendation.matchScore * 100).roundToInt()

    val imageUrl = restaurant.thumbnailUrl
        ?: restaurant.sourceVideoId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imgShape = RoundedCornerShape(12.dp)
            val bgColor = MaterialTheme.colorScheme.surfaceVariant

            if (imageUrl != null) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = restaurant.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(imgShape),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(bgColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = restaurant.category.toIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(bgColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = restaurant.category.toIcon(),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    },
                    success = {
                        SubcomposeAsyncImageContent(Modifier.fillMaxSize())
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(color = bgColor, shape = imgShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = restaurant.category.toIcon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatDistance(recommendation.distanceMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (restaurant.address.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = restaurant.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = restaurant.category.toIcon(),
                        contentDescription = "${restaurant.category.toKoreanLabel()} 카테고리",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = restaurant.category.toKoreanLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (isTrending) {
                        val badgeText = restaurant.viewCount?.let { formatViewCount(it) } ?: "인기"
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    } else if (isDiscovery) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "태그 매칭",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    } else if (matchPercent > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                        ) {
                            Text(
                                text = "취향 ${matchPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                val tagsToShow = restaurant.tags.take(3)
                val videoTitle = if (isTrending) recommendation.sourceVideoTitle else null

                if (tagsToShow.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        tagsToShow.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                } else if (videoTitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // DISCOVERY 모드: 추천 사유 표시
                if (isDiscovery && !restaurant.recommendationReason.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = restaurant.recommendationReason!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
