package com.example.myfoodload.ui.map

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

// ─────────────────────────────────────────────────────────────────────────────
// 바텀시트 중간 상태 — 마커 클릭 시 표시되는 맛집 요약 카드
// (카카오맵 스타일: 이름+카테고리, 썸네일 2장, 주소+거리+뱃지, 전화/상세보기 버튼)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SelectedRestaurantDetailCard(
    recommendation: RecommendedRestaurantDto,
    onDismiss: () -> Unit,
    onDetailClick: () -> Unit,
    onPhoneClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val restaurant = recommendation.restaurant
    val isTrending = recommendation.mode == "TRENDING"
    val isDiscovery = recommendation.mode == "DISCOVERY"
    val matchPercent = (recommendation.matchScore * 100).roundToInt()
    val videoId = restaurant.sourceVideoId
    val hqUrl = videoId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
    val mqUrl = videoId?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" }
    val fallbackUrl = restaurant.thumbnailUrl

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 헤더: 카테고리 아이콘 + 이름 + 닫기(X)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = restaurant.category.toIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = restaurant.category.toKoreanLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "선택 해제",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 썸네일 2장 가로 배치
        val imageUrls = listOfNotNull(hqUrl ?: fallbackUrl, mqUrl).take(2)
        if (imageUrls.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                imageUrls.forEach { url ->
                    ThumbnailImage(
                        url = url,
                        name = restaurant.name,
                        fallbackIcon = { restaurant.category.toIcon() },
                        modifier = Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                // 이미지 1장일 때 빈 공간 채움
                if (imageUrls.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // 주소 + 거리 + 매칭 뱃지
        if (restaurant.address.isNotBlank()) {
            Text(
                text = restaurant.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDistance(recommendation.distanceMeters),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            MatchBadge(isTrending, isDiscovery, matchPercent, restaurant.viewCount)
        }

        // 추천 사유
        restaurant.recommendationReason?.takeIf { it.isNotBlank() }?.let { reason ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 태그
        val tagsToShow = restaurant.tags.take(4)
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
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 하단 버튼: [전화] [상세보기]
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onPhoneClick != null) {
                OutlinedButton(
                    onClick = onPhoneClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("전화", fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = onDetailClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("상세보기", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 내부 헬퍼
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThumbnailImage(
    url: String,
    name: String,
    fallbackIcon: @Composable () -> androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fallbackIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = fallbackIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        success = { SubcomposeAsyncImageContent(Modifier.fillMaxSize()) },
    )
}

@Composable
private fun MatchBadge(
    isTrending: Boolean,
    isDiscovery: Boolean,
    matchPercent: Int,
    viewCount: Long?,
) {
    when {
        isTrending && viewCount != null -> {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)) {
                Text(
                    text = formatViewCount(viewCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        isDiscovery -> {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)) {
                Text(
                    text = "태그 매칭",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        !isTrending && matchPercent > 0 -> {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)) {
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
}
