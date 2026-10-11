package com.printgo.app.data.ipp

import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed interface IppResult {
    data class Success(
        val response: IppMessage,
        val responseTimeMs: Long,
        val effectivePath: String
    ) : IppResult

    data class HttpError(val statusCode: Int, val message: String) : IppResult
    data class IppStatusError(val statusCode: Int, val message: String) : IppResult
    data class NetworkError(val message: String, val cause: Throwable? = null) : IppResult
    data class Timeout(val message: String) : IppResult
    data class TlsUntrusted(val fingerprint: String, val host: String, val port: Int) : IppResult
}

class IppClient {

    private val mediaTypeIpp = "application/ipp".toMediaType()

    suspend fun getPrinterAttributes(
        printer: Printer,
        pinnedFingerprint: String? = null
    ): IppResult = withContext(Dispatchers.IO) {
        val pathsToTry = mutableListOf<String>()
        val initialPath = printer.resourcePath.trim().removePrefix("/").ifBlank { "ipp/print" }
        pathsToTry.add(initialPath)

        val fallbackPaths = listOf("ipp/print", "ipp/printer", "ipp", "printers/ipp", "")
        for (fb in fallbackPaths) {
            if (!pathsToTry.contains(fb)) {
                pathsToTry.add(fb)
            }
        }

        var lastError: IppResult = IppResult.NetworkError("Failed to connect to printer")

        for (path in pathsToTry) {
            val result = executeGetAttributesWithPath(printer, path, pinnedFingerprint)
            when (result) {
                is IppResult.Success -> return@withContext result
                is IppResult.TlsUntrusted -> return@withContext result // Stop fallback on untrusted TLS so user can trust it
                is IppResult.HttpError -> {
                    lastError = result
                    if (result.statusCode != 404 && result.statusCode != 400) {
                        // If not a path-related error, don't keep trying paths
                        break
                    }
                }
                is IppResult.IppStatusError -> {
                    lastError = result
                    if (result.statusCode != IppMessage.STATUS_CLIENT_ERROR_NOT_FOUND.toInt()) {
                        break
                    }
                }
                else -> {
                    lastError = result
                }
            }
        }

        lastError
    }

    private suspend fun executeGetAttributesWithPath(
        printer: Printer,
        path: String,
        pinnedFingerprint: String?
    ): IppResult {
        val isHttps = printer.protocol == PrinterProtocol.IPPS
        val scheme = if (isHttps) "https" else "http"
        val port = if (printer.port > 0) printer.port else 631
        val cleanPath = path.removePrefix("/")
        val url = "$scheme://${printer.host}:$port/$cleanPath"
        val ippUri = "ipp://${printer.host}:$port/$cleanPath"

        val requestMessage = IppEncoder.createGetPrinterAttributesRequest(
            printerUri = ippUri,
            requestId = 1,
            requestedAttributes = listOf("all", "media-col-database")
        )
        val requestBytes = IppEncoder.encode(requestMessage)

        var detectedFingerprint: String? = null

        val okHttpClient = createOkHttpClient(
            printer = printer,
            pinnedFingerprint = pinnedFingerprint,
            onFingerprintCaptured = { detectedFingerprint = it }
        )

        val request = Request.Builder()
            .url(url)
            .post(requestBytes.toRequestBody(mediaTypeIpp))
            .header("Content-Type", "application/ipp")
            .build()

        var attempts = 0
        val maxAttempts = 3
        var backoffMs = 500L

        while (attempts < maxAttempts) {
            attempts++
            val startTime = System.currentTimeMillis()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val responseTime = System.currentTimeMillis() - startTime
                    if (!response.isSuccessful) {
                        return IppResult.HttpError(response.code, "HTTP ${response.code}: ${response.message}")
                    }

                    val body = response.body ?: return IppResult.NetworkError("Empty response from printer")
                    val responseBytes = body.bytes()

                    val ippResponse = try {
                        IppDecoder.decode(responseBytes)
                    } catch (e: Exception) {
                        return IppResult.NetworkError("Invalid IPP response payload: ${e.message}", e)
                    }

                    if (!ippResponse.isSuccess) {
                        return IppResult.IppStatusError(
                            ippResponse.code.toInt(),
                            "Printer reported status code: 0x${Integer.toHexString(ippResponse.code.toInt())}"
                        )
                    }

                    return IppResult.Success(
                        response = ippResponse,
                        responseTimeMs = responseTime,
                        effectivePath = cleanPath
                    )
                }
            } catch (e: SocketTimeoutException) {
                if (attempts >= maxAttempts) {
                    return IppResult.Timeout("Connection to printer timed out (${e.message})")
                }
            } catch (e: SSLHandshakeException) {
                if (detectedFingerprint != null) {
                    return IppResult.TlsUntrusted(detectedFingerprint!!, printer.host, port)
                }
                return IppResult.NetworkError("SSL Handshake failed: ${e.message}", e)
            } catch (e: SSLPeerUnverifiedException) {
                if (detectedFingerprint != null) {
                    return IppResult.TlsUntrusted(detectedFingerprint!!, printer.host, port)
                }
                return IppResult.NetworkError("TLS certificate unverified: ${e.message}", e)
            } catch (e: IOException) {
                if (attempts >= maxAttempts) {
                    return IppResult.NetworkError(e.message ?: "Network error while communicating with printer", e)
                }
            } catch (e: Exception) {
                return IppResult.NetworkError(e.message ?: "Unexpected error", e)
            }

            delay(backoffMs)
            backoffMs *= 2
        }

        return IppResult.NetworkError("Max retries exceeded")
    }

    internal fun createOkHttpClient(
        printer: Printer,
        pinnedFingerprint: String?,
        onFingerprintCaptured: (String) -> Unit
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)

        if (printer.protocol == PrinterProtocol.IPPS) {
            val isPrivateLan = isPrivateLanAddress(printer.host)

            if (isPrivateLan) {
                // Trust-on-first-use certificate handling for private LAN
                val trustManager = object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                        val serverCert = chain?.firstOrNull() ?: throw CertificateException("No certificate received")
                        val fingerprint = computeSha256Fingerprint(serverCert)
                        onFingerprintCaptured(fingerprint)

                        if (pinnedFingerprint != null) {
                            if (!fingerprint.equals(pinnedFingerprint, ignoreCase = true)) {
                                throw CertificateException("Certificate fingerprint mismatch. Expected $pinnedFingerprint, got $fingerprint")
                            }
                        } else {
                            // First time connect on private LAN: reject handshake so we trigger the trust dialog
                            throw CertificateException("Untrusted certificate for private printer")
                        }
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }

                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
                }

                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true } // Pinned certificate fingerprint provides cryptographic identity on LAN
            }
        }

        return builder.build()
    }

    companion object {
        fun isPrivateLanAddress(host: String): Boolean {
            val h = host.trim().lowercase()
            if (h == "localhost" || h.endsWith(".local") || h.endsWith(".lan")) return true

            // Link-local
            if (h.startsWith("169.254.")) return true

            // Class A: 10.0.0.0 - 10.255.255.255
            if (h.startsWith("10.")) return true

            // Class C: 192.168.0.0 - 192.168.255.255
            if (h.startsWith("192.168.")) return true

            // Class B: 172.16.0.0 - 172.31.255.255
            if (h.startsWith("172.")) {
                val secondOctet = h.removePrefix("172.").substringBefore(".").toIntOrNull()
                if (secondOctet != null && secondOctet in 16..31) return true
            }

            return false
        }

        fun computeSha256Fingerprint(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(certificate.encoded)
            return hash.joinToString(":") { "%02X".format(it) }
        }
    }
}
