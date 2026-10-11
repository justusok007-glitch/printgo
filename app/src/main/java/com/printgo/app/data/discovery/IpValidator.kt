package com.printgo.app.data.discovery

object IpValidator {

    private val IPV4_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    private val HOSTNAME_REGEX = Regex(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?$"
    )

    fun isValidIpOrHostname(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains(" ") || trimmed.contains("/")) return false

        val parts = trimmed.split(".")
        // If it consists of 4 digit-only parts, strictly validate as IPv4
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }) {
            return parts.all {
                val num = it.toIntOrNull() ?: return false
                num in 0..255 && (it == "0" || !it.startsWith("0"))
            }
        }

        // Hostnames must match syntax and the TLD cannot be purely numeric
        if (!HOSTNAME_REGEX.matches(trimmed)) return false
        if (parts.size > 1 && parts.last().all { it.isDigit() }) return false

        return true
    }

    fun isValidPort(port: Int): Boolean {
        return port in 1..65535
    }

    fun validate(hostInput: String, portInput: String): ValidationResult {
        val trimmedHost = hostInput.trim()
        if (trimmedHost.isEmpty()) {
            return ValidationResult.Error("IP address or hostname is required")
        }
        if (!isValidIpOrHostname(trimmedHost)) {
            return ValidationResult.Error("Invalid IP address or hostname (e.g. 192.168.1.100 or printer.local)")
        }

        val port = portInput.trim().toIntOrNull()
        if (port == null || !isValidPort(port)) {
            return ValidationResult.Error("Port must be between 1 and 65535 (default 631)")
        }

        return ValidationResult.Valid(trimmedHost, port)
    }

    sealed interface ValidationResult {
        data class Valid(val host: String, val port: Int) : ValidationResult
        data class Error(val message: String) : ValidationResult
    }
}
