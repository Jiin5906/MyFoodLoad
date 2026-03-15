package com.example.myfoodload.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * YouTube 분석 파이프라인 시작/진행 상태를 보여주는 배너.
 *
 * - Idle: "YouTube 연동" 버튼 + 설명 텍스트
 * - Syncing: 단계별 진행 스피너
 * - Complete: 성공 메시지 + 닫기
 * - NoResults: "맛집 쇼츠 없음" 안내 + 재시도
 * - Error: 오류 메시지 + 재시도
 */
@Composable
internal fun YouTubeSyncBanner(
    syncUiState: SyncUiState,
    onSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (syncUiState) {
            is SyncUiState.Idle -> {
                Icon(
                    imageVector = Icons.Outlined.CloudSync,
                    contentDescription = "YouTube 연동",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = "내 취향 맛집 분석",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "좋아요한 유튜브 쇼츠를 AI로 분석해\n맞춤 맛집을 찾아드립니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onSync,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Outlined.CloudSync, contentDescription = "YouTube 연동", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("YouTube 연동하기", fontWeight = FontWeight.Bold)
                }
            }

            is SyncUiState.AwaitingYoutubeConsent, is SyncUiState.Syncing -> {
                val stepText = if (syncUiState is SyncUiState.Syncing) syncUiState.step else "YouTube 권한 요청 중..."
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
                Text(
                    text = stepText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "완료까지 수십 초~수 분이 걸릴 수 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }

            is SyncUiState.Complete -> {
                Text(
                    text = "✅ 분석 완료!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val addedText = if (syncUiState.restaurantsAdded > 0) {
                    "${syncUiState.restaurantsAdded}개 맛집이 새로 추가되었어요"
                } else {
                    "기존 맛집 목록이 업데이트되었어요"
                }
                Text(
                    text = addedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                syncUiState.warningMessage?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }
                OutlinedButton(onClick = onDismiss) { Text("닫기") }
            }

            is SyncUiState.PartialFailure -> {
                Text(
                    text = "⚠️ AI 분석 한도 초과",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "${syncUiState.processed}/${syncUiState.total}개 영상만 처리되었어요.\n" +
                        "잠시 후 다시 시도하면 나머지도 분석됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSync) { Text("다시 시도") }
                    OutlinedButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            is SyncUiState.NoResults -> {
                Text(
                    text = "😕 맛집 쇼츠를 찾지 못했어요",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "좋아요한 유튜브 영상 중 맛집 관련\n쇼츠가 없거나 분석이 어려웠어요.\n🔥 핫한 맛집 탭을 이용해보세요!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSync) { Text("다시 시도") }
                    OutlinedButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            is SyncUiState.Error -> {
                Text(
                    text = "오류가 발생했어요",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = syncUiState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSync,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("다시 시도") }
                    OutlinedButton(onClick = onDismiss) { Text("닫기") }
                }
            }
        }
    }
}
