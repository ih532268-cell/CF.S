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

sealed class ScanState {
    object Idle : ScanState()
    data class Stage1PortScan(val checked: Int, val total: Int, val foundActive: Int) : ScanState()
    data class Stage2Benchmark(val currentIp: String, val indexed: Int, val total: Int) : ScanState()
    object Finished : ScanState()
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ScannerRepository(db)

    // DB Flows
    val savedIps = repository.savedIps.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val configBases = repository.configBases.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val scanHistory = repository.scanHistory.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val scanRanges = repository.scanRanges.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI & Active Scanner States
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalLog>> = _terminalLogs.asStateFlow()

    private val _liveResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val liveResults: StateFlow<List<ScanResult>> = _liveResults.asStateFlow()

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

    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            repository.initDefaultRangesIfNeeded()
        }
    }

    private val logLock = Any()

    fun addLog(fa: String, en: String) {
        synchronized(logLock) {
            val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val currentLogs = _terminalLogs.value
            val newLog = TerminalLog(timeStamp, fa, en)
            val updatedLogs = if (currentLogs.size >= 500) {
                currentLogs.drop(currentLogs.size - 499) + newLog
            } else {
                currentLogs + newLog
            }
            _terminalLogs.value = updatedLogs
        }
    }

    fun addLog(msg: String) {
        addLog(msg, msg)
    }

    fun clearLogs() {
        synchronized(logLock) {
            _terminalLogs.value = emptyList()
        }
    }

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        _liveResults.value = emptyList()
        clearLogs()

        addLog("🚀 شروع اسکنر کلادفلر...", "🚀 Starting Cloudflare Scanner...")
        addLog("🛠️ پیکربندی بافرهای شبکه و محیط SSL...", "🛠️ Configuring Network Buffers & SSL Context...")

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                // 1. Gather all active ranges
                val ranges = repository.scanRanges.first().filter { it.isEnabled }
                if (ranges.isEmpty()) {
                    addLog("❌ خطا: هیچ رنج آی‌پی در تنظیمات فعال نشده است!", "❌ Error: No IP Ranges are selected/enabled in Settings!")
                    _isScanning.value = false
                    _scanState.value = ScanState.Idle
                    return@launch
                }

                addLog("📂 محاسبه‌ی محدوده کل پورت‌های هدف...", "📂 Calculating total target search space...")

                val portsList = portsConfig.value.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .ifEmpty { listOf(443) }

                val sampling = samplePerCidr.value
                var totalTargets = 0
                ranges.forEach { range ->
                    totalTargets += CidrUtils.countIps(range.cidr, sampling) * portsList.size
                }

                if (totalTargets == 0) {
                    addLog("❌ خطا: هیچ آدرس کاندیدی تولید نشد. رنج‌های CIDR را بررسی کنید!", "❌ Error: Could not generate any candidate IPs. Check CIDRs!")
                    _isScanning.value = false
                    _scanState.value = ScanState.Idle
                    return@launch
                }

                addLog("📋 کل کانال‌های سوکت هدف برای اسکن: $totalTargets", "📋 Total Target Sockets to scan: $totalTargets")
                addLog("🔎 مرحله ۱: بررسی بست آدرس‌های لبه فعال روی پورت‌های $portsList", "🔎 Step 1: Active Port Checking on ports $portsList")

                _scanState.value = ScanState.Stage1PortScan(0, totalTargets, 0)

                // Run concurrent Stage 1 checking using highly optimized backpressured Channel pool
                val activeList = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
                var checkedCount = 0

                // Backpressured queue to prevent OutOfMemory on huge subnets
                val channel = Channel<Pair<String, Int>>(capacity = 1000)

                // Populate channel dynamically range-by-range
                val producerJob = launch(Dispatchers.Default) {
                    try {
                        ranges.forEach { range ->
                            val list = CidrUtils.generateIpsFromCidr(range.cidr, sampling)
                            list.forEach { ip ->
                                portsList.forEach { port ->
                                    channel.send(Pair(ip, port))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        channel.close()
                    }
                }

                val workerCount = minOf(concurrencyLimit.value, totalTargets).coerceAtLeast(1)
                val stage1Jobs = List(workerCount) {
                    async {
                        for (target in channel) {
                            try {
                                val active = ScannerEngine.checkPort(target.first, target.second, timeoutMs.value)
                                var currentChecked = 0
                                var currentActiveSize = 0
                                synchronized(activeList) {
                                    checkedCount++
                                    currentChecked = checkedCount
                                    if (active) {
                                        activeList.add(target)
                                    }
                                    currentActiveSize = activeList.size
                                }
                                if (currentChecked % 10 == 0 || currentChecked == totalTargets || active) {
                                    _scanState.value = ScanState.Stage1PortScan(currentChecked, totalTargets, currentActiveSize)
                                }
                                if (active) {
                                    addLog("🟢 آی‌پی پاسخگو در ${target.first}:${target.second}", "🟢 Edge alive on ${target.first}:${target.second}")
                                }
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                    }
                }

                stage1Jobs.awaitAll()
                producerJob.join()

                val aliveResultList = synchronized(activeList) { activeList.toList() }

                addLog("✅ مرحله ۱ پایان یافت. ${aliveResultList.size} گره‌ی فعال پاسخگو پیدا شد!", "✅ Stage 1 Complete. Found ${aliveResultList.size} responsive Edge Nodes!")

                if (aliveResultList.isEmpty()) {
                    addLog("⚠️ هیچ سرور فعالی پاسخ نداد. پورت یا آی‌پی‌های تستی دیگر را امتحان کنید.", "⚠️ No active CDNs verified. Try higher timeouts or other ranges.")
                    _isScanning.value = false
                    _scanState.value = ScanState.Idle
                    return@launch
                }

                // 2. Stage 2 Benchmarking (to avoid bandwidth skew, we run them sequentially/tight concurrency)
                addLog("⚡ مرحله ۲: ارزیابی و امتیازدهی نهایی (پینگ، لرزش پکت خروجی و عیارسنجی سرعت)...", "⚡ Stage 2: Deep Benchmarking (Ping, TLS Handshaking, Jitter, Speed)...")
                val finalCheckedList = mutableListOf<ScanResult>()
                _scanState.value = ScanState.Stage2Benchmark("", 0, aliveResultList.size)

                for (idx in aliveResultList.indices) {
                    val target = aliveResultList[idx]
                    _scanState.value = ScanState.Stage2Benchmark("${target.first}:${target.second}", idx + 1, aliveResultList.size)
                    addLog("⚙️ ارزیابی کیفیت ${target.first}:${target.second} (${idx + 1}/${aliveResultList.size})...", "⚙️ Profiling ${target.first}:${target.second} (${idx + 1}/${aliveResultList.size})...")

                    val result = ScannerEngine.benchmarkIp(
                        ip = target.first,
                        port = target.second,
                        timeoutMs = timeoutMs.value,
                        benchDurationSeconds = benchDurationSeconds.value,
                        jitterSamples = jitterSamples.value,
                        ignoreCert = ignoreCert.value,
                        onProgress = { phase ->
                            val faPhrase = when {
                                phase.contains("Handshaking") -> "اتصال امن TLS..."
                                phase.contains("Trace") -> "دریافت اطلاعات هدر لبه..."
                                phase.contains("Jitter") -> "بررسی پکت لاس و جیتر..."
                                phase.contains("Download") -> "تست سرعت دانلود..."
                                phase.contains("Upload") -> "تست سرعت آپلود..."
                                else -> phase
                            }
                            addLog("   📊 $faPhrase", "   📊 $phase")
                        }
                    )

                    if (result != null) {
                        if (result.timeoutRatio > maxLossRatio.value) {
                            addLog("   ❌ رد شد: درصد تلفات پکت بسیار بالا است (${(result.timeoutRatio * 100).toInt()}%).", "   ❌ Rejected: Packet Loss too high (${(result.timeoutRatio * 100).toInt()}%).")
                            continue
                        }
                        synchronized(finalCheckedList) {
                            finalCheckedList.add(result)
                            finalCheckedList.sortByDescending { it.score }
                            _liveResults.value = finalCheckedList.toList()
                        }
                        addLog("   ⭐ موفقیت‌آمیز! سرعت: ${result.downloadSpeed} مگابیت | امتیاز: ${result.score} | دیتاسنتر: ${result.colo}", "   ⭐ Success! Speed: ${result.downloadSpeed} Mbps | Score: ${result.score} | Colo: ${result.colo}")
                    } else {
                        addLog("   ❌ خطا: سرور لبه نبود یا هدر لازم از سمت کلادفلر بازگشت داده نشد.", "   ❌ Profiling failed or CDN header check invalid.")
                    }
                }

                addLog("   🏁 عملیات ارزیابی با موفقیت به پایان رسید! ${finalCheckedList.size} مپ‌رنج پاسخگو ثبت شد.", "🏁 Scanning Complete! ${finalCheckedList.size} nodes successfully scored.")
                _scanState.value = ScanState.Finished

                // Insert into scan history if we have results
                if (finalCheckedList.isNotEmpty()) {
                    val best = finalCheckedList.first()
                    repository.insertHistory(
                        ScanHistory(
                            totalScanned = totalTargets,
                            activeRanges = ranges.joinToString(", ") { it.cidr },
                            bestIp = best.ip,
                            bestPing = best.medianLatency,
                            bestSpeed = best.downloadSpeed,
                            bestScore = best.score
                        )
                    )
                }

            } catch (e: Exception) {
                addLog("❌ خطای سیستمی: ${e.message}", "❌ Fatal Error: ${e.message}")
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun stopScanning() {
        if (!_isScanning.value) return
        addLog("🛑 متوقف کردن عملیات اسکن فعال...", "🛑 Terminating scan...")
        scanJob?.cancel()
        _isScanning.value = false
        _scanState.value = ScanState.Idle
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
        }
    }

    fun deleteSavedIp(saved: SavedIp) {
        viewModelScope.launch {
            repository.deleteSavedIp(saved)
        }
    }

    fun clearSavedIps() {
        viewModelScope.launch {
            repository.clearSavedIps()
        }
    }

    // Configuration Management
    fun addConfigBase(name: String, rawUri: String) {
        viewModelScope.launch {
            repository.insertConfigBase(ConfigBase(name = name, rawUri = rawUri))
        }
    }

    fun deleteConfigBase(config: ConfigBase) {
        viewModelScope.launch {
            repository.deleteConfigBase(config)
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
