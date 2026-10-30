package com.workcheckin.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts ORDER BY inMinutes")
    fun observeAll(): Flow<List<Shift>>

    @Query("SELECT * FROM shifts")
    suspend fun getAll(): List<Shift>

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun get(id: Long): Shift?

    @Insert
    suspend fun insert(shift: Shift): Long

    @Update
    suspend fun update(shift: Shift)

    @Delete
    suspend fun delete(shift: Shift)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM check_records WHERE epochDay >= :fromDay ORDER BY epochDay DESC, checkedAtMillis DESC")
    fun observeSince(fromDay: Long): Flow<List<CheckRecord>>

    @Query("SELECT * FROM check_records WHERE epochDay = :epochDay")
    suspend fun forDay(epochDay: Long): List<CheckRecord>

    @Query("SELECT * FROM check_records WHERE shiftId = :shiftId AND epochDay = :epochDay AND type = :type LIMIT 1")
    suspend fun find(shiftId: Long, epochDay: Long, type: CheckType): CheckRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CheckRecord): Long

    @Query("DELETE FROM check_records WHERE shiftId = :shiftId AND epochDay = :epochDay AND type = :type")
    suspend fun delete(shiftId: Long, epochDay: Long, type: CheckType)
}
