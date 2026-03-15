package com.example.myfoodload.backend.controller

import com.example.myfoodload.backend.service.RestaurantService
import com.example.myfoodload.shared.dto.CategoryType
import com.example.myfoodload.shared.dto.RestaurantDto
import com.example.myfoodload.shared.response.ApiResponse
import com.example.myfoodload.shared.response.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/restaurants")
class RestaurantController(
    private val restaurantService: RestaurantService,
) {
    /** GET /api/restaurants — 전체 목록 또는 카테고리·검색어 필터 */
    @GetMapping
    fun findAll(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) q: String?,
    ): ResponseEntity<ApiResponse<List<RestaurantDto>>> {
        val result =
            when {
                !category.isNullOrBlank() -> {
                    val cat =
                        runCatching { CategoryType.valueOf(category.uppercase()) }
                            .getOrNull()
                            ?: return ResponseEntity.badRequest().body(
                                ApiResponse(
                                    success = false,
                                    error = ErrorResponse("INVALID_CATEGORY", "유효하지 않은 카테고리: $category"),
                                ),
                            )
                    restaurantService.findByCategory(cat)
                }
                !q.isNullOrBlank() -> restaurantService.search(q)
                else -> restaurantService.findAll()
            }
        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }

    /** GET /api/restaurants/{id} — 단건 조회 */
    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<RestaurantDto>> {
        val result =
            restaurantService.findById(id)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse(success = false, error = ErrorResponse("NOT_FOUND", "맛집을 찾을 수 없습니다: id=$id")),
                )
        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }

    /** POST /api/restaurants — 신규 등록 */
    @PostMapping
    fun create(
        @RequestBody dto: RestaurantDto,
    ): ResponseEntity<ApiResponse<RestaurantDto>> {
        val result = restaurantService.create(dto)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse(success = true, data = result))
    }

    /** PUT /api/restaurants/{id} — 전체 수정 */
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody dto: RestaurantDto,
    ): ResponseEntity<ApiResponse<RestaurantDto>> {
        val result =
            restaurantService.update(id, dto)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse(success = false, error = ErrorResponse("NOT_FOUND", "맛집을 찾을 수 없습니다: id=$id")),
                )
        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }

    /** DELETE /api/restaurants/{id} — 삭제 */
    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<Nothing>> {
        val deleted = restaurantService.delete(id)
        return if (deleted) {
            ResponseEntity.ok(ApiResponse(success = true))
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiResponse(success = false, error = ErrorResponse("NOT_FOUND", "맛집을 찾을 수 없습니다: id=$id")),
            )
        }
    }

    /**
     * GET /api/restaurants/nearby?lat=37.5&lon=127.0&radius=1000
     * Phase 7에서 완전 구현. 현재는 PostGIS 인프라 연결 검증용.
     */
    @GetMapping("/nearby")
    fun findNearby(
        @RequestParam lat: Double,
        @RequestParam lon: Double,
        @RequestParam(defaultValue = "1000.0") radius: Double,
    ): ResponseEntity<ApiResponse<List<RestaurantDto>>> {
        val result = restaurantService.findNearby(lat, lon, radius)
        return ResponseEntity.ok(ApiResponse(success = true, data = result))
    }
}
