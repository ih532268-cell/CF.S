package com.example.network

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.min

object CidrUtils {

    fun countIps(cidr: String, sampleCount: Int = 100): Int {
        val trimmed = cidr.trim()
        if (trimmed.isEmpty()) return 0
        val normalized = trimmed
            .replace(Regex("\\s*-\\s*"), "-")
            .replace(Regex("\\s*/\\s*"), "/")
        val tokens = normalized.split(Regex("[,;\\s\\n\\r]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            return tokens.sumOf { countSingleTokenIps(it, sampleCount) }
        }
        return countSingleTokenIps(normalized, sampleCount)
    }

    private fun countSingleTokenIps(token: String, sampleCount: Int): Int {
        if (token.isEmpty()) return 0
        if (token.contains("-")) {
            val parts = token.split("-").map { it.trim() }
            if (parts.size != 2) return 0
            val startIpStr = parts[0]
            var endIpStr = parts[1]
            if (!endIpStr.contains(".")) {
                val lastDotIdx = startIpStr.lastIndexOf('.')
                if (lastDotIdx != -1) {
                    endIpStr = startIpStr.substring(0, lastDotIdx + 1) + endIpStr
                }
            }
            return try {
                val startAddr = InetAddress.getByName(startIpStr)
                val endAddr = InetAddress.getByName(endIpStr)
                val startBytes = startAddr.address
                val endBytes = endAddr.address
                if (startBytes.size != 4 || endBytes.size != 4) return 0
                var startInt = 0
                var endInt = 0
                for (i in 0..3) {
                    startInt = (startInt shl 8) or (startBytes[i].toInt() and 0xFF)
                    endInt = (endInt shl 8) or (endBytes[i].toInt() and 0xFF)
                }
                val minInt = minOf(startInt, endInt)
                val maxInt = maxOf(startInt, endInt)
                (maxInt.toLong() - minInt.toLong() + 1).coerceAtMost(10000L).toInt()
            } catch (e: Exception) {
                0
            }
        }
        if (!token.contains("/")) return 1
        val parts = token.split("/")
        if (parts.size != 2) return 0
        val ipPart = parts[0]
        val prefixPart = parts[1]
        val prefix = prefixPart.toIntOrNull() ?: return 0
        if (ipPart.contains(":")) {
            return minOf(sampleCount, 100)
        } else {
            val hostBits = 32 - prefix
            if (hostBits <= 0) return 1
            val numAddresses = 1L shl hostBits
            return if (numAddresses <= sampleCount) numAddresses.toInt() else sampleCount
        }
    }

    fun generateIpsFromCidr(cidr: String, sampleCount: Int = 100): List<String> {
        val trimmed = cidr.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Normalize spaces around '-' and '/' so they do not split on spaces
        val normalized = trimmed
            .replace(Regex("\\s*-\\s*"), "-")
            .replace(Regex("\\s*/\\s*"), "/")

        // Support multiple IP specifications separated by commas, semicolons, spaces, or newlines
        val tokens = normalized.split(Regex("[,;\\s\\n\\r]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size > 1) {
            val allIps = mutableListOf<String>()
            tokens.forEach { token ->
                allIps.addAll(generateSingleTokenIps(token, sampleCount))
            }
            return allIps.distinct()
        }

        return generateSingleTokenIps(normalized, sampleCount)
    }

    private fun generateSingleTokenIps(token: String, sampleCount: Int): List<String> {
        if (token.isEmpty()) return emptyList()

        // Handle dash IP ranges (e.g. 192.168.1.10-192.168.1.50 or 192.168.1.10-50)
        if (token.contains("-")) {
            return generateFromRangeToken(token)
        }

        if (!token.contains("/")) {
            // It is a single IP address
            return listOf(cleanIp(token))
        }

        val parts = token.split("/")
        if (parts.size != 2) return emptyList()

        val ipPart = parts[0]
        val prefixPart = parts[1]

        val prefix = prefixPart.toIntOrNull() ?: return emptyList()

        return if (ipPart.contains(":")) {
            // IPv6
            generateIpv6(ipPart, prefix, sampleCount)
        } else {
            // IPv4
            generateIpv4(ipPart, prefix, sampleCount)
        }
    }

    private fun generateFromRangeToken(token: String): List<String> {
        val parts = token.split("-").map { it.trim() }
        if (parts.size != 2) return emptyList()
        val startIpStr = parts[0]
        var endIpStr = parts[1]

        // Handle shorthand range, e.g. 192.168.1.10-50
        if (!endIpStr.contains(".")) {
            val lastDotIdx = startIpStr.lastIndexOf('.')
            if (lastDotIdx != -1) {
                endIpStr = startIpStr.substring(0, lastDotIdx + 1) + endIpStr
            }
        }

        try {
            val startAddr = InetAddress.getByName(startIpStr)
            val endAddr = InetAddress.getByName(endIpStr)
            val startBytes = startAddr.address
            val endBytes = endAddr.address

            if (startBytes.size != 4 || endBytes.size != 4) return emptyList()

            var startInt = 0
            var endInt = 0
            for (i in 0..3) {
                startInt = (startInt shl 8) or (startBytes[i].toInt() and 0xFF)
                endInt = (endInt shl 8) or (endBytes[i].toInt() and 0xFF)
            }

            val minInt = minOf(startInt, endInt)
            val maxInt = maxOf(startInt, endInt)

            // Limit scanning range within a dashboard run to avoid memory problems
            val count = (maxInt.toLong() - minInt.toLong() + 1).coerceAtMost(10000L).toInt()
            val list = mutableListOf<String>()
            for (i in 0 until count) {
                list.add(integerToIpv4(minInt + i))
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }

    private fun cleanIp(ip: String): String {
        var clean = ip.trim()
        if (clean.startsWith("[")) {
            val end = clean.indexOf("]")
            if (end != -1) {
                clean = clean.substring(1, end)
            }
        }
        return clean
    }

    private fun generateIpv4(ipStr: String, prefix: Int, maxSamples: Int): List<String> {
        val ips = mutableListOf<String>()
        try {
            val inetAddress = InetAddress.getByName(ipStr)
            val bytes = inetAddress.address
            if (bytes.size != 4) return emptyList()

            var ipInt = 0
            for (i in 0..3) {
                ipInt = (ipInt shl 8) or (bytes[i].toInt() and 0xFF)
            }

            val hostBits = 32 - prefix
            if (hostBits <= 0) {
                return listOf(ipStr)
            }

            // Align base address to network address using mask
            val mask = if (prefix == 0) 0 else { -1 shl hostBits }
            val baseIpInt = ipInt and mask

            val numAddresses = 1L shl hostBits
            if (numAddresses <= maxSamples) {
                for (i in 0 until numAddresses.toInt()) {
                    val currentIpInt = baseIpInt + i
                    ips.add(integerToIpv4(currentIpInt))
                }
            } else {
                val step = numAddresses / maxSamples
                for (i in 0 until maxSamples) {
                    val currentIpInt = baseIpInt + (i * step).toInt()
                    ips.add(integerToIpv4(currentIpInt))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ips
    }

    private fun integerToIpv4(ip: Int): String {
        return "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"
    }

    private fun generateIpv6(ipStr: String, prefix: Int, maxSamples: Int): List<String> {
        val ips = mutableListOf<String>()
        try {
            val address = InetAddress.getByName(cleanIp(ipStr))
            val bytes = address.address
            if (bytes.size != 16) return emptyList()

            // For simplicity and safety on Android, we sample IPv6 by taking the network base prefix
            // and generating random/step-based hosts in the lower bytes.
            val byteCountToModify = (128 - prefix) / 8
            val staticBytesCount = 16 - byteCountToModify

            if (byteCountToModify <= 0) {
                return listOf(ipStr)
            }

            // Generate sampleCount addresses by varying the mutable bytes
            for (i in 1..min(maxSamples, 100)) {
                val newBytes = bytes.copyOf()
                // Vary bytes deterministically or semi-randomly
                val factor = i * 2311
                for (b in 0 until byteCountToModify) {
                    val idx = staticBytesCount + b
                    if (idx in 0..15) {
                        newBytes[idx] = ((newBytes[idx].toInt() xor (factor ushr (b * 8))) and 0xFF).toByte()
                    }
                }
                try {
                    val sampledAddress = InetAddress.getByAddress(newBytes)
                    ips.add(sampledAddress.hostAddress ?: "")
                } catch (e: Exception) {
                    // Ignore invalid
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ips
    }
}
