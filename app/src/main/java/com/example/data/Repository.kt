package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ScannerRepository(private val db: AppDatabase) {
    val savedIps: Flow<List<SavedIp>> = db.savedIpDao().getAllSavedIps()
    val configBases: Flow<List<ConfigBase>> = db.configBaseDao().getAllConfigBases()
    val scanHistory: Flow<List<ScanHistory>> = db.scanHistoryDao().getAllHistory()
    val scanRanges: Flow<List<ScanRange>> = db.scanRangeDao().getAllRanges()

    suspend fun insertSavedIp(ip: SavedIp) = db.savedIpDao().insertSavedIp(ip)
    suspend fun deleteSavedIp(ip: SavedIp) = db.savedIpDao().deleteSavedIp(ip)
    suspend fun clearSavedIps() = db.savedIpDao().clearSavedIps()

    suspend fun insertConfigBase(config: ConfigBase) = db.configBaseDao().insertConfigBase(config)
    suspend fun updateConfigBase(config: ConfigBase) = db.configBaseDao().updateConfigBase(config)
    suspend fun deleteConfigBase(config: ConfigBase) = db.configBaseDao().deleteConfigBase(config)

    suspend fun insertHistory(history: ScanHistory) = db.scanHistoryDao().insertHistory(history)
    suspend fun clearHistory() = db.scanHistoryDao().clearHistory()

    suspend fun insertRange(range: ScanRange) = db.scanRangeDao().insertRange(range)
    suspend fun deleteRange(range: ScanRange) = db.scanRangeDao().deleteRange(range)

    suspend fun initDefaultRangesIfNeeded() {
        val count = db.scanRangeDao().getCount()
        if (count == 0) {
            val defaults = listOf(
                "103.21.244.0/22",
                "103.22.200.0/22",
                "103.31.4.0/22",
                "104.16.0.0/13",
                "104.24.0.0/14",
                "108.162.192.0/18",
                "131.0.72.0/22",
                "141.101.64.0/18",
                "162.158.0.0/15",
                "172.64.0.0/13",
                "173.245.48.0/20",
                "188.114.96.0/20",
                "190.93.240.0/20",
                "197.234.240.0/22",
                "198.41.128.0/17"
            ).map { ScanRange(cidr = it, isEnabled = true, isCustom = false) }
            db.scanRangeDao().insertAll(defaults)
        }
    }
}
