package com.lightnet.debug

import platform.Foundation.NSLog

actual fun platformLog(tag: String, message: String) {
    NSLog("[$tag] %s", message)
}
