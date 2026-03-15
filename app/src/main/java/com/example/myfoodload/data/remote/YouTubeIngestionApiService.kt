package com.example.myfoodload.data.remote

import com.example.myfoodload.shared.dto.IngestionResultDto
import com.example.myfoodload.shared.dto.YoutubeIngestRequest
import com.example.myfoodload.shared.response.ApiResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface YouTubeIngestionApiService {

    @POST("api/youtube/ingest")
    suspend fun ingestLikedVideos(
        @Body request: YoutubeIngestRequest,
    ): ApiResponse<IngestionResultDto>
}
