package com.printgo.app.data.drivers

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Runs a driver program (lib/<abi>/libpg_*.so inside the app): PrintGo writes pages to its input and the
 * printer's own data comes back in [Result.output]. Each job runs in its own process, so a driver that
 * crashes cannot take the app with it.
 */
object DriverRunner {

    data class Result(val exitCode: Int, val messages: String) {
        val ok get() = exitCode == 0
        /** The driver's own explanation of a failure (lines starting with ERROR:), if any. */
        val error: String? get() = messages.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
    }

    fun program(context: Context, name: String): File = File(context.applicationInfo.nativeLibraryDir, "lib$name.so")

    fun isAvailable(context: Context, name: String): Boolean = program(context, name).let { it.isFile && it.canExecute() }

    suspend fun run(
        context: Context,
        name: String,
        args: List<String>,
        output: File,
        env: Map<String, String> = emptyMap(),
        timeoutMinutes: Long = 15,
        writeInput: (OutputStream) -> Unit
    ): Result = withContext(Dispatchers.IO) {
        val exe = program(context, name)
        if (!exe.isFile) throw IOException("The printer driver is missing from this copy of PrintGo.")
        val tmp = context.cacheDir
        val pb = ProcessBuilder(listOf(exe.absolutePath) + args)
            .redirectOutput(output)
            .redirectErrorStream(false)
        pb.environment().apply {
            put("TMPDIR", tmp.absolutePath)
            put("HOME", tmp.absolutePath)
            putAll(env)
        }
        val proc = pb.start()
        val log = StringBuilder()
        val errReader = Thread {
            try {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(log) {
                        log.append(line).append('\n')
                        if (log.length > 32_000) log.delete(0, log.length - 16_000)
                    }
                }
            } catch (_: IOException) { }
        }.apply { isDaemon = true; start() }
        try {
            try {
                proc.outputStream.buffered(256 * 1024).use { writeInput(it) }
            } catch (e: IOException) {
                // The driver stopped reading (usually because it failed); its exit code and messages say why.
            }
            currentCoroutineContext().ensureActive()
            val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(timeoutMinutes)
            while (!proc.waitFor(500, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
                if (System.currentTimeMillis() > deadline) {
                    proc.destroy()
                    throw IOException("The printer driver took too long and was stopped.")
                }
            }
            errReader.join(2000)
            Result(proc.exitValue(), synchronized(log) { log.toString() })
        } catch (e: CancellationException) {
            proc.destroy()
            throw e
        } finally {
            if (proc.isAlive) proc.destroyForcibly()
        }
    }
}
