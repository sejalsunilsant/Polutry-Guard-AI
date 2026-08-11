package com.poultryguard.ai.data.api

import com.google.gson.annotations.SerializedName
import com.poultryguard.ai.data.model.Batch
import com.poultryguard.ai.data.model.BatchStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class StartBatchRequest(
    @SerializedName("batch_id")
    val batchId: String,
    
    @SerializedName("start_date")
    val startDate: String,
    
    @SerializedName("initial_count")
    val initialCount: Int,
    
    @SerializedName("breed")
    val breed: String
)

data class CloseBatchRequest(
    @SerializedName("status")
    val status: BatchStatus,
    
    @SerializedName("end_date")
    val endDate: String,
    
    @SerializedName("current_count")
    val currentCount: Int
)

data class RecordMortalityRequest(
    @SerializedName("death_count")
    val deathCount: Int
)

data class BatchApiResponse(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("data")
    val data: Batch?
)

data class BatchListApiResponse(
    @SerializedName("status")
    val status: String,
    
    @SerializedName("data")
    val data: List<Batch>?
)

interface BatchApi {
    @POST("api/v1/farms/{farm_id}/batches")
    suspend fun startBatch(
        @Path("farm_id") farmId: String,
        @Body body: StartBatchRequest
    ): BatchApiResponse

    @GET("api/v1/farms/{farm_id}/batches/active")
    suspend fun getActiveBatch(
        @Path("farm_id") farmId: String
    ): BatchApiResponse

    @PUT("api/v1/batches/{batch_id}/status")
    suspend fun closeBatch(
        @Path("batch_id") batchId: String,
        @Body body: CloseBatchRequest
    ): BatchApiResponse

    @GET("api/v1/farms/{farm_id}/batches")
    suspend fun getAllBatches(
        @Path("farm_id") farmId: String
    ): BatchListApiResponse

    @POST("api/v1/batches/{batch_id}/mortality")
    suspend fun recordMortality(
        @Path("batch_id") batchId: String,
        @Body body: RecordMortalityRequest
    ): BatchApiResponse
}
