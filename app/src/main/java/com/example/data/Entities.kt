package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "saved_ips")
data class SavedIp(
    @PrimaryKey val ip: String,
    val port: Int,
    val colo: String,
    val country: String,
    val ping: Double,
    val dlSpeed: Double,
    val ulSpeed: Double,
    val jitter: Double,
    val score: Double,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "config_bases")
data class ConfigBase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rawUri: String,
    val lastCleanUri: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "scan_history")
data class ScanHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalScanned: Int,
    val activeRanges: String,
    val bestIp: String,
    val bestPing: Double,
    val bestSpeed: Double,
    val bestScore: Double
) : Serializable

@Entity(tableName = "scan_ranges")
data class ScanRange(
    @PrimaryKey val cidr: String,
    val isEnabled: Boolean = true,
    val isCustom: Boolean = false
) : Serializable
