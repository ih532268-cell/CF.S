package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ConfigBase
import com.example.data.SavedIp
import com.example.data.ScanHistory
import com.example.data.ScanRange
import com.example.data.ScannerRepository
import com.example.network.CidrUtils
import com.example.network.ConfigRebuilder
import com.example.network.ScanResult
import com.example.network.ScannerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TerminalLog(
    val timestamp: String,
    val faText: String,
    val enText: String
)

data class GeneratedConfig(
    val name: String,
    val sourceBaseName: String,
    val ip: String,
    val port: Int,
    val configUri: String
)

sealed class ScanState {
    object Idle : ScanState()
    data class Stage1PortScan(val checked: Int, val total: Int, val foundActive: Int) : ScanState()
    data class Stage2Benchmark(val currentIp: String, val indexed: Int, val total: Int) : ScanState()
    object Finished : ScanState()
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ScannerRepository(db)

    private val _generatedConfigs = MutableStateFlow<List<GeneratedConfig>>(emptyList())
    val generatedConfigs: StateFlow<List<GeneratedConfig>> = _generatedConfigs.asStateFlow()

    private val _scanTimestamp = MutableStateFlow("")
    val scanTimestamp: StateFlow<String> = _scanTimestamp.asStateFlow()

    // DB Flows
    val savedIps = repository.savedIps.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val configBases = repository.configBases.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val scanHistory = repository.scanHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val scanRanges = repository.scanRanges.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI & Active Scanner States (Delegated directly to Thread-safe ScannerManager)
    val scanState: StateFlow<ScanState> = ScannerManager.scanState
    val isScanning: StateFlow<Boolean> = ScannerManager.isScanning
    val terminalLogs: StateFlow<List<TerminalLog>> = ScannerManager.terminalLogs
    val liveResults: StateFlow<List<ScanResult>> = ScannerManager.liveResults
    val isPaused: StateFlow<Boolean> = ScannerManager.isPaused

    // Settings States
    val portsConfig = MutableStateFlow("443,2053,2083,2087,2096,8443")
    val timeoutMs = MutableStateFlow(3500)
    val benchDurationSeconds = MutableStateFlow(4.0)
    val jitterSamples = MutableStateFlow(10)
    val maxLossRatio = MutableStateFlow(0.4)
    val ignoreCert = MutableStateFlow(true)
    val samplePerCidr = MutableStateFlow(20) // Optimized default sampling for speed & extreme stability
    val concurrencyLimit = MutableStateFlow(32) // Safer execution limits to prevent socket leaks and CPU freezes
    val isDarkTheme = MutableStateFlow(true)
    val isEnglish = MutableStateFlow(false)

    fun toggleTheme() {
        isDarkTheme.value = !isDarkTheme.value
    }

    fun toggleLanguage() {
        isEnglish.value = !isEnglish.value
    }

    init {
        ScannerManager.initialize(application)
        
        viewModelScope.launch {
            repository.initDefaultRangesIfNeeded()
        }
        
        // Listen to ScannerManager's scanState. When it reaches Finished, trigger a rebuild or database sync!
        viewModelScope.launch {
            ScannerManager.scanState.collect { state ->
                if (state is ScanState.Finished) {
                    rebuildWithSavedIps()
                }
            }
        }
    }

    fun addLog(fa: String, en: String) {
        ScannerManager.addLog(fa, en)
    }

    fun addLog(msg: String) {
        ScannerManager.addLog(msg, msg)
    }

    fun clearLogs() {
        ScannerManager.clearLogs()
    }

    fun startScanning() {
        viewModelScope.launch {
            val ranges = repository.scanRanges.first()
            ScannerManager.startScan(
                cidrRanges = ranges,
                ports = portsConfig.value,
                sampling = samplePerCidr.value,
                timeoutMs = timeoutMs.value,
                benchSec = benchDurationSeconds.value,
                jitter = jitterSamples.value,
                maxLoss = maxLossRatio.value,
                ignoreCert = ignoreCert.value,
                concurrency = concurrencyLimit.value,
                isEng = isEnglish.value
            )
        }
    }

    fun pauseScanning() {
        ScannerManager.pauseScan()
    }

    fun resumeScanning() {
        ScannerManager.resumeScan()
    }

    fun stopScanning() {
        ScannerManager.stopScan()
    }

    // IP Management
    fun saveIp(res: ScanResult) {
        viewModelScope.launch {
            repository.insertSavedIp(
                SavedIp(
                    ip = res.ip,
                    port = res.port,
                    colo = res.colo,
                    country = res.country,
                    ping = res.medianLatency,
                    dlSpeed = res.downloadSpeed,
                    ulSpeed = res.uploadSpeed,
                    jitter = res.jitter,
                    score = res.score
                )
            )
            addLog("💾 آی‌پی تمیز ${res.ip} در لیست نشان‌شده ذخیره شد.", "💾 Clean IP ${res.ip} saved to favorites database.")
            rebuildWithSavedIps()
        }
    }

    fun saveIp(saved: SavedIp) {
        viewModelScope.launch {
            repository.insertSavedIp(saved)
            addLog("💾 آی‌پی دستی ${saved.ip} در لیست نشان‌شده ذخیره شد.", "💾 Manual IP ${saved.ip} saved to favorites database.")
            rebuildWithSavedIps()
        }
    }

    fun deleteSavedIp(saved: SavedIp) {
        viewModelScope.launch {
            repository.deleteSavedIp(saved)
            rebuildWithSavedIps()
        }
    }

    fun clearSavedIps() {
        viewModelScope.launch {
            repository.clearSavedIps()
            _generatedConfigs.value = emptyList()
        }
    }

    // Configuration Management
    fun addConfigBase(name: String, rawUri: String) {
        viewModelScope.launch {
            repository.insertConfigBase(ConfigBase(name = name, rawUri = rawUri))
            rebuildWithSavedIps()
        }
    }

    fun deleteConfigBase(config: ConfigBase) {
        viewModelScope.launch {
            repository.deleteConfigBase(config)
            rebuildWithSavedIps()
        }
    }

    fun rebuildWithSavedIps() {
        viewModelScope.launch {
            val bases = repository.configBases.first()
            val saved = repository.savedIps.first()
            if (bases.isEmpty() || saved.isEmpty()) {
                _generatedConfigs.value = emptyList()
                return@launch
            }
            
            val list = mutableListOf<GeneratedConfig>()
            bases.forEach { base ->
                saved.forEach { ipState ->
                    val rebuilt = ConfigRebuilder.modifyUriWithCleanIp(base.rawUri, ipState.ip, ipState.port)
                    if (rebuilt != null) {
                        list.add(GeneratedConfig(
                            name = "${base.name} - ${ipState.colo}",
                            sourceBaseName = base.name,
                            ip = ipState.ip,
                            port = ipState.port,
                            configUri = rebuilt
                        ))
                    }
                }
            }
            _generatedConfigs.value = list

            // Update database configs lastCleanUri with the best IP if available
            val bestSaved = saved.firstOrNull()
            if (bestSaved != null) {
                bases.forEach { base ->
                    val rebuilt = ConfigRebuilder.modifyUriWithCleanIp(base.rawUri, bestSaved.ip, bestSaved.port)
                    if (rebuilt != null) {
                        repository.updateConfigBase(base.copy(lastCleanUri = rebuilt))
                    }
                }
            }
        }
    }

    fun rebuildConfigs(targetIp: String, targetPort: Int) {
        viewModelScope.launch {
            val bases = configBases.value
            if (bases.isEmpty()) {
                addLog("⚠️ هیچ کانفیگ پایه‌ای یافت نشد! ابتدا کانفیگ پایه اضافه کنید.", "⚠️ No Config bases found to rebuild! Add base links first.")
                return@launch
            }
            addLog("🔄 بازسازی کل کانفیگ‌های پایه با آی‌پی تمیز: $targetIp:$targetPort...", "🔄 Rebuilding all base configs with clean IP: $targetIp:$targetPort...")
            bases.forEach { base ->
                val rebuilt = ConfigRebuilder.modifyUriWithCleanIp(base.rawUri, targetIp, targetPort)
                if (rebuilt != null) {
                    repository.updateConfigBase(base.copy(lastCleanUri = rebuilt))
                }
            }
            addLog("✅ بازسازی تکمیل شد. لینک‌های جدید را در کارت‌های تنظیمات ببینید!", "✅ Rebuilt complete. View links on the Configuration screen!")
            rebuildWithSavedIps()
        }
    }

    // Settings IP Ranges
    fun addCustomRange(cidr: String) {
        viewModelScope.launch {
            if (cidr.trim().isNotEmpty()) {
                repository.insertRange(ScanRange(cidr = cidr.trim(), isEnabled = true, isCustom = true))
            }
        }
    }

    fun deleteRange(range: ScanRange) {
        viewModelScope.launch {
            repository.deleteRange(range)
        }
    }

    fun toggleRangeSelection(range: ScanRange, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.insertRange(range.copy(isEnabled = isEnabled))
        }
    }
}
