package com.example.geminifnanodemo.core.common

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

private const val MEDIAPIPE_MIN_RAM_BYTES = 4L * 1024 * 1024 * 1024 // ~4GB

/**
 * Vive en `core:common` (no en `data:repositories`, como decía el plan
 * original) porque tanto `data:mediapipe` como `data:cloudgemini` la llaman
 * directamente desde su propio `checkAvailability()` — ponerla en
 * `data:repositories` habría creado una dependencia circular (esos módulos ya
 * son consumidos POR `data:repositories`).
 */
fun hasEnoughRamForMediaPipe(context: Context): Boolean {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return !activityManager.isLowRamDevice && memoryInfo.totalMem >= MEDIAPIPE_MIN_RAM_BYTES
}

/** RAM total del dispositivo en GB, para mostrar en la pantalla de Dispositivo. */
fun totalRamGb(context: Context): Double {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return memoryInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
}

/** Comprueba conectividad real (no solo "hay wifi/datos") antes de intentar el Tier C. */
fun hasInternetConnection(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
