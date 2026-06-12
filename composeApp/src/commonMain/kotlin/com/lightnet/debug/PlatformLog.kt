package com.lightnet.debug

/** Mirrors a debug log line to the platform's native log (e.g. Android logcat) for offline capture. */
expect fun platformLog(tag: String, message: String)
