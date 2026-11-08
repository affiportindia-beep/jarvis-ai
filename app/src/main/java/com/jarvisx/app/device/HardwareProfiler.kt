package com.jarvisx.app.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import com.jarvisx.core.model.DeviceProfile
import java.io.File

/** Reads RAM, CPU, storage, heat and battery. Everything is local; nothing is sent anywhere. */
class HardwareProfiler(private val context: Context) {

    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun profile(): DeviceProfile {
        val mem = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val cores = Runtime.getRuntime().availableProcessors()
        return DeviceProfile(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            socModel = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE,
            androidSdk = Build.VERSION.SDK_INT,
            totalRamMb = mem.totalMem / MB,
            availableRamMb = mem.availMem / MB,
            cpuCores = cores,
            performanceCores = performanceCores(cores),
            cpuFeatures = cpuFeatures(),
            freeStorageMb = freeStorageMb(),
            thermalStatus = thermalStatus(),
            batteryTempC = batteryTempC(),
        )
    }

    fun thermalStatus(): Int = runCatching { powerManager.currentThermalStatus }.getOrDefault(0)

    fun freeStorageMb(): Long = StatFs(context.filesDir.absolutePath).availableBytes / MB

    /** Proportional set size of this app, the RAM Android attributes to it (model pages included). */
    fun appPssMb(): Long = runCatching {
        activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0].totalPss.toLong() / 1024
    }.getOrDefault(0)

    fun batteryTempC(): Float? = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }

    fun batteryPercent(): Int? = batteryIntent()?.let {
        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    private fun batteryIntent(): Intent? =
        runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()

    /** Cores whose maximum frequency is within 80% of the fastest core. */
    private fun performanceCores(cores: Int): Int {
        val freqs = (0 until cores).mapNotNull { i ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrNull()
        }
        if (freqs.isEmpty()) return maxOf(2, cores / 2)
        val max = freqs.max()
        return freqs.count { it >= max * 0.8 }.coerceAtLeast(1)
    }

    private fun cpuFeatures(): Set<String> = runCatching {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith("Features") }
            ?.substringAfter(':')
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.toSet()
    }.getOrNull() ?: emptySet()

    companion object {
        private const val MB = 1024L * 1024L
    }
}
