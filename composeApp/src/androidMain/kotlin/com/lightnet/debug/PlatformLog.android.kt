package com.lightnet.debug

import android.util.Log

actual fun platformLog(tag: String, message: String) {
    Log.d("Lightnet/$tag", message)
}
