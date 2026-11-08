package com.jarvisx.app.models

import android.content.Context
import com.jarvisx.core.model.ModelCatalog
import com.jarvisx.core.model.ModelSpec
import java.io.File

/**
 * Where model files live: app-private storage (`files/models`), never shared with other apps.
 * A model counts as installed only when its file AND its verified SHA-256 marker exist.
 */
class ModelStore(context: Context) {
    val dir: File = File(context.filesDir, "models").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("models", Context.MODE_PRIVATE)

    fun file(spec: ModelSpec) = File(dir, spec.fileName)
    fun partFile(spec: ModelSpec) = File(dir, spec.fileName + ".part")
    fun hashFile(spec: ModelSpec) = File(dir, spec.fileName + ".sha256")

    fun isInstalled(spec: ModelSpec): Boolean = file(spec).isFile && hashFile(spec).isFile

    fun installed(): List<ModelSpec> = ModelCatalog.all.filter { isInstalled(it) }

    fun sizeOnDiskMb(spec: ModelSpec): Long =
        (file(spec).takeIf { it.isFile }?.length() ?: partFile(spec).takeIf { it.isFile }?.length() ?: 0L) / (1024 * 1024)

    fun verifiedHash(spec: ModelSpec): String? = hashFile(spec).takeIf { it.isFile }?.readText()?.trim()

    fun delete(spec: ModelSpec) {
        file(spec).delete()
        partFile(spec).delete()
        hashFile(spec).delete()
        if (selectedId == spec.id) selectedId = null
    }

    /** The model the user chose; null means "use the recommendation". */
    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(value) { prefs.edit().putString("selected", value).apply() }
}
