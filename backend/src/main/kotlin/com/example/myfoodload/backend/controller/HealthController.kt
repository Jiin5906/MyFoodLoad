package com.example.myfoodload.backend.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.sql.DataSource

@RestController
@RequestMapping("/api")
class HealthController(
    private val dataSource: DataSource,
) {
    @GetMapping("/health")
    fun health(): Map<String, String> {
        val dbStatus = try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT 1").use { it.executeQuery() }
            }
            "UP"
        } catch (e: Exception) {
            "DOWN"
        }
        return mapOf(
            "status" to if (dbStatus == "UP") "UP" else "DEGRADED",
            "service" to "MyFoodLoad Backend",
            "database" to dbStatus,
        )
    }
}
