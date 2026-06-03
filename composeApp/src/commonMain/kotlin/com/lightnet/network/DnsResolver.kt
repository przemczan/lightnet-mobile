package com.lightnet.network

/** Resolves [hostname] to a dotted-decimal IP string, or null on failure / if already an IP. */
expect suspend fun resolveHostToIp(hostname: String): String?
