package com.lightnet.ui

import androidx.compose.runtime.Composable

@Composable
expect fun BackHandlerCompat(onBack: () -> Unit)
