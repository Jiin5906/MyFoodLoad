package com.example.myfoodload.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val TAG = "InterstitialAdManager"

/** 디버그: Google 공식 테스트 전면 광고 ID, 릴리스: 프로덕션 ID */
private val INTERSTITIAL_AD_UNIT_ID =
    if (com.example.myfoodload.BuildConfig.DEBUG) "ca-app-pub-3940256099942544/1033173712"
    else "ca-app-pub-7947155260681633/9176770253"

/**
 * 전면 광고(Interstitial Ad) 로드/표시 매니저.
 *
 * - load(): 광고 미리 로드 (DetailScreen 진입 시 호출)
 * - showThenExecute(): 광고 표시 후 콜백 실행, 광고 없으면 바로 콜백
 * - 빈도 제한은 AdMob 콘솔 대시보드에서 서버 단으로 제어 (코드에서 미구현)
 */
object InterstitialAdManager {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun load(context: Context) {
        if (interstitialAd != null || isLoading) return
        isLoading = true

        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    Log.d(TAG, "전면 광고 로드 완료")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    Log.w(TAG, "전면 광고 로드 실패: ${error.message}")
                }
            },
        )
    }

    /**
     * 전면 광고를 표시하고, 광고 종료 후 [onComplete]를 실행한다.
     * 광고가 미로드/Activity 추출 실패 등 모든 예외 상황에서 [onComplete]를 즉시 실행하여
     * 사용자 흐름이 절대 끊기지 않도록 보장한다.
     */
    fun showThenExecute(context: Context, onComplete: () -> Unit) {
        val ad = interstitialAd
        val activity = context.findActivity()

        if (ad == null || activity == null) {
            onComplete()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                load(context) // 다음 노출을 위해 즉시 재장전
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "전면 광고 표시 실패: ${error.message}")
                interstitialAd = null
                load(context)
                onComplete()
            }
        }

        ad.show(activity)
    }
}

/**
 * Compose의 [LocalContext.current]는 순수 Activity가 아니라
 * ContextWrapper에 여러 번 감싸여 있을 수 있다.
 * 껍데기를 반복 해제하여 진짜 Activity를 추출한다.
 */
fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .firstOrNull { it is Activity } as? Activity
