package com.example.myfoodload.shared.response

/**
 * 모든 API 응답의 공통 래퍼
 *
 * 성공: ApiResponse(success=true, data=T)
 * 실패: ApiResponse(success=false, error=ErrorResponse)
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorResponse? = null,
)

data class PagedResponse<T>(
    val items: List<T>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean,
)

data class ErrorResponse(
    val code: String,
    val message: String,
    val detail: String? = null,
)
