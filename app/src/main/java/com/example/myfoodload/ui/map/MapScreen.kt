package com.example.myfoodload.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfoodload.MyFoodLoadApp
import com.example.myfoodload.ui.common.AdmobBanner
import kotlinx.coroutines.launch

/**
 * 지도 메인 화면 (카카오 맵 기반).
 *
 * 3단 바텀시트:
 *   - 최하 (미선택): peek 80dp — 탭만 보임
 *   - 기본 (마커 클릭): peek ~48% — 요약 카드 (이름+사진+주소+버튼)
 *   - 풀  (스와이프 업): Expanded — 요약 카드 + 탭 + 맛집 리스트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onRestaurantClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onFavoritesClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyFoodLoadApp
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModel.Factory(
            app.container.recommendationApiService,
            app.container.recommendationDatabase,
            app.container.youTubeIngestionApiService,
            app.container.llmAnalysisApiService,
            app.container.geocodingApiService,
            app.container.tokenManager,
        ),
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trendingUiState by viewModel.trendingUiState.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filteredRestaurants by viewModel.filteredRestaurants.collectAsStateWithLifecycle()
    val sortedRestaurants by viewModel.sortedRestaurants.collectAsStateWithLifecycle()
    val excludeVisited by viewModel.excludeVisited.collectAsStateWithLifecycle()
    val syncUiState by viewModel.syncUiState.collectAsStateWithLifecycle()
    val cameraEvent by viewModel.cameraEvent.collectAsStateWithLifecycle()
    val isMapInteracting by viewModel.isMapInteracting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                event.onAction?.invoke()
            }
        }
    }

    val youtubeSyncLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.handleYouTubeConsentResult(context, result.data)
    }

    LaunchedEffect(syncUiState) {
        if (syncUiState is SyncUiState.AwaitingYoutubeConsent) {
            val intentSender = (syncUiState as SyncUiState.AwaitingYoutubeConsent).intentSender
            youtubeSyncLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.loadNearbyRestaurants(context)
    }

    LaunchedEffect(Unit) {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.loadNearbyRestaurants(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == MapTab.TRENDING && trendingUiState is MapUiState.Idle) {
            viewModel.loadTrendingRestaurants(context)
        }
    }

    val scope = rememberCoroutineScope()

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = false,
            confirmValueChange = { newValue ->
                when (newValue) {
                    // Hidden 전환 거부 → 시트가 사라지지 않고 peek만 80dp로 축소
                    SheetValue.Hidden -> {
                        viewModel.setMapInteracting(true)
                        false
                    }
                    // Expanded 도달 → 기본 모드(40%)로 복귀 준비
                    SheetValue.Expanded -> {
                        viewModel.setMapInteracting(false)
                        true
                    }
                    else -> true
                }
            },
        ),
    )

    // ── 3단 바텀시트 peek 높이 (즉시 전환, 애니메이션 없음) ────
    // 최하 (80dp): 드래그 다운 → 탭만 보임
    // 기본 (~40%): 기본 상태 → 탭 + 카테고리 + 리스트 상단
    // 선택 (~48%): 마커 클릭 → 요약 카드 + 탭 시작
    val configuration = LocalConfiguration.current
    val peekHeight = when {
        selectedRestaurant != null -> (configuration.screenHeightDp * 0.48f).dp
        isMapInteracting -> 80.dp
        else -> (configuration.screenHeightDp * 0.4f).dp
    }

    // 선택/해제 시 PartiallyExpanded로 전환
    val sheetState = scaffoldState.bottomSheetState
    LaunchedEffect(selectedRestaurant) {
        sheetState.partialExpand()
    }

    // BackHandler: Expanded → Partial, Partial+선택 → 선택 해제
    BackHandler(
        enabled = sheetState.currentValue == SheetValue.Expanded || selectedRestaurant != null,
    ) {
        scope.launch {
            if (sheetState.currentValue == SheetValue.Expanded) {
                sheetState.partialExpand()
            } else if (selectedRestaurant != null) {
                viewModel.selectRestaurant(null)
            }
        }
    }

    val activeLoaded = when (currentTab) {
        MapTab.PERSONALIZED -> uiState as? MapUiState.Loaded
        MapTab.TRENDING -> trendingUiState as? MapUiState.Loaded
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 지도 + 바텀시트 영역 (배너 제외한 나머지 공간)
        Box(modifier = Modifier.weight(1f)) {
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                sheetTonalElevation = 2.dp,
                sheetShadowElevation = 8.dp,
                sheetDragHandle = {},
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                sheetContent = {
                    RestaurantSheetContent(
                        currentTab = currentTab,
                        onTabSelect = { tab ->
                            viewModel.switchTab(tab)
                            if (tab == MapTab.TRENDING && trendingUiState is MapUiState.Idle) {
                                viewModel.loadTrendingRestaurants(context)
                            }
                        },
                        uiState = uiState,
                        filteredRestaurants = filteredRestaurants,
                        sortedRestaurants = sortedRestaurants,
                        totalCount = (uiState as? MapUiState.Loaded)?.restaurants?.size ?: 0,
                        excludeVisited = excludeVisited,
                        onExcludeVisitedToggle = { viewModel.toggleExcludeVisited(context) },
                        trendingUiState = trendingUiState,
                        selectedRestaurant = selectedRestaurant,
                        selectedCategory = selectedCategory,
                        onCategorySelect = viewModel::selectCategory,
                        onRestaurantClick = onRestaurantClick,
                        onRestaurantSelect = { rec ->
                            viewModel.selectRestaurant(rec)
                            if (rec != null) {
                                scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                            }
                        },
                        onRetry = { viewModel.loadNearbyRestaurants(context) },
                        onTrendingRetry = { viewModel.loadTrendingRestaurants(context) },
                        syncUiState = syncUiState,
                        onYouTubeSync = { viewModel.requestYouTubeSync(context) },
                        onSyncDismiss = { viewModel.resetSyncState() },
                        onSwitchToTrending = {
                            viewModel.switchTab(MapTab.TRENDING)
                            if (trendingUiState is MapUiState.Idle) {
                                viewModel.loadTrendingRestaurants(context)
                            }
                        },
                        onPhoneClick = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        },
                    )
                },
                containerColor = MaterialTheme.colorScheme.background,
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    KakaoMapView(
                        loaded = activeLoaded,
                        filteredRestaurants = filteredRestaurants,
                        selectedRestaurant = selectedRestaurant,
                        cameraEvent = cameraEvent,
                        onCameraEventConsumed = { viewModel.consumeCameraEvent() },
                        onMarkerClick = { rec ->
                            viewModel.selectRestaurant(rec)
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        },
                        onMapClick = { viewModel.selectRestaurant(null) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    FloatingSearchBar(
                        onSearchClick = onSearchClick,
                        onFavoritesClick = onFavoritesClick,
                        onProfileClick = onProfileClick,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    FloatingActionButton(
                        onClick = { viewModel.moveCameraToUserLocation() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = peekHeight + 16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "현재 위치로 이동")
                    }
                }
            }
        }

        // 2. 화면 최하단 고정 배너 광고 (바텀시트/FAB과 물리적으로 분리된 안전 영역)
        Surface(color = MaterialTheme.colorScheme.background) {
            AdmobBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 플로팅 검색창 (구글 맵 / 네이버 지도 스타일)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FloatingSearchBar(
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSearchClick)
                .height(52.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "검색",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "맛집 검색",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onFavoritesClick) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = "즐겨찾기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onProfileClick) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "내 프로필",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
