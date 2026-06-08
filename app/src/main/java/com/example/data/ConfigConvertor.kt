package com.example.data

import android.net.Uri

object ConfigConvertor {

    data class ServerConfig(
        val type: String, // vles, vmess, trojan, ss
        val uuid: String,
        val address: String,
        val port: Int,
        val name: String,
        val sni: String = "cloudflare.com",
        val path: String = "/graphql",
        val host: String = "cloudflare.com"
    )

    fun convertToV2rayConfigs(ip: String, operator: String): List<String> {
        val cleanIp = ip.trim()
        val suffix = "$operator-LekScanner"
        
        val vlessTcp = "vless://937f2a1a-4de1-41db-b53e-2fa189a08f51@$cleanIp:443" +
                "?encryption=none" +
                "&security=tls" +
                "&sni=telewebion.com" +
                "&type=tcp" +
                "&headerType=none" +
                "#VLESS-TLS-$suffix"

        val vmessWs = "vmess://" + Base64Encoder.encode(
            """{
            "v": "2",
            "ps": "VMess-WS-$suffix",
            "add": "$cleanIp",
            "port": 443,
            "id": "e9b2c3a5-d850-482a-9e11-66def6a90ec4",
            "aid": 0,
            "scy": "auto",
            "net": "ws",
            "type": "none",
            "host": "speedtest.net",
            "path": "/graphql",
            "tls": "tls",
            "sni": "speedtest.net",
            "alpn": ""
        }""".trimIndent()
        )

        val trojanWs = "trojan://b772c3aa-9e32-47ef-b924-4f05cd1ef059@$cleanIp:443" +
                "?security=tls" +
                "&sni=divar.ir" +
                "&type=ws" +
                "&host=divar.ir" +
                "&path=%2F" +
                "#Trojan-WS-$suffix"

        val vlessReality = "vless://4cf5d3a2-2f3b-41ca-ab91-c89b3f309ff6@$cleanIp:443" +
                "?encryption=none" +
                "&security=reality" +
                "&sni=images.unsplash.com" +
                "&pubkey=38_c6f2_t-9bU87Z976G-Q_M7a8-gS7wPzNIsZ_g2W8" +
                "&fp=chrome" +
                "&type=tcp" +
                "#VLESS-Reality-$suffix"

        return listOf(vlessTcp, vmessWs, trojanWs, vlessReality)
    }

    /**
     * Parses a raw v2ray link and injects our healthy IP into it, returning the updated fully qualified config URI.
     */
    fun injectCleanIp(rawLink: String, ip: String, label: String = "LekScan"): String {
        val cleanIp = ip.trim()
        val trimmed = rawLink.trim()
        if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
            return try {
                val parts = trimmed.split("@")
                if (parts.size == 2) {
                    val prefix = parts[0]
                    val suffix = parts[1]
                    val serverAndParams = suffix.split(":")
                    if (serverAndParams.size >= 2) {
                        val rest = serverAndParams.subList(1, serverAndParams.size).joinToString(":")
                        val paramSplit = rest.split("?")
                        val portAndHash = paramSplit[0]
                        val port = portAndHash.split("#")[0]
                        
                        val params = if (paramSplit.size > 1) {
                            "?" + paramSplit[1].split("#")[0]
                        } else ""

                        val baseName = if (paramSplit.size > 1 && paramSplit[1].contains("#")) {
                            paramSplit[1].substringAfter("#")
                        } else if (portAndHash.contains("#")) {
                            portAndHash.substringAfter("#")
                        } else {
                            "Secured"
                        }
                        
                        // Strip out previous IP suffix/clean name
                        val cleanBase = baseName.split("-")[0].trim()

                        "$prefix@$cleanIp:$port$params#$cleanBase-$label-$cleanIp"
                    } else trimmed
                } else trimmed
            } catch (e: Exception) {
                trimmed
            }
        } else if (trimmed.startsWith("vmess://")) {
            return try {
                val base64Content = trimmed.substring(8)
                val json = Base64Encoder.decode(base64Content)
                
                var newJson = json
                if (json.contains("\"add\"")) {
                    newJson = newJson.replace(Regex("\"add\"\\s*:\\s*\"[^\"]+\""), "\"add\":\"$cleanIp\"")
                }
                
                if (newJson.contains("\"ps\"")) {
                    val oldPsMatch = Regex("\"ps\"\\s*:\\s*\"([^\"]+)\"").find(newJson)
                    val oldPs = oldPsMatch?.groupValues?.get(1) ?: "VMessConfig"
                    val cleanPs = oldPs.split("-")[0].trim()
                    newJson = newJson.replace(Regex("\"ps\"\\s*:\\s*\"[^\"]+\""), "\"ps\":\"$cleanPs-$label-$cleanIp\"")
                }
                
                "vmess://${Base64Encoder.encode(newJson)}"
            } catch (e: Exception) {
                trimmed
            }
        }
        return trimmed
    }

    /**
     * Multiplies a raw client configuration against a list of healthy IPs.
     */
    fun generateBulkConfigs(rawLink: String, ips: List<String>, label: String = "LekScan"): List<String> {
        val trimmed = rawLink.trim()
        if (trimmed.isEmpty() || ips.isEmpty()) return emptyList()
        return ips.map { ip ->
            injectCleanIp(trimmed, ip, label)
        }
    }
}

object Base64Encoder {
    fun encode(txt: String): String {
        return try {
            android.util.Base64.encodeToString(txt.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun decode(base64: String): String {
        return try {
            String(android.util.Base64.decode(base64, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
