package com.lightnet.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

actual suspend fun resolveHostToIp(hostname: String): String? = withContext(Dispatchers.IO) {
    try {
        InetAddress.getByName(hostname).hostAddress
    } catch (_: Exception) {
        null
    }
}
