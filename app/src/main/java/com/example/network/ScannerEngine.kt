package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

data class ScanResult(
    val ip: String,
    val port: Int,
    val tcpLatency: Double,
    val tlsLatency: Double,
    val meanLatency: Double,
    val medianLatency: Double,
    val p95Latency: Double,
    val jitter: Double,
    val timeoutRatio: Double,
    val downloadSpeed: Double,
    val uploadSpeed: Double,
    val stabilityScore: Int,
    val score: Double,
    val colo: String,
    val country: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ScannerEngine {

    private const val TAG = "ScannerEngine"
    private const val CF_HOST = "speed.cloudflare.com"
    private const val BUFFER_SIZE = 128 * 1024 // 128 KB standard buffer for dynamic scale

    suspend fun checkPort(ip: String, port: Int, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                socket?.close()
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }

    suspend fun benchmarkIp(
        ip: String,
        port: Int,
        timeoutMs: Int,
        benchDurationSeconds: Double,
        jitterSamples: Int,
        ignoreCert: Boolean,
        onProgress: (String) -> Unit = {}
    ): ScanResult? = withContext(Dispatchers.IO) {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            onProgress("🤝 Handshaking Dynamic TLS...")
            rawSocket = Socket()
            rawSocket.tcpNoDelay = true
            rawSocket.soTimeout = timeoutMs
            try {
                rawSocket.receiveBufferSize = BUFFER_SIZE
                rawSocket.sendBufferSize = BUFFER_SIZE
            } catch (ex: Exception) {
                Log.w(TAG, "Could not scale buffers: ${ex.message}")
            }

            val t0 = System.nanoTime()
            rawSocket.connect(InetSocketAddress(ip, port), timeoutMs)
            val tcpLatency = (System.nanoTime() - t0) / 1_000_000.0

            val sslFactory = if (ignoreCert) {
                TrustAllSocketFactory.getTrustAllSslSocketFactory()
            } else {
                SSLSocketFactory.getDefault() as SSLSocketFactory
            }

            val t1 = System.nanoTime()
            sslSocket = sslFactory.createSocket(rawSocket, ip, port, true) as SSLSocket
            val sslParameters = sslSocket.sslParameters
            sslParameters.serverNames = listOf(SNIHostName(CF_HOST))
            sslSocket.sslParameters = sslParameters
            sslSocket.startHandshake()
            val tlsLatency = (System.nanoTime() - t1) / 1_000_000.0

            // 1. Fetch Trace
            onProgress("📡 Fetching Trace Data...")
            val traceText = fetchTraceText(sslSocket, timeoutMs) ?: return@withContext null
            val traceInfo = parseTrace(traceText)
            val isCf = traceInfo.containsKey("colo") || traceInfo.containsKey("loc")
            if (!isCf) {
                Log.d(TAG, "Not a Cloudflare CDN endpoint on $ip:$port")
                return@withContext null
            }

            val colo = traceInfo["colo"] ?: "UNK"
            val country = traceInfo["loc"] ?: "UNK"
            val cfIp = traceInfo["ip"] ?: ip

            // 2. Jitter & Ploss
            onProgress("📊 Analyzing Jitter ($jitterSamples samples)...")
            val jitterMetrics = runJitterTest(sslSocket, jitterSamples, timeoutMs)

            // 3. Download Speed Test
            onProgress("📥 Download Benchmarking...")
            val dlMetrics = runDownloadTest(ip, port, timeoutMs, benchDurationSeconds, ignoreCert)

            // 4. Upload Speed Test
            onProgress("📤 Upload Benchmarking...")
            val ulMetrics = runUploadTest(ip, port, timeoutMs, benchDurationSeconds, ignoreCert)

            val stabilityScore = ((dlMetrics.stability + ulMetrics.stability) / 2)

            val score = calculateWeightedScore(
                medLat = jitterMetrics.median,
                p95Lat = jitterMetrics.p95,
                jitter = jitterMetrics.jitter,
                dlSpeed = dlMetrics.speedMbps,
                ulSpeed = ulMetrics.speedMbps,
                stability = stabilityScore,
                lossRate = jitterMetrics.lossRatio,
                colo = colo
            )

            ScanResult(
                ip = ip,
                port = port,
                tcpLatency = tcpLatency,
                tlsLatency = tlsLatency,
                meanLatency = jitterMetrics.mean,
                medianLatency = jitterMetrics.median,
                p95Latency = jitterMetrics.p95,
                jitter = jitterMetrics.jitter,
                timeoutRatio = jitterMetrics.lossRatio,
                downloadSpeed = dlMetrics.speedMbps,
                uploadSpeed = ulMetrics.speedMbps,
                stabilityScore = stabilityScore,
                score = score,
                colo = colo,
                country = country
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fail benchmarking IP $ip:$port — ${e.message}")
            null
        } finally {
            try {
                sslSocket?.close()
            } catch (ex: Exception) {}
            try {
                rawSocket?.close()
            } catch (ex: Exception) {}
        }
    }

    private fun fetchTraceText(sslSocket: SSLSocket, timeoutMs: Int): String? {
        return try {
            val req = "GET /cdn-cgi/trace HTTP/1.1\r\n" +
                    "Host: $CF_HOST\r\n" +
                    "User-Agent: CFScanner-Pro-Agha-Davood/10.0-Davood\r\n" +
                    "Accept: */*\r\n" +
                    "Connection: keep-alive\r\n\r\n"
            sslSocket.soTimeout = timeoutMs
            val out = sslSocket.outputStream
            out.write(req.toByteArray(Charsets.UTF_8))
            out.flush()

            val inp = sslSocket.inputStream
            val responseBytes = readHttpBody(inp, timeoutMs)
            responseBytes?.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun readHttpBody(inp: InputStream, timeoutMs: Int): ByteArray? {
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        val deadline = System.currentTimeMillis() + timeoutMs
        var headerBytesStr = ""
        var contentLength = -1
        var headerEnded = false

        while (true) {
            if (System.currentTimeMillis() > deadline) break
            if (bos.size() > 65536) break // Limit trace buffer size to 64KB to avoid OOM
            val read = inp.read(buffer)
            if (read == -1) break
            bos.write(buffer, 0, read)

            if (!headerEnded) {
                headerBytesStr = bos.toString("UTF-8")
                val delimiterIndex = headerBytesStr.indexOf("\r\n\r\n")
                if (delimiterIndex != -1) {
                    headerEnded = true
                    // parse content length
                    val headers = headerBytesStr.substring(0, delimiterIndex).lowercase()
                    val clHeader = "content-length:"
                    val clIndex = headers.indexOf(clHeader)
                    if (clIndex != -1) {
                        val start = clIndex + clHeader.length
                        val end = headers.indexOf("\r\n", start)
                        if (end != -1) {
                            contentLength = headers.substring(start, end).trim().toIntOrNull() ?: -1
                        }
                    }
                }
            }

            if (headerEnded && contentLength != -1) {
                val fullBytes = bos.toByteArray()
                val headerLength = headerBytesStr.indexOf("\r\n\r\n") + 4
                val bodyLength = fullBytes.size - headerLength
                if (bodyLength >= contentLength) {
                    val bodyOnly = ByteArray(contentLength)
                    System.arraycopy(fullBytes, headerLength, bodyOnly, 0, contentLength)
                    return bodyOnly
                }
            }
        }

        // Fallback: try parsing body manually
        val raw = bos.toByteArray()
        val delimiterIndex = raw.indexOfDelimiter()
        if (delimiterIndex != -1 && delimiterIndex + 4 < raw.size) {
            val size = raw.size - (delimiterIndex + 4)
            val result = ByteArray(size)
            System.arraycopy(raw, delimiterIndex + 4, result, 0, size)
            return result
        }
        return null
    }

    private fun ByteArray.indexOfDelimiter(): Int {
        for (i in 0 until this.size - 3) {
            if (this[i] == '\r'.toByte() && this[i+1] == '\n'.toByte() &&
                this[i+2] == '\r'.toByte() && this[i+3] == '\n'.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun parseTrace(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        for (line in text.split("\n")) {
            val cleanLine = line.trim()
            if (cleanLine.contains("=")) {
                val p = cleanLine.split("=", limit = 2)
                if (p.size == 2) {
                    map[p[0].trim()] = p[1].trim()
                }
            }
        }
        return map
    }

    data class JitterMetrics(
        val median: Double,
        val p95: Double,
        val mean: Double,
        val jitter: Double,
        val lossRatio: Double
    )

    private fun runJitterTest(sslSocket: SSLSocket, samples: Int, timeoutMs: Int): JitterMetrics {
        val latencies = mutableListOf<Double>()
        var failures = 0
        val warmup = 1
        sslSocket.soTimeout = timeoutMs

        val req = "HEAD /cdn-cgi/trace HTTP/1.1\r\n" +
                "Host: $CF_HOST\r\n" +
                "Connection: keep-alive\r\n\r\n"

        val out = sslSocket.outputStream
        val inp = sslSocket.inputStream
        val buffer = ByteArray(1024)

        for (i in 0 until (samples + warmup)) {
            val t0 = System.nanoTime()
            try {
                out.write(req.toByteArray(Charsets.UTF_8))
                out.flush()

                // Read until end of headers (\r\n\r\n) or socket end
                var read = 0
                val bos = ByteArrayOutputStream()
                while (true) {
                    if (bos.size() > 16384) {
                        failures++
                        break
                    }
                    val r = inp.read(buffer)
                    if (r == -1) {
                        failures++
                        break
                    }
                    bos.write(buffer, 0, r)
                    if (bos.toString("UTF-8").contains("\r\n\r\n")) {
                        val lat = (System.nanoTime() - t0) / 1_000_000.0
                        if (i >= warmup) {
                            latencies.add(lat)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                if (i >= warmup) {
                    failures++
                    latencies.add(timeoutMs.toDouble())
                }
            }
        }

        if (latencies.isEmpty()) {
            return JitterMetrics(timeoutMs.toDouble(), timeoutMs.toDouble(), timeoutMs.toDouble(), timeoutMs.toDouble(), 1.0)
        }

        val sorted = latencies.sorted()
        val n = sorted.size
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        val p95Idx = minOf((n * 0.95).toInt(), n - 1)
        val p95 = sorted[p95Idx]
        val mean = latencies.average()

        var jitterSum = 0.0
        for (i in 0 until latencies.size - 1) {
            jitterSum += Math.abs(latencies[i + 1] - latencies[i])
        }
        val jitter = if (latencies.size > 1) jitterSum / (latencies.size - 1) else 0.0
        val lossRatio = failures.toDouble() / samples.toDouble()

        return JitterMetrics(median, p95, mean, jitter, lossRatio)
    }

    data class BenchMetrics(val speedMbps: Double, val stability: Int)

    private fun runDownloadTest(
        ip: String,
        port: Int,
        timeoutMs: Int,
        durationSeconds: Double,
        ignoreCert: Boolean
    ): BenchMetrics {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            rawSocket = Socket()
            rawSocket.tcpNoDelay = true
            rawSocket.receiveBufferSize = BUFFER_SIZE
            rawSocket.soTimeout = timeoutMs
            rawSocket.connect(InetSocketAddress(ip, port), timeoutMs)

            val sslFactory = if (ignoreCert) {
                TrustAllSocketFactory.getTrustAllSslSocketFactory()
            } else {
                SSLSocketFactory.getDefault() as SSLSocketFactory
            }
            sslSocket = sslFactory.createSocket(rawSocket, ip, port, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs
            sslSocket.startHandshake()

            // To save user's cellular data and speed up the app significantly, we fetch 2 MB for benchmarking
            val downloadBytesCount = 2 * 1024 * 1024
            val req = "GET /__down?bytes=$downloadBytesCount HTTP/1.1\r\n" +
                    "Host: $CF_HOST\r\n" +
                    "User-Agent: Mozilla/5.0 Powered-by-Agha-Davood\r\n" +
                    "Connection: close\r\n\r\n"

            val out = sslSocket.outputStream
            out.write(req.toByteArray(Charsets.UTF_8))
            out.flush()

            val inp = sslSocket.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead = 0
            val start = System.currentTimeMillis()
            val maxDurationMs = (durationSeconds * 1000).toLong()

            // Read headers first
            var headersRead = false
            val headerBos = ByteArrayOutputStream()
            while (!headersRead) {
                if (headerBos.size() > 65536) break
                val r = inp.read(buffer, 0, minOf(buffer.size, 1024))
                if (r == -1) break
                headerBos.write(buffer, 0, r)
                if (headerBos.toString("UTF-8").contains("\r\n\r\n")) {
                    headersRead = true
                }
            }

            val windows = mutableListOf<Double>()
            var wBytes = 0
            var wStart = System.currentTimeMillis()

            while (true) {
                val now = System.currentTimeMillis()
                if (now - start >= maxDurationMs) break

                val toRead = buffer.size
                val r = inp.read(buffer, 0, toRead)
                if (r == -1) break
                bytesRead += r
                wBytes += r

                val wElapsed = System.currentTimeMillis() - wStart
                if (wElapsed >= 250) {
                    val speed = (wBytes * 8.0) / (wElapsed / 1000.0 * 1_000_000.0)
                    windows.add(speed)
                    wBytes = 0
                    wStart = System.currentTimeMillis()
                }
            }

            val elapsedSecCount = (System.currentTimeMillis() - start) / 1000.0
            if (elapsedSecCount < 0.05 || bytesRead == 0) return BenchMetrics(0.0, 10)

            val overallMbps = (bytesRead * 8.0) / (elapsedSecCount * 1_000_000.0)

            // Compute stability
            if (windows.size >= 2) {
                val mean = windows.average()
                val variance = windows.map { Math.pow(it - mean, 2.0) }.sum() / windows.size
                val std = Math.sqrt(variance)
                val cv = if (mean > 0.0) std / mean else 1.0
                val stability = maxOf(0, minOf(100, ((1.0 - cv) * 100).toInt()))
                return BenchMetrics(Math.round(overallMbps * 100.0) / 100.0, stability)
            }

            return BenchMetrics(Math.round(overallMbps * 100.0) / 100.0, 50)
        } catch (e: Exception) {
            return BenchMetrics(0.0, 0)
        } finally {
            try { sslSocket?.close() } catch (e: Exception) {}
            try { rawSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun runUploadTest(
        ip: String,
        port: Int,
        timeoutMs: Int,
        durationSeconds: Double,
        ignoreCert: Boolean
    ): BenchMetrics {
        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            rawSocket = Socket()
            rawSocket.tcpNoDelay = true
            rawSocket.sendBufferSize = BUFFER_SIZE
            rawSocket.soTimeout = timeoutMs
            rawSocket.connect(InetSocketAddress(ip, port), timeoutMs)

            val sslFactory = if (ignoreCert) {
                TrustAllSocketFactory.getTrustAllSslSocketFactory()
            } else {
                SSLSocketFactory.getDefault() as SSLSocketFactory
            }
            sslSocket = sslFactory.createSocket(rawSocket, ip, port, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs
            sslSocket.startHandshake()

            // 1 MB uploaded content
            val uploadBytesCount = 1024 * 1024
            val headers = "POST /__up HTTP/1.1\r\n" +
                    "Host: $CF_HOST\r\n" +
                    "User-Agent: Mozilla/5.0 Powered-by-Agha-Davood\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Content-Length: $uploadBytesCount\r\n" +
                    "Connection: close\r\n\r\n"

            val out = sslSocket.outputStream
            out.write(headers.toByteArray(Charsets.UTF_8))
            out.flush()

            val randomChunk = ByteArray(16 * 1024)
            SecureRandom().nextBytes(randomChunk)

            var bytesSent = 0
            val start = System.currentTimeMillis()
            val maxDurationMs = (durationSeconds * 1000).toLong()

            val windows = mutableListOf<Double>()
            var wBytes = 0
            var wStart = System.currentTimeMillis()

            while (bytesSent < uploadBytesCount) {
                val now = System.currentTimeMillis()
                if (now - start >= maxDurationMs) break

                val toSend = minOf(randomChunk.size, uploadBytesCount - bytesSent)
                out.write(randomChunk, 0, toSend)
                bytesSent += toSend
                wBytes += toSend

                val wElapsed = System.currentTimeMillis() - wStart
                if (wElapsed >= 250) {
                    val speed = (wBytes * 8.0) / (wElapsed / 1000.0 * 1_000_000.0)
                    windows.add(speed)
                    wBytes = 0
                    wStart = System.currentTimeMillis()
                }
            }
            out.flush()

            val elapsedSecCount = (System.currentTimeMillis() - start) / 1000.0
            if (elapsedSecCount < 0.05 || bytesSent == 0) return BenchMetrics(0.0, 10)

            val overallMbps = (bytesSent * 8.0) / (elapsedSecCount * 1_000_000.0)

            if (windows.size >= 2) {
                val mean = windows.average()
                val variance = windows.map { Math.pow(it - mean, 2.0) }.sum() / windows.size
                val std = Math.sqrt(variance)
                val cv = if (mean > 0.0) std / mean else 1.0
                val stability = maxOf(0, minOf(100, ((1.0 - cv) * 100).toInt()))
                return BenchMetrics(Math.round(overallMbps * 100.0) / 100.0, stability)
            }

            return BenchMetrics(Math.round(overallMbps * 100.0) / 100.0, 50)
        } catch (e: Exception) {
            return BenchMetrics(0.0, 0)
        } finally {
            try { sslSocket?.close() } catch (e: Exception) {}
            try { rawSocket?.close() } catch (e: Exception) {}
        }
    }

    fun calculateWeightedScore(
        medLat: Double,
        p95Lat: Double,
        jitter: Double,
        dlSpeed: Double,
        ulSpeed: Double,
        stability: Int,
        lossRate: Double,
        colo: String
    ): Double {
        val latScore = when {
            medLat <= 80.0 -> 250.0
            medLat < 400.0 -> maxOf(0.0, 250.0 * (1.0 - ((medLat - 80.0) / 320.0)))
            else -> 0.0
        }

        var p95Penalty = 0.0
        if (p95Lat > medLat * 1.5) {
            p95Penalty = minOf(50.0, (p95Lat - medLat) / 10.0)
        }

        val jitterScore = when {
            jitter < 15.0 -> 150.0
            jitter < 100.0 -> maxOf(0.0, 150.0 * (1.0 - ((jitter - 15.0) / 85.0)))
            else -> 0.0
        }

        val dlScore = if (dlSpeed > 0.0) {
            minOf(300.0, Math.log1p(dlSpeed) / Math.log1p(100.0) * 300.0)
        } else 0.0

        val ulScore = if (ulSpeed > 0.0) {
            minOf(150.0, Math.log1p(ulSpeed) / Math.log1p(30.0) * 150.0)
        } else 0.0

        val stabScore = maxOf(0.0, minOf(100.0, stability.toDouble()))

        val preferredColos = listOf("THR", "DXB", "KWI", "BAH", "MCT", "IST", "AMS", "FRA", "LHR", "CDG")
        val coloBonus = when {
            preferredColos.take(5).contains(colo.uppercase()) -> 50.0
            preferredColos.drop(5).contains(colo.uppercase()) -> 20.0
            else -> 0.0
        }

        var total = latScore - p95Penalty + jitterScore + dlScore + ulScore + stabScore + coloBonus
        total *= maxOf(0.0, 1.0 - (lossRate * 2.5))
        return Math.round(maxOf(0.0, total) * 10.0) / 10.0
    }
}
