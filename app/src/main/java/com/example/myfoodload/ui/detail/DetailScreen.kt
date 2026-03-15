package com.example.myfoodload.ui.detail

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfoodload.MyFoodLoadApp
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.PriceRange
import com.example.myfoodload.shared.dto.RestaurantDto
import com.example.myfoodload.ui.common.AdmobBanner
import com.example.myfoodload.ui.common.InterstitialAdManager

/**
 * Phase 9: 맛집 상세 화면.
 *
 * - 맛집 기본 정보(이름, 카테고리, 주소, 가격대, 태그) 표시
 * - sourceVideoId가 있으면 YouTube Shorts IFrame WebView 재생
 *   (9:16 비율 MATCH_PARENT 강제, 레터박스 없음)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    restaurantId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyFoodLoadApp
    val viewModel: DetailViewModel = viewModel(
        factory = DetailViewModel.Factory(
            restaurantId = restaurantId,
            restaurantApiService = app.container.restaurantApiService,
            favoritesApiService = app.container.favoritesApiService,
            visitApiService = app.container.visitApiService,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입 시 전면 광고 미리 로드 (사용자가 버튼 누를 때 즉시 표시 가능)
    LaunchedEffect(Unit) {
        InterstitialAdManager.load(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("맛집 상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    val loaded = uiState as? DetailUiState.Loaded
                    if (loaded != null) {
                        // 공유 버튼
                        IconButton(onClick = {
                            val shareText = buildString {
                                append(loaded.restaurant.name)
                                if (loaded.restaurant.address.isNotBlank()) {
                                    append("\n${loaded.restaurant.address}")
                                }
                                loaded.restaurant.kakaoPlaceUrl?.let { append("\n$it") }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "맛집 공유"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "공유")
                        }
                        // 방문 완료 버튼
                        IconButton(onClick = { viewModel.toggleVisit() }) {
                            Icon(
                                imageVector = if (loaded.isVisited) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = if (loaded.isVisited) "방문 취소" else "방문 완료",
                                tint = if (loaded.isVisited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 즐겨찾기 버튼
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (loaded.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (loaded.isFavorite) "즐겨찾기 해제" else "즐겨찾기 추가",
                                tint = if (loaded.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is DetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is DetailUiState.Loaded -> {
                DetailContent(
                    restaurant = state.restaurant,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is DetailUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.retry() }) { Text("다시 시도") }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    restaurant: RestaurantDto,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // YouTube Shorts 재생 (sourceVideoId가 있는 경우)
        restaurant.sourceVideoId?.let { videoId ->
            YouTubeShortsPlayer(
                videoId = videoId,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f),
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 이름 + 카테고리
            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = restaurant.category.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = restaurant.priceRange.displayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            // 주소
            if (restaurant.address.isNotBlank()) {
                Text(
                    text = restaurant.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 추천 이유 스토리텔링 (B-4)
            val reason = restaurant.recommendationReason
            if (!reason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            // 태그 chips
            if (restaurant.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    restaurant.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 배너 광고
            AdmobBanner(
                modifier = Modifier.padding(vertical = 12.dp),
            )

            // 전화 버튼
            val phone = restaurant.phone
            if (!phone.isNullOrBlank()) {
                CallButton(phone = phone, modifier = Modifier.fillMaxWidth())
            }

            // 카카오맵에서 보기 (평점/리뷰/메뉴/사진 등 상세 정보)
            val placeUrl = restaurant.kakaoPlaceUrl
            if (!placeUrl.isNullOrBlank() && (placeUrl.startsWith("http://") || placeUrl.startsWith("https://"))) {
                Spacer(Modifier.height(8.dp))
                KakaoPlaceButton(placeUrl = placeUrl, modifier = Modifier.fillMaxWidth())
            }

            // YouTube 앱에서 보기 + 길찾기 (가로 배치)
            val videoId = restaurant.sourceVideoId
            val hasVideo = !videoId.isNullOrBlank()
            val hasCoords = restaurant.latitude != null && restaurant.longitude != null
            if (hasVideo || hasCoords) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (hasVideo) {
                        YouTubeAppButton(
                            videoId = videoId!!,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (hasCoords) {
                        KakaoRouteButton(
                            name = restaurant.name,
                            latitude = restaurant.latitude!!,
                            longitude = restaurant.longitude!!,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * YouTube Shorts WebView 플레이어.
 *
 * /embed/ URL은 Shorts에서 임베딩 거부(오류 152-4)를 일으킴.
 * youtube.com/shorts/videoId 를 직접 loadUrl()로 로드하면 Shorts 플레이어 자체가 열림.
 * Chrome Mobile UA 필수 — WebView UA 감지 시 YouTube가 재생 차단.
 */
@Composable
private fun YouTubeShortsPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    // Chrome Mobile UA — WebView UA는 YouTube가 재생 차단
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.6099.210 Mobile Safari/537.36"
                }
                val webView = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webView, true)
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val host = request.url.host ?: return true
                        // YouTube·Google 도메인만 WebView 내에서 허용
                        return !host.endsWith("youtube.com") &&
                            !host.endsWith("googlevideo.com") &&
                            !host.endsWith("google.com") &&
                            !host.endsWith("ytimg.com") &&
                            !host.endsWith("ggpht.com")
                    }
                }
                // Shorts 플레이어 페이지 직접 로드 (iframe embed 방식 대신)
                loadUrl("https://www.youtube.com/shorts/$videoId")
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.settings.javaScriptEnabled = false
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        },
        modifier = modifier,
    )
}

@Composable
private fun KakaoPlaceButton(
    placeUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(placeUrl)))
        },
        modifier = modifier,
    ) {
        Text("카카오맵에서 보기  (리뷰·메뉴·사진)")
    }
}

@Composable
private fun CallButton(
    phone: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace("-", "")}"))
            context.startActivity(intent)
        },
        modifier = modifier,
    ) {
        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
        Text("전화")
    }
}

@Composable
private fun YouTubeAppButton(
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            InterstitialAdManager.showThenExecute(context) {
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                    setPackage("com.google.android.youtube")
                }
                if (appIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(appIntent)
                } else {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/shorts/$videoId")),
                    )
                }
            }
        },
        modifier = modifier,
    ) {
        Text("YouTube 앱에서 보기")
    }
}

@Composable
private fun KakaoRouteButton(
    name: String,
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            InterstitialAdManager.showThenExecute(context) {
                val uri = Uri.parse(
                    "kakaomap://route?ep=$latitude,$longitude&by=FOOT",
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://map.kakao.com/link/to/$name,$latitude,$longitude"),
                        ),
                    )
                }
            }
        },
        modifier = modifier,
    ) {
        Text("길찾기")
    }
}

private fun CategoryType.displayName(): String = when (this) {
    CategoryType.KOREAN -> "한식"
    CategoryType.JAPANESE -> "일식"
    CategoryType.CHINESE -> "중식"
    CategoryType.WESTERN -> "양식"
    CategoryType.CAFE -> "카페/디저트"
    CategoryType.STREET_FOOD -> "기타"
    CategoryType.UNKNOWN -> "기타"
}

private fun PriceRange.displayName(): String = when (this) {
    PriceRange.LOW -> "1만원 미만"
    PriceRange.MEDIUM -> "1~3만원"
    PriceRange.HIGH -> "3만원 이상"
    PriceRange.UNKNOWN -> ""
}
