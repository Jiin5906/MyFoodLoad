package com.example.myfoodload.backend.service

import com.example.myfoodload.backend.model.entity.UserVisit
import com.example.myfoodload.backend.repository.RestaurantRepository
import com.example.myfoodload.backend.repository.UserRepository
import com.example.myfoodload.backend.repository.UserVisitRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VisitService(
    private val visitRepository: UserVisitRepository,
    private val userRepository: UserRepository,
    private val restaurantRepository: RestaurantRepository,
) {
    /** 방문 완료 추가. 이미 방문 기록이 있으면 무시 (idempotent). */
    @Transactional
    fun markVisited(
        userId: Long,
        restaurantId: Long,
    ): Boolean {
        if (visitRepository.existsByUserIdAndRestaurantId(userId, restaurantId)) return false
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalArgumentException("사용자를 찾을 수 없습니다: $userId")
            }
        val restaurant =
            restaurantRepository.findById(restaurantId).orElseThrow {
                IllegalArgumentException("맛집을 찾을 수 없습니다: $restaurantId")
            }
        visitRepository.save(UserVisit(user = user, restaurant = restaurant))
        return true
    }

    /** 방문 기록 제거 (실수로 체크한 경우 취소). */
    @Transactional
    fun unmarkVisited(
        userId: Long,
        restaurantId: Long,
    ) {
        visitRepository.deleteByUserIdAndRestaurantId(userId, restaurantId)
    }

    @Transactional(readOnly = true)
    fun getVisitedIds(userId: Long): Set<Long> = visitRepository.findRestaurantIdsByUserId(userId)

    @Transactional(readOnly = true)
    fun isVisited(
        userId: Long,
        restaurantId: Long,
    ): Boolean = visitRepository.existsByUserIdAndRestaurantId(userId, restaurantId)
}
