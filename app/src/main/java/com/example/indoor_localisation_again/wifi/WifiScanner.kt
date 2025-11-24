package com.example.indoor_localisation_again.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.indoor_localisation_again.model.AccessPointReading
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiScanner(private val context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun scanFreshReadings(): List<AccessPointReading> =
        suspendCancellableCoroutine { cont ->
            val now = SystemClock.elapsedRealtime()
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            val handler = Handler(Looper.getMainLooper())
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    context.unregisterReceiver(this)
                    val readings = wifiManager.scanResults
                        .filter { isFresh(it, SystemClock.elapsedRealtime()) }
                        .map { it.toReading(SystemClock.elapsedRealtime()) }
                    handler.removeCallbacksAndMessages(null)
                    cont.resume(readings)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            val started = wifiManager.startScan()
            if (!started) {
                context.unregisterReceiver(receiver)
                handler.removeCallbacksAndMessages(null)
                val readings = wifiManager.scanResults
                    .filter { isFresh(it, now) }
                    .map { it.toReading(now) }
                cont.resume(readings)
                return@suspendCancellableCoroutine
            }

            handler.postDelayed({
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                }
                if (cont.isActive) {
                    cont.resume(emptyList())
                }
            }, FRESH_THRESHOLD_MS + 1500)

            cont.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: IllegalArgumentException) {
                }
                handler.removeCallbacksAndMessages(null)
            }
        }

    private fun isFresh(result: ScanResult, nowMs: Long): Boolean {
        val readingMs = result.timestamp / 1000 // platform reports micros since boot
        val age = nowMs - readingMs
        return age in 0..FRESH_THRESHOLD_MS
    }

    private fun ScanResult.toReading(nowMs: Long): AccessPointReading {
        val readingMs = timestamp / 1000
        return AccessPointReading(
            bssid = BSSID.orEmpty(),
            ssid = SSID,
            rssi = level,
            frequency = frequency,
            ageMs = nowMs - readingMs
        )
    }

    companion object {
        const val FRESH_THRESHOLD_MS = 7_000L
    }
}
