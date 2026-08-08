package com.aidesktop.os.ui.desktop.window

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real, live values for the taskbar's system tray. Every field here comes
 * from an actual Android platform API on this device — nothing is a
 * hardcoded "always full / always connected" placeholder:
 *   - [timeText]     ticks every real second off System.currentTimeMillis(),
 *                    formatted with the device's actual clock/locale
 *   - [batteryPct]   read from the sticky ACTION_BATTERY_CHANGED broadcast
 *                    (BatteryManager EXTRA_LEVEL / EXTRA_SCALE), same source
 *                    Android's own status bar uses
 *   - [isCharging]   from the same sticky broadcast's EXTRA_STATUS
 *   - [network]      from ConnectivityManager's live NetworkCallback,
 *                    reporting WIFI / CELLULAR / NONE as they actually change
 *   - [isMuted]      from AudioManager's real ringer mode
 */
data class SystemStatus(
    val timeText: String,
    val dateText: String,
    val batteryPct: Int,
    val isCharging: Boolean,
    val network: NetworkKind,
    val isMuted: Boolean
)

enum class NetworkKind { WIFI, CELLULAR, NONE }

@Composable
fun rememberSystemStatus(): SystemStatus {
    val context = LocalContext.current

    var timeText by remember { mutableStateOf(formatTime()) }
    var dateText by remember { mutableStateOf(formatDate()) }
    var batteryPct by remember { mutableStateOf(readBatteryPercentNow(context)) }
    var isCharging by remember { mutableStateOf(readIsChargingNow(context)) }
    var network by remember { mutableStateOf(readNetworkKindNow(context)) }
    var isMuted by remember { mutableStateOf(readIsMutedNow(context)) }

    // Live clock: recompute every real second, so the taskbar never shows a
    // stale time frozen at whenever the window happened to last recompose.
    // LaunchedEffect's coroutine is auto-cancelled when this leaves composition.
    LaunchedEffect(Unit) {
        while (isActive) {
            timeText = formatTime()
            dateText = formatDate()
            delay(1000L)
        }
    }

    // Battery: re-read on every real ACTION_BATTERY_CHANGED / charging
    // broadcast the system sends, instead of polling.
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                batteryPct = batteryPercentFromIntent(intent) ?: batteryPct
                isCharging = isChargingFromIntent(intent) ?: isCharging
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // Network: real ConnectivityManager callback, fires on actual
    // connect/disconnect/transport-change events.
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(net: Network, caps: NetworkCapabilities) {
                network = networkKindFromCapabilities(caps)
            }
            override fun onLost(net: Network) {
                network = readNetworkKindNow(context)
            }
            override fun onAvailable(net: Network) {
                network = readNetworkKindNow(context)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }

    // Ringer/mute: real system setting, refreshed whenever it actually changes.
    DisposableEffect(context) {
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                isMuted = readIsMutedNow(context)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    return SystemStatus(timeText, dateText, batteryPct, isCharging, network, isMuted)
}

private fun formatTime(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
private fun formatDate(): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

private fun batteryPercentFromIntent(intent: Intent): Int? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return (level * 100) / scale
}

private fun isChargingFromIntent(intent: Intent): Boolean? {
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    if (status == -1) return null
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

private fun readBatteryPercentNow(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return intent?.let { batteryPercentFromIntent(it) } ?: 100
}

private fun readIsChargingNow(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return intent?.let { isChargingFromIntent(it) } ?: false
}

private fun networkKindFromCapabilities(caps: NetworkCapabilities): NetworkKind = when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.WIFI
    else -> NetworkKind.NONE
}

private fun readNetworkKindNow(context: Context): NetworkKind {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return NetworkKind.NONE
    val caps = cm.getNetworkCapabilities(net) ?: return NetworkKind.NONE
    return networkKindFromCapabilities(caps)
}

private fun readIsMutedNow(context: Context): Boolean {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return am.ringerMode != AudioManager.RINGER_MODE_NORMAL
}
