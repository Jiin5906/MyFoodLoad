package com.example.myfoodload.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myfoodload.R
import com.example.myfoodload.shared.dto.RecommendedRestaurantDto
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle

private fun Context.drawableToBitmap(@DrawableRes resId: Int, sizePx: Int): Bitmap {
    val drawable = ContextCompat.getDrawable(this, resId)
        ?: return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bitmap
}

private const val DEFAULT_ZOOM_LEVEL = 15
private const val USER_LOCATION_Z_ORDER = 200
private const val RESTAURANT_LAYER_Z_ORDER = 100
private const val RESTAURANT_LAYER_ID = "restaurant_layer"
private const val DOT_SIZE_PX = 48

@Composable
fun KakaoMapView(
    loaded: MapUiState.Loaded?,
    filteredRestaurants: List<RecommendedRestaurantDto>,
    selectedRestaurant: RecommendedRestaurantDto? = null,
    cameraEvent: CameraEvent? = null,
    onCameraEventConsumed: () -> Unit = {},
    onMarkerClick: (RecommendedRestaurantDto) -> Unit,
    onMapClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    val kakaoMapState = remember { mutableStateOf<KakaoMap?>(null) }
    val mapErrorState = remember { mutableStateOf<String?>(null) }
    val isCameraInitialized = remember { mutableStateOf(false) }

    val dotBitmap = remember { context.drawableToBitmap(R.drawable.ic_location_dot, DOT_SIZE_PX) }

    val markerBitmapCache = remember {
        object : android.util.LruCache<Long, Bitmap>(50) {
            override fun entryRemoved(evicted: Boolean, key: Long, oldValue: Bitmap, newValue: Bitmap?) {
                oldValue.recycle()
            }
        }
    }

    val currentFilteredRestaurants by rememberUpdatedState(filteredRestaurants)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)

    val userDotLabel = remember { mutableStateOf<Label?>(null) }

    val markerKeys = remember(filteredRestaurants) {
        filteredRestaurants.joinToString(",") { "${it.restaurant.id}_${it.restaurant.viewCount}" }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) = mapView.resume()
            override fun onPause(owner: LifecycleOwner) = mapView.pause()
        }

        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    kakaoMapState.value = null
                    isCameraInitialized.value = false
                    userDotLabel.value = null
                }

                override fun onMapError(e: Exception) {
                    Log.e("KakaoMap", "지도 오류: ${e.message}", e)
                    mapErrorState.value = "${e.javaClass.simpleName}: ${e.message}"
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(kakaoMap: KakaoMap) {
                    Log.d("KakaoMap", "지도 준비 완료")
                    kakaoMap.setOnLabelClickListener { _, _, label ->
                        val restaurantId = label.labelId.toLongOrNull()
                        val clickedRec = currentFilteredRestaurants.find { it.restaurant.id == restaurantId }
                        if (clickedRec != null) {
                            currentOnMarkerClick(clickedRec)
                        }
                        true
                    }
                    kakaoMap.setOnMapClickListener { _, latLng, _, _ ->
                        val clickLat = latLng.latitude
                        val clickLon = latLng.longitude
                        val zoomLevel = kakaoMap.zoomLevel
                        val threshold = when {
                            zoomLevel >= 17 -> 0.0005
                            zoomLevel >= 15 -> 0.001
                            zoomLevel >= 13 -> 0.003
                            zoomLevel >= 11 -> 0.008
                            else -> 0.02
                        }
                        val closest = currentFilteredRestaurants
                            .mapNotNull { rec ->
                                val rLat = rec.restaurant.latitude ?: return@mapNotNull null
                                val rLon = rec.restaurant.longitude ?: return@mapNotNull null
                                val dist = (rLat - clickLat) * (rLat - clickLat) + (rLon - clickLon) * (rLon - clickLon)
                                if (Math.abs(rLat - clickLat) < threshold && Math.abs(rLon - clickLon) < threshold) {
                                    rec to dist
                                } else null
                            }
                            .minByOrNull { it.second }
                            ?.first
                        if (closest != null) {
                            currentOnMarkerClick(closest)
                        } else {
                            currentOnMapClick()
                        }
                    }
                    kakaoMapState.value = kakaoMap
                }
            },
        )

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.resume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        mapErrorState.value?.let { errorMsg ->
            Text(
                text = "지도 오류: $errorMsg",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // 카메라 초기화 + 파란 점 생성/업데이트
    LaunchedEffect(kakaoMapState.value, loaded) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        val data = loaded ?: return@LaunchedEffect
        val userLatLng = LatLng.from(data.userLatitude, data.userLongitude)

        if (!isCameraInitialized.value) {
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(userLatLng, DEFAULT_ZOOM_LEVEL),
            )
            isCameraInitialized.value = true
        }

        val dotStyle = LabelStyles.from(LabelStyle.from(dotBitmap))
        val existingDot = userDotLabel.value
        if (existingDot != null) {
            existingDot.moveTo(userLatLng)
        } else {
            val userLayer = map.labelManager?.getLayer("user_location")
                ?: map.labelManager?.addLayer(
                    LabelLayerOptions.from("user_location").setZOrder(USER_LOCATION_Z_ORDER),
                )
            val newDot = userLayer?.addLabel(
                LabelOptions.from("user_dot", userLatLng).setStyles(dotStyle),
            )
            userDotLabel.value = newDot
        }
    }

    // 마커 업데이트
    LaunchedEffect(kakaoMapState.value, markerKeys) {
        val map = kakaoMapState.value ?: return@LaunchedEffect

        val existingLayer = map.labelManager?.getLayer(RESTAURANT_LAYER_ID)
        if (existingLayer != null) {
            map.labelManager?.remove(existingLayer)
        }

        val layer = map.labelManager?.addLayer(
            LabelLayerOptions.from(RESTAURANT_LAYER_ID)
                .setZOrder(RESTAURANT_LAYER_Z_ORDER)
                .setClickable(true),
        ) ?: return@LaunchedEffect

        layer.removeAll()

        currentFilteredRestaurants.forEach { rec ->
            val lat = rec.restaurant.latitude ?: return@forEach
            val lon = rec.restaurant.longitude ?: return@forEach

            val bitmap = markerBitmapCache.get(rec.restaurant.id)
                ?: createCustomMarkerBitmap(context, rec.restaurant.category, rec.restaurant.viewCount).also {
                    markerBitmapCache.put(rec.restaurant.id, it)
                }
            val textStyle = LabelTextStyle.from(23, android.graphics.Color.BLACK, 4, android.graphics.Color.WHITE)
            val style = LabelStyles.from(
                LabelStyle.from(bitmap)
                    .setAnchorPoint(0.5f, 1.0f)
                    .setTextStyles(textStyle),
            )

            layer.addLabel(
                LabelOptions.from(rec.restaurant.id.toString(), LatLng.from(lat, lon))
                    .setStyles(style)
                    .setTexts(LabelTextBuilder().setTexts(rec.restaurant.name))
                    .setClickable(true),
            )
        }
        Log.d("KakaoMap", "마커 갱신: ${currentFilteredRestaurants.size}개")
    }

    // 선택된 맛집 변경 시 카메라 이동
    LaunchedEffect(kakaoMapState.value, selectedRestaurant) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        val rec = selectedRestaurant ?: return@LaunchedEffect
        val lat = rec.restaurant.latitude ?: return@LaunchedEffect
        val lon = rec.restaurant.longitude ?: return@LaunchedEffect
        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lon)),
            CameraAnimation.from(300),
        )
    }

    // 카메라 이벤트 처리 (FAB → 현재 위치 이동)
    LaunchedEffect(kakaoMapState.value, cameraEvent) {
        val map = kakaoMapState.value ?: return@LaunchedEffect
        val event = cameraEvent ?: return@LaunchedEffect

        when (event) {
            is CameraEvent.MoveToUser -> {
                map.moveCamera(
                    CameraUpdateFactory.newCenterPosition(
                        LatLng.from(event.lat, event.lng), DEFAULT_ZOOM_LEVEL,
                    ),
                    CameraAnimation.from(500),
                )
            }
            is CameraEvent.ZoomToUser -> {
                map.moveCamera(
                    CameraUpdateFactory.newCenterPosition(
                        LatLng.from(event.lat, event.lng), 17,
                    ),
                    CameraAnimation.from(800),
                )
            }
        }
        onCameraEventConsumed()
    }
}
