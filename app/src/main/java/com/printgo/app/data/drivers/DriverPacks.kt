package com.printgo.app.data.drivers

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Driver data packs: printer descriptions for Gutenprint, HPLIP and SpliX. They hold data only (XML and
 * PPD files); the driver programs themselves are inside the app. A pack is downloaded once from the
 * project's GitHub release "driver-packs", or picked from a file, and checked against the SHA-256 the
 * app was built with before it is used.
 */
class DriverPacks(context: Context) {
    private val app = context.applicationContext

    data class Pack(
        val name: String,
        val file: String,
        val size: Long,
        val sha256: String,
        val version: String,
        val url: String
    ) {
        val label: String
            get() = when (name) {
                "gutenprint" -> "Gutenprint printers (Canon, Epson, HP, Lexmark and more)"
                "hplip" -> "HP printers (HPLIP)"
                "splix" -> "Samsung, Xerox and Dell lasers (SpliX)"
                else -> name
            }
        val sizeText: String get() = String.format(java.util.Locale.US, "%.1f MB", size / 1_048_576.0)
    }

    class PackException(message: String) : Exception(message)

    val packs: Map<String, Pack> by lazy { readManifest() }

    private fun readManifest(): Map<String, Pack> = try {
        val o = JSONObject(app.assets.open("drivers/packs.json").use { it.readBytes().decodeToString() })
        val p = o.getJSONObject("packs")
        p.keys().asSequence().associateWith { k ->
            val e = p.getJSONObject(k)
            Pack(k, e.getString("file"), e.getLong("size"), e.getString("sha256"), e.optString("version"), e.getString("url"))
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun root() = File(app.filesDir, "drivers").apply { mkdirs() }

    /** Folder of an installed pack (its files are under xml/ or ppd/). */
    fun dir(name: String): File = File(root(), name)

    private fun marker(name: String) = File(dir(name), ".installed")

    fun isInstalled(name: String): Boolean {
        val p = packs[name] ?: return false
        return try { marker(name).readText().trim() == p.sha256 } catch (_: Exception) { false }
    }

    /** True when a pack of an older app version is there but this app needs a newer one. */
    fun isOutdated(name: String): Boolean = !isInstalled(name) && marker(name).exists()

    fun installedSize(name: String): Long = dir(name).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun delete(name: String) {
        dir(name).deleteRecursively()
    }

    /** Downloads and installs a pack. [onProgress] gets 0..1. */
    suspend fun download(name: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val p = packs[name] ?: throw PackException("This driver pack is not part of this version of PrintGo.")
        val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true).followSslRedirects(true).build()
        val tmp = File(app.cacheDir, "pack_${p.file}.part")
        try {
            val resp = try {
                client.newCall(Request.Builder().url(p.url).build()).execute()
            } catch (e: Exception) {
                throw PackException("Could not reach the download server. Check the internet connection and try again.")
            }
            resp.use { r ->
                if (!r.isSuccessful) throw PackException(
                    if (r.code == 404) "The driver pack is not available for download yet. You can also install it from a file."
                    else "The download failed (error ${r.code}). Try again later."
                )
                val body = r.body ?: throw PackException("The download was empty.")
                val total = body.contentLength().takeIf { it > 0 } ?: p.size
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            install(p, tmp)
        } finally {
            tmp.delete()
        }
    }

    /** Installs a pack the user picked as a file (for example downloaded on a computer). Returns its name. */
    suspend fun installFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val tmp = File(app.cacheDir, "pack_import.zip")
        try {
            app.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw PackException("Could not open that file.")
            val digest = sha256(tmp)
            val p = packs.values.firstOrNull { it.sha256 == digest }
                ?: throw PackException("That file is not a driver pack for this version of PrintGo.")
            install(p, tmp)
            p.name
        } finally {
            tmp.delete()
        }
    }

    private fun install(p: Pack, zip: File) {
        val digest = sha256(zip)
        if (digest != p.sha256) throw PackException("The downloaded driver pack is damaged. Please try again.")
        val staging = File(root(), ".${p.name}.new")
        staging.deleteRecursively()
        staging.mkdirs()
        unzip(zip.inputStream(), staging)
        File(staging, ".installed").writeText(p.sha256)
        val target = dir(p.name)
        val old = File(root(), ".${p.name}.old")
        old.deleteRecursively()
        if (target.exists() && !target.renameTo(old)) target.deleteRecursively()
        if (!staging.renameTo(target)) throw PackException("Could not save the driver pack on this phone.")
        old.deleteRecursively()
    }

    companion object {
        fun sha256(f: File): String = f.inputStream().use { sha256(it) }

        fun sha256(input: InputStream): String {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Unzips into [dest], refusing entries that would land outside it. */
        fun unzip(input: InputStream, dest: File) {
            val base = dest.canonicalFile
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val out = File(base, e.name).canonicalFile
                    if (!out.path.startsWith(base.path + File.separator)) throw PackException("The driver pack is not valid.")
                    if (e.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { z.copyTo(it) }
                    }
                    z.closeEntry()
                }
            }
        }
    }
}
