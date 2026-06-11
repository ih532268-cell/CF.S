package com.example.ui

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.SavedIp
import com.example.data.ScanHistory
import com.example.data.ScannerRepository
import com.example.network.CidrUtils
import com.example.network.ScanResult
import com.example.network.ScannerEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

object ScannerManager {
    private var application: Application? = null
    private var repository: ScannerRepository? = null

    // Configuration settings cached for pause/resume lifecycle
    private var targetPorts = listOf<Int>()
    private var scanSampling = 20
    private var scanTimeoutMs = 3500
    private var scanBenchDuration = 4.0
    private var scanJitterSamples = 10
    private var scanMaxLossRatio = 0.4
    private var scanIgnoreCert = true
    private var scanConcurrency = 32
    var isEnglish = false

    // Scanner tracking variables for Resume Capability
    private var currentStage = 1 // 1: PortScan, 2: Benchmark
    private var allTargetsTotalCount = 0
    private var checkedTargetsCount = 0
    private val remainingTargetsQueue = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
    private val activePortFoundList = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Int>>())

    private val remainingAliveList = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Int>>())
    private val benchmarkedResults = java.util.Collections.synchronizedList(mutableListOf<ScanResult>())
    private var totalAliveToBenchmarkCount = 0

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null

    // Public StateFlows
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState = _scanState.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val terminalLogs = _terminalLogs.asStateFlow()

    private val _liveResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val liveResults = _liveResults.asStateFlow()

    fun initialize(app: Application) {
        if (application == null) {
            application = app
            val db = AppDatabase.getDatabase(app)
            repository = ScannerRepository(db)
        }
    }

    private val logLock = Any()
    fun addLog(fa: String, en: String) {
        synchronized(logLock) {
            val timeStamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val currentLogs = _terminalLogs.value
            val newLog = TerminalLog(timeStamp, fa, en)
            _terminalLogs.value = if (currentLogs.size >= 500) {
                currentLogs.drop(currentLogs.size - 499) + newLog
            } else {
                currentLogs + newLog
            }
        }
    }

    fun clearLogs() {
        synchronized(logLock) {
            _terminalLogs.value = emptyList()
        }
    }

    fun startScan(
        cidrRanges: List<com.example.data.ScanRange>,
        ports: String,
        sampling: Int,
        timeoutMs: Int,
        benchSec: Double,
        jitter: Int,
        maxLoss: Double,
        ignoreCert: Boolean,
        concurrency: Int,
        isEng: Boolean
    ) {
        if (_isScanning.value) return

        val ranges = cidrRanges.filter { it.isEnabled }
        if (ranges.isEmpty()) {
            addLog("❌ خطا: هیچ رنج آی‌پی فعال تعریف نشده است!", "❌ Error: No IP Ranges are selected/enabled in Settings!")
            _scanState.value = ScanState.Idle
            _isScanning.value = false
            return
        }

        targetPorts = ports.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(443) }

        scanSampling = sampling
        scanTimeoutMs = timeoutMs
        scanBenchDuration = benchSec
        scanJitterSamples = jitter
        scanMaxLossRatio = maxLoss
        scanIgnoreCert = ignoreCert
        scanConcurrency = concurrency
        isEnglish = isEng

        clearLogs()
        _liveResults.value = emptyList()

        addLog("🚀 شروع اسکنر کلادفلر...", "🚀 Starting Cloudflare Scanner...")
        addLog("🛠️ پیکربندی بافرهای شبکه و محیط SSL...", "🛠️ Configuring Network Buffers & SSL Context...")
        addLog("📂 در حال آماده‌سازی آدرس‌های کاندیدا...", "📂 Generating candidates from subnets...")

        val listTargets = mutableListOf<Pair<String, Int>>()
        ranges.forEach { range ->
            val ipList = CidrUtils.generateIpsFromCidr(range.cidr, sampling)
            ipList.forEach { ip ->
                targetPorts.forEach { port ->
                    listTargets.add(Pair(ip, port))
                }
            }
        }

        if (listTargets.isEmpty()) {
            addLog("❌ خطا: صف آدرس کاندیدی تولید نشد. رنج‌های CIDR را بررسی کنید!", "❌ Error: Could not generate any candidate IPs. Check CIDRs!")
            _scanState.value = ScanState.Idle
            _isScanning.value = false
            return
        }

        allTargetsTotalCount = listTargets.size
        checkedTargetsCount = 0
        
        synchronized(remainingTargetsQueue) {
            remainingTargetsQueue.clear()
            remainingTargetsQueue.addAll(listTargets)
        }
        synchronized(activePortFoundList) {
            activePortFoundList.clear()
        }
        synchronized(remainingAliveList) {
            remainingAliveList.clear()
        }
        synchronized(benchmarkedResults) {
            benchmarkedResults.clear()
        }
        totalAliveToBenchmarkCount = 0
        currentStage = 1

        runScanningJob()
    }

    fun pauseScan() {
        if (!_isScanning.value) return
        addLog("⏸️ توقف موقت عملیات اسکن...", "⏸️ Pausing scan...")
        scanJob?.cancel()
        _isScanning.value = false
        _isPaused.value = true
        // Keep current Stage & Cache intact
        addLog("⏸️ اسکن متوقف شد. از منوی اصلی یا نوتیفیکیشن می‌توانید آن را ادامه دهید.", "⏸️ Scan paused. You can resume from settings or notification.")
        startService() // Refresh notification action button layout
    }

    fun resumeScan() {
        if (_isScanning.value) return
        addLog("▶️ ادامه‌ی اسکن از نقطه توقف...", "▶️ Resuming scan from paused checkpoint...")
        runScanningJob()
    }

    fun stopScan() {
        addLog("🛑 متوقف کردن کامل و نشانه‌گذاری خروج...", "🛑 Terminating and resetting scanner state...")
        scanJob?.cancel()
        _isScanning.value = false
        _isPaused.value = false
        _scanState.value = ScanState.Idle
        
        synchronized(remainingTargetsQueue) { remainingTargetsQueue.clear() }
        synchronized(activePortFoundList) { activePortFoundList.clear() }
        synchronized(remainingAliveList) { remainingAliveList.clear() }
        synchronized(benchmarkedResults) { benchmarkedResults.clear() }
        totalAliveToBenchmarkCount = 0
        checkedTargetsCount = 0
        allTargetsTotalCount = 0
        
        stopService()
    }

    private fun runScanningJob() {
        _isScanning.value = true
        _isPaused.value = false
        startService()

        scanJob = scope.launch {
            try {
                val repo = repository ?: return@launch

                // Stage 1 Port Scan
                if (currentStage == 1) {
                    val queueSnapshot = synchronized(remainingTargetsQueue) { remainingTargetsQueue.toList() }
                    if (queueSnapshot.isEmpty()) {
                        addLog("❌ خطا: صف کاندیداها خالی است.", "❌ Error: Queue of candidates is empty.")
                        finishScan(false)
                        return@launch
                    }

                    addLog("📋 کل کانال‌های هدف باقی‌مانده: ${queueSnapshot.size}", "📋 Total remaining target sockets: ${queueSnapshot.size}")
                    addLog("🔎 مرحله ۱: بررسی بست آدرس‌های لبه فعال روی پورت‌های $targetPorts", "🔎 Step 1: Active Port Checking on ports $targetPorts")

                    _scanState.value = ScanState.Stage1PortScan(checkedTargetsCount, allTargetsTotalCount, activePortFoundList.size)

                    val channel = Channel<Pair<String, Int>>(capacity = 1000)
                    
                    val producer = launch(Dispatchers.Default) {
                        try {
                            for (target in queueSnapshot) {
                                channel.send(target)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            channel.close()
                        }
                    }

                    val workerCount = minOf(scanConcurrency, queueSnapshot.size).coerceAtLeast(1)
                    val activeList = activePortFoundList
                    
                    val stage1Workers = List(workerCount) {
                        async {
                            for (target in channel) {
                                if (!isActive) break
                                try {
                                    val active = ScannerEngine.checkPort(target.first, target.second, scanTimeoutMs)
                                    synchronized(remainingTargetsQueue) {
                                        remainingTargetsQueue.remove(target)
                                        checkedTargetsCount = allTargetsTotalCount - remainingTargetsQueue.size
                                    }
                                    if (active) {
                                        activeList.add(target)
                                        addLog("🟢 آی‌پی پاسخگو در ${target.first}:${target.second}", "🟢 Edge alive on ${target.first}:${target.second}")
                                    }
                                    if (checkedTargetsCount % 10 == 0 || active) {
                                        _scanState.value = ScanState.Stage1PortScan(checkedTargetsCount, allTargetsTotalCount, activeList.size)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    stage1Workers.awaitAll()
                    producer.join()

                    if (!isActive) {
                        // Job was cancelled due to pause or stop
                        return@launch
                    }

                    val aliveSnapshot = synchronized(activeList) { activeList.toList() }
                    addLog("✅ مرحله ۱ پایان یافت. ${aliveSnapshot.size} گره‌ی فعال پاسخگو پیدا شد!", "✅ Stage 1 Complete. Found ${aliveSnapshot.size} responsive Edge Nodes!")

                    if (aliveSnapshot.isEmpty()) {
                        addLog("⚠️ هیچ سرور فعالی پاسخ نداد. پورت یا آی‌پی‌های تستی دیگر را امتحان کنید.", "⚠️ No active CDNs verified. Try higher timeouts or other ranges.")
                        finishScan(false)
                        return@launch
                    }

                    synchronized(remainingAliveList) {
                        remainingAliveList.clear()
                        remainingAliveList.addAll(aliveSnapshot)
                        totalAliveToBenchmarkCount = aliveSnapshot.size
                    }
                    currentStage = 2
                }

                // Stage 2 Deep Benchmark
                if (currentStage == 2) {
                    addLog("⚡ مرحله ۲: ارزیابی و امتیازدهی نهایی (پینگ، لرزش پکت خروجی و عیارسنجی سرعت)...", "⚡ Stage 2: Deep Benchmarking (Ping, TLS Handshaking, Jitter, Speed)...")
                    _scanState.value = ScanState.Stage2Benchmark("", totalAliveToBenchmarkCount - remainingAliveList.size, totalAliveToBenchmarkCount)

                    while (true) {
                        if (!isActive) return@launch
                        val target = synchronized(remainingAliveList) {
                            if (remainingAliveList.isEmpty()) null else remainingAliveList.removeAt(0)
                        } ?: break

                        val currentIdx = totalAliveToBenchmarkCount - remainingAliveList.size
                        _scanState.value = ScanState.Stage2Benchmark("${target.first}:${target.second}", currentIdx, totalAliveToBenchmarkCount)

                        addLog("⚙️ ارزیابی کیفیت ${target.first}:${target.second} ($currentIdx/$totalAliveToBenchmarkCount)...", "⚙️ Profiling ${target.first}:${target.second} ($currentIdx/$totalAliveToBenchmarkCount)...")

                        val result = ScannerEngine.benchmarkIp(
                            ip = target.first,
                            port = target.second,
                            timeoutMs = scanTimeoutMs,
                            benchDurationSeconds = scanBenchDuration,
                            jitterSamples = scanJitterSamples,
                            ignoreCert = scanIgnoreCert,
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
                            if (result.timeoutRatio > scanMaxLossRatio) {
                                addLog("   ❌ رد شد: درصد تلفات پکت بسیار بالا است (${(result.timeoutRatio * 100).toInt()}%).", "   ❌ Rejected: Packet Loss too high (${(result.timeoutRatio * 100).toInt()}%).")
                                continue
                            }
                            synchronized(benchmarkedResults) {
                                benchmarkedResults.add(result)
                                benchmarkedResults.sortByDescending { it.score }
                                _liveResults.value = benchmarkedResults.toList()
                            }
                            addLog("   ⭐ موفقیت‌آمیز! سرعت: ${result.downloadSpeed} مگابیت | امتیاز: ${result.score} | دیتاسنتر: ${result.colo}", "   ⭐ Success! Speed: ${result.downloadSpeed} Mbps | Score: ${result.score} | Colo: ${result.colo}")
                        } else {
                            addLog("   ❌ خطا: سرور لبه نبود یا هدر لازم از سمت کلادفلر بازگشت داده نشد.", "   ❌ Profiling failed or CDN header check invalid.")
                        }
                    }

                    // Completed Stage 2 Successfully!
                    saveAndFinishSuccessfully()
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    // standard cancel
                } else {
                    addLog("❌ خطای سیستمی: ${e.message}", "❌ Fatal Error: ${e.message}")
                    e.printStackTrace()
                    finishScan(false)
                }
            } finally {
                if (!_isPaused.value && scanJob?.isCancelled != true) {
                    _isScanning.value = false
                    stopService()
                }
            }
        }
    }

    private suspend fun saveAndFinishSuccessfully() {
        val repo = repository ?: return
        val results = benchmarkedResults.toList()
        
        addLog("🏁 عملیات ارزیابی با موفقیت به پایان رسید! ${results.size} مپ‌رنج پاسخگو ثبت شد.", "🏁 Scanning Complete! ${results.size} nodes successfully scored.")
        _scanState.value = ScanState.Finished

        val ranges = repo.scanRanges.first().filter { it.isEnabled }

        if (results.isNotEmpty()) {
            val best = results.first()
            repo.insertHistory(
                ScanHistory(
                    totalScanned = allTargetsTotalCount,
                    activeRanges = ranges.joinToString(", ") { it.cidr },
                    bestIp = best.ip,
                    bestPing = best.medianLatency,
                    bestSpeed = best.downloadSpeed,
                    bestScore = best.score
                )
            )

            results.forEach { res ->
                val savedIp = SavedIp(
                    ip = res.ip,
                    port = res.port,
                    colo = res.colo,
                    country = res.country,
                    ping = res.medianLatency,
                    dlSpeed = res.downloadSpeed,
                    ulSpeed = res.uploadSpeed,
                    jitter = res.jitter,
                    score = res.score,
                    timestamp = System.currentTimeMillis()
                )
                repo.insertSavedIp(savedIp)
            }
        }
        
        currentStage = 1 // Reset for next run
        _isScanning.value = false
        _isPaused.value = false
        stopService()
    }

    private fun finishScan(endedSuccessfully: Boolean) {
        _isScanning.value = false
        _isPaused.value = false
        _scanState.value = if (endedSuccessfully) ScanState.Finished else ScanState.Idle
        currentStage = 1
        stopService()
    }

    private fun startService() {
        val app = application ?: return
        val intent = Intent(app, ScannerService::class.java).apply {
            action = ScannerService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopService() {
        val app = application ?: return
        val intent = Intent(app, ScannerService::class.java)
        try {
            app.stopService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
