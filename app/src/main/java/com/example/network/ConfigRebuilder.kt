package com.example.network

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

object ConfigRebuilder {

    fun modifyUriWithCleanIp(uri: String, cleanIp: String, cleanPort: Int? = null): String? {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return null

        return when {
            trimmed.startsWith("vmess://") -> {
                modifyVmess(trimmed, cleanIp, cleanPort)
            }
            trimmed.startsWith("vless://") || trimmed.startsWith("trojan://") || trimmed.startsWith("ss://") -> {
                modifyStandardUri(trimmed, cleanIp, cleanPort)
            }
            else -> null
        }
    }

    private fun modifyStandardUri(uriStr: String, cleanIp: String, cleanPort: Int?): String? {
        try {
            // Because Android's URI parser can fail with custom schemes like vless://, we do hand-parsing
            val schemeIndex = uriStr.indexOf("://")
            if (schemeIndex == -1) return null
            val scheme = uriStr.substring(0, schemeIndex)
            val rest = uriStr.substring(schemeIndex + 3)

            // Split credentials and host
            val atIndex = rest.indexOf("@")
            if (atIndex == -1) return null
            val credentials = rest.substring(0, atIndex)
            val endpointAndParams = rest.substring(atIndex + 1)

            // Separate endpoint (host:port) from query and fragment
            val queryIndex = endpointAndParams.indexOf("?")
            val fragmentIndex = endpointAndParams.indexOf("#")

            val endOfEndpoint = when {
                queryIndex != -1 && fragmentIndex != -1 -> minOf(queryIndex, fragmentIndex)
                queryIndex != -1 -> queryIndex
                fragmentIndex != -1 -> fragmentIndex
                else -> endpointAndParams.length
            }

            val endpoint = endpointAndParams.substring(0, endOfEndpoint)
            var queryAndFragment = endpointAndParams.substring(endOfEndpoint)

            // Extract old host and port
            var oldHost = endpoint
            var oldPort = "443"
            if (endpoint.startsWith("[")) {
                val bracketEnd = endpoint.indexOf("]")
                if (bracketEnd != -1) {
                    oldHost = endpoint.substring(1, bracketEnd)
                    val portPart = endpoint.substring(bracketEnd + 1)
                    if (portPart.startsWith(":")) {
                        oldPort = portPart.substring(1)
                    }
                }
            } else if (endpoint.contains(":")) {
                val parts = endpoint.split(":")
                oldHost = parts[0]
                oldPort = parts[1]
            }

            val finalPort = cleanPort?.toString() ?: oldPort
            val finalCleanIpVal = if (cleanIp.contains(":") && !cleanIp.startsWith("[")) {
                "[$cleanIp]"
            } else cleanIp

            val newEndpoint = "$finalCleanIpVal:$finalPort"

            // Parse and modify query parameters
            var queryStr = ""
            var fragmentStr = ""
            if (queryAndFragment.startsWith("?")) {
                val nextHash = queryAndFragment.indexOf("#")
                if (nextHash != -1) {
                    queryStr = queryAndFragment.substring(1, nextHash)
                    fragmentStr = queryAndFragment.substring(nextHash)
                } else {
                    queryStr = queryAndFragment.substring(1)
                }
            } else if (queryAndFragment.startsWith("#")) {
                fragmentStr = queryAndFragment
            }

            val params = parseQueryParams(queryStr).toMutableMap()
            if (!params.containsKey("sni")) {
                params["sni"] = oldHost
            }
            if (!params.containsKey("host")) {
                params["host"] = oldHost
            }

            val newQueryStr = buildQueryString(params)
            val finalQueryPart = if (newQueryStr.isNotEmpty()) "?$newQueryStr" else ""

            return "$scheme://$credentials@$newEndpoint$finalQueryPart$fragmentStr"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun modifyVmess(uriStr: String, cleanIp: String, cleanPort: Int?): String? {
        try {
            val base64Part = uriStr.substring(8).trim()
            val decodedBytes = Base64.decode(base64Part, Base64.DEFAULT)
            val decodedString = String(decodedBytes, Charsets.UTF_8)

            val json = JSONObject(decodedString)
            val oldHost = json.optString("add", "")
            val oldPort = json.optInt("port", 443)

            json.put("add", cleanIp)
            if (cleanPort != null) {
                json.put("port", cleanPort)
            }

            // Keep the old hostname as SNI and Host if not already set
            if (!json.has("sni") || json.optString("sni").isEmpty()) {
                json.put("sni", oldHost)
            }
            if (!json.has("host") || json.optString("host").isEmpty()) {
                json.put("host", oldHost)
            }

            val modifiedJsonStr = json.toString()
            val encodedBase64 = Base64.encodeToString(modifiedJsonStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            return "vmess://$encodedBase64"
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = URLDecoder.decode(parts[0], "UTF-8")
                val value = URLDecoder.decode(parts[1], "UTF-8")
                result[key] = value
            } else if (parts.size == 1) {
                val key = URLDecoder.decode(parts[0], "UTF-8")
                result[key] = ""
            }
        }
        return result
    }

    private fun buildQueryString(params: Map<String, String>): String {
        return params.map { (key, value) ->
            URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
        }.joinToString("&")
    }
}
