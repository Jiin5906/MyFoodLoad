package com.example.myfoodload.data.remote

import retrofit2.http.DELETE

interface UserApiService {
    @DELETE("api/users/me")
    suspend fun deleteAccount()
}
