package com.jarvisx.core.model

/** What the app knows about the phone. Filled by the Android hardware profiler. */
data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val socModel: String,
    val androidSdk: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val cpuCores: Int,
    val performanceCores: Int,
    val cpuFeatures: Set<String>,
    val freeStorageMb: Long,
    /** Android PowerManager thermal status: 0 none .. 6 shutdown. */
    val thermalStatus: Int,
    val batteryTempC: Float?,
) {
    val hasDotProd get() = "asimddp" in cpuFeatures || "dotprod" in cpuFeatures
    val hasI8mm get() = "i8mm" in cpuFeatures
}

data class Recommendation(val model: ModelSpec, val reason: String)

data class LoadCheck(val ok: Boolean, val reason: String)

/** Pure decision logic for model choice, threads and context size. */
object ModelSelector {

    /** RAM kept free for Android and other apps when deciding whether a model fits now. */
    const val SAFETY_MARGIN_MB = 500L

    fun recommend(profile: DeviceProfile, catalog: List<ModelSpec> = ModelCatalog.all): Recommendation {
        val candidates = catalog
            .filter { !it.experimental }
            .filter { profile.totalRamMb >= it.minTotalRamMb }
            .sortedByDescending { it.ramRequiredMb }
        val pick = candidates.firstOrNull() ?: catalog.minBy { it.ramRequiredMb }
        val gb = "%.1f".format(profile.totalRamMb / 1024.0)
        val reason = if (candidates.isEmpty()) {
            "Your phone has $gb GB RAM, below what JARVIS X is designed for, so the smallest model is suggested."
        } else {
            "Your phone has $gb GB RAM, so ${pick.displayName} is the strongest model that fits comfortably."
        }
        return Recommendation(pick, reason)
    }

    fun canLoadNow(model: ModelSpec, profile: DeviceProfile): LoadCheck {
        val needed = model.ramRequiredMb + SAFETY_MARGIN_MB
        return if (profile.availableRamMb >= needed) {
            LoadCheck(true, "${profile.availableRamMb} MB free, ${model.displayName} needs about ${model.ramRequiredMb} MB.")
        } else {
            LoadCheck(
                false,
                "Only ${profile.availableRamMb} MB RAM is free right now; ${model.displayName} needs about $needed MB " +
                    "including a safety margin. Close some apps or use a smaller model.",
            )
        }
    }

    /** Largest model from [downloaded] that fits in RAM right now, preferring [preferred]. */
    fun bestLoadable(preferred: ModelSpec?, downloaded: List<ModelSpec>, profile: DeviceProfile): ModelSpec? {
        if (preferred != null && preferred in downloaded && canLoadNow(preferred, profile).ok) return preferred
        return downloaded.filter { canLoadNow(it, profile).ok }.maxByOrNull { it.ramRequiredMb }
    }

    /** Threads = performance cores, reduced when the phone reports heat. */
    fun threads(profile: DeviceProfile): Int {
        val base = profile.performanceCores.coerceIn(2, 6)
        return when {
            profile.thermalStatus >= 3 -> maxOf(2, base / 2)
            profile.thermalStatus == 2 -> maxOf(2, base - 1)
            else -> base
        }
    }

    /**
     * 8192 tokens when RAM allows, else 4096, else 2048. Qwen3.5 keeps full attention in only a
     * quarter of its layers, so a bigger window costs little RAM and fits much longer chats.
     */
    fun contextSize(model: ModelSpec, profile: DeviceProfile): Int {
        val spare = profile.availableRamMb - model.ramRequiredMb - SAFETY_MARGIN_MB
        return when {
            spare >= 800 -> 8192
            spare >= 300 -> 4096
            else -> 2048
        }
    }

    /**
     * True when the next turn (prompt + longest possible answer) might not fit. Recurrent
     * models cannot drop old tokens, so the conversation restarts before that happens.
     */
    fun shouldRestartContext(used: Int, size: Int, nextTurnTokens: Int = 600): Boolean =
        size > 0 && used + nextTurnTokens >= size - 64
}
