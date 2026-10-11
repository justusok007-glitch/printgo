package com.printgo.app.data.print

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object PdfSaver {

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun sanitize(name: String): String {
        val base = name.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _()-]"), "_").trim().ifBlank { "PrintGo" }
        return base.take(80)
    }

    /** Copies [source] into the public Downloads folder (or app documents on Android 9). Returns the Uri to open. */
    fun saveToDownloads(context: Context, source: File, displayName: String): Uri {
        val fileName = sanitize(displayName) + ".pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PrintGo")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Could not create the file in Downloads.")
            try {
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                    ?: throw java.io.IOException("Could not write the file in Downloads.")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            dir.mkdirs()
            var target = File(dir, fileName)
            var n = 1
            while (target.exists()) target = File(dir, "${sanitize(displayName)} (${n++}).pdf")
            source.copyTo(target)
            FileProvider.getUriForFile(context, authority(context), target)
        }
    }

    fun shareUri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)
}
