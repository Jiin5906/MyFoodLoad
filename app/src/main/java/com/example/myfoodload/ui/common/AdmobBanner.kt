package com.example.myfoodload.ui.common

import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myfoodload.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/** 디버그: Google 공식 테스트 배너 ID, 릴리스: 프로덕션 ID */
private val BANNER_AD_UNIT_ID =
    if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/6300978111"
    else "ca-app-pub-7947155260681633/8799298053"

private const val TAG = "AdmobBanner"

@Composable
fun AdmobBanner(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "배너 광고 로드 성공")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "배너 광고 로드 실패: code=${error.code}, msg=${error.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth(),
    )
}
