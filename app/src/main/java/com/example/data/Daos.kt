package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedIpDao {
    @Query("SELECT * FROM saved_ips ORDER BY score DESC")
    fun getAllSavedIps(): Flow<List<SavedIp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedIp(ip: SavedIp)

    @Delete
    suspend fun deleteSavedIp(ip: SavedIp)

    @Query("DELETE FROM saved_ips")
    suspend fun clearSavedIps()
}

@Dao
interface ConfigBaseDao {
    @Query("SELECT * FROM config_bases ORDER BY timestamp DESC")
    fun getAllConfigBases(): Flow<List<ConfigBase>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigBase(config: ConfigBase)

    @Update
    suspend fun updateConfigBase(config: ConfigBase)

    @Delete
    suspend fun deleteConfigBase(config: ConfigBase)
}

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScanHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ScanHistory)

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()
}

@Dao
interface ScanRangeDao {
    @Query("SELECT * FROM scan_ranges")
    fun getAllRanges(): Flow<List<ScanRange>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRange(range: ScanRange)

    @Delete
    suspend fun deleteRange(range: ScanRange)

    @Query("SELECT COUNT(*) FROM scan_ranges")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(ranges: List<ScanRange>)
}
