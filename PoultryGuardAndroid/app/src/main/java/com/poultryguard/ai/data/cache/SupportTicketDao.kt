package com.poultryguard.ai.data.cache

import androidx.room.*
import com.poultryguard.ai.data.model.SupportTicket
import kotlinx.coroutines.flow.Flow

@Dao
interface SupportTicketDao {
    @Query("SELECT * FROM support_tickets")
    fun getAllTicketsFlow(): Flow<List<SupportTicket>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tickets: List<SupportTicket>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ticket: SupportTicket)

    @Query("UPDATE support_tickets SET status = :status WHERE ticketId = :ticketId")
    suspend fun updateStatus(ticketId: String, status: String)

    @Query("SELECT COUNT(*) FROM support_tickets")
    suspend fun getCount(): Int
}
