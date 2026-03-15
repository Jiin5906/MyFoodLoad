package com.example.myfoodload.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.myfoodload.R
import com.example.myfoodload.shared.dto.CategoryType

// ─────────────────────────────────────────────────────────────────────────────
// 카카오맵 스타일 동적 마커 Bitmap 생성 팩토리
//
// 마커 구조:
//   ┌────────────────────────┐
//   │ [아이콘]  [조회수 텍스트] │  ← 캡슐(pill) 배경
//   └──────────┬─────────────┘
//              ▼  ← 삼각형 포인터 (앵커: 중앙 하단)
//
// 식당 이름은 SDK LabelTextStyle로 별도 렌더링 (터치 바운딩 박스 정합성 보장)
// ─────────────────────────────────────────────────────────────────────────────

// 마커 치수 (px) — Kakao SDK에서 Bitmap을 직접 픽셀 단위로 처리
private const val PILL_HEIGHT = 52
private const val PILL_PADDING_H = 14   // 좌우 내부 패딩
private const val ICON_SIZE = 28        // 아이콘 크기
private const val ICON_TEXT_GAP = 6     // 아이콘-텍스트 간격
private const val TEXT_SIZE = 22f       // 텍스트 크기 (px)
private const val TRIANGLE_HEIGHT = 14  // 삼각형 높이
private const val CORNER_RADIUS = PILL_HEIGHT / 2f  // 완전 둥근 pill

// 마커 배경 색상
private const val MARKER_COLOR = 0xFF1A73E8.toInt()  // Google Blue
private const val MARKER_TEXT_COLOR = Color.WHITE

// 재사용 Paint 객체 (매 호출마다 인스턴스화 방지)
private val sharedTextPaint by lazy {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARKER_TEXT_COLOR
        textSize = TEXT_SIZE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
}
private val sharedBgPaint by lazy {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARKER_COLOR
        style = Paint.Style.FILL
    }
}

/**
 * 조회수 → 마커 표시 문자열 변환
 *
 * 290,000 → "29만"
 *   1,897 → "1.8천"
 *     500 → "500"
 */
fun formatMarkerViewCount(viewCount: Long?): String {
    if (viewCount == null || viewCount <= 0) return ""
    return when {
        viewCount >= 10_000 -> "${viewCount / 10_000}만"
        viewCount >= 1_000 -> {
            val tenths = (viewCount / 100) % 10
            if (tenths == 0L) "${viewCount / 1_000}천"
            else "${viewCount / 1_000}.${tenths}천"
        }
        else -> viewCount.toString()
    }
}

/** CategoryType → 마커에 사용할 아이콘 리소스 ID */
@DrawableRes
fun CategoryType.toMarkerIconResId(): Int = when (this) {
    CategoryType.CAFE -> R.drawable.ic_marker_cafe
    else -> R.drawable.ic_marker_food
}

/**
 * 카카오맵 스타일 캡슐 마커 Bitmap 생성.
 *
 * 식당 이름 텍스트는 포함하지 않음 — SDK [LabelTextStyle]로 별도 렌더링.
 * 앵커 포인트: 삼각형 끝 = 비트맵 하단 중앙 → [setAnchorPoint(0.5f, 1.0f)]
 *
 * @param context  Context (drawable 로드용)
 * @param category 식당 카테고리 (아이콘 선택)
 * @param viewCount YouTube 조회수 (null 또는 0이면 텍스트 미표시)
 * @return 생성된 마커 Bitmap
 */
fun createCustomMarkerBitmap(
    context: Context,
    category: CategoryType,
    viewCount: Long?,
): Bitmap {
    // ── 1. 텍스트 측정 ───────────────────────────────────────────────────────
    val labelText = formatMarkerViewCount(viewCount)
    val textWidth = if (labelText.isEmpty()) 0f else sharedTextPaint.measureText(labelText)

    // ── 2. Bitmap 크기 계산 ──────────────────────────────────────────────────
    val pillWidth = (PILL_PADDING_H * 2 + ICON_SIZE +
            (if (labelText.isNotEmpty()) ICON_TEXT_GAP + textWidth.toInt() else 0)).toInt()
    val totalHeight = PILL_HEIGHT + TRIANGLE_HEIGHT

    val bitmap = Bitmap.createBitmap(pillWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ── 3. 캡슐 배경 그리기 ──────────────────────────────────────────────────
    val pillRect = RectF(0f, 0f, pillWidth.toFloat(), PILL_HEIGHT.toFloat())
    canvas.drawRoundRect(pillRect, CORNER_RADIUS, CORNER_RADIUS, sharedBgPaint)

    // ── 4. 삼각형 포인터 그리기 ──────────────────────────────────────────────
    val trianglePath = Path().apply {
        val midX = pillWidth / 2f
        moveTo(midX - 10f, PILL_HEIGHT.toFloat())
        lineTo(midX + 10f, PILL_HEIGHT.toFloat())
        lineTo(midX, totalHeight.toFloat())
        close()
    }
    canvas.drawPath(trianglePath, sharedBgPaint)

    // ── 5. 카테고리 아이콘 그리기 (흰색 tint) ────────────────────────────────
    val iconResId = category.toMarkerIconResId()
    val iconDrawable = ContextCompat.getDrawable(context, iconResId)
    if (iconDrawable != null) {
        val tintedDrawable = DrawableCompat.wrap(iconDrawable.mutate())
        DrawableCompat.setTint(tintedDrawable, Color.WHITE)

        val iconLeft = PILL_PADDING_H
        val iconTop = (PILL_HEIGHT - ICON_SIZE) / 2
        tintedDrawable.setBounds(iconLeft, iconTop, iconLeft + ICON_SIZE, iconTop + ICON_SIZE)
        tintedDrawable.draw(canvas)
    }

    // ── 6. 조회수 텍스트 그리기 ──────────────────────────────────────────────
    if (labelText.isNotEmpty()) {
        val textX = (PILL_PADDING_H + ICON_SIZE + ICON_TEXT_GAP).toFloat()
        val fontMetrics = sharedTextPaint.fontMetrics
        val textY = PILL_HEIGHT / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(labelText, textX, textY, sharedTextPaint)
    }

    return bitmap
}
