package com.printgo.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterProtocol

@Entity(tableName = "saved_printers")
data class SavedPrinterEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val connectionType: String,
    val protocol: String,
    val resourcePath: String,
    val make: String?,
    val model: String?,
    val supportedFormatsCsv: String,
    val supportsColor: Boolean,
    val supportsDuplex: Boolean,
    val isDefault: Boolean,
    val lastUsedAt: Long,
    val isManual: Boolean,
    val capabilitiesJson: String? = null,
    val capabilitiesUpdatedAt: Long = 0L,
    val tlsFingerprint: String? = null
) {
    fun toPrinter(isOnline: Boolean = true): Printer {
        val connType = try {
            ConnectionType.valueOf(connectionType)
        } catch (_: Exception) {
            ConnectionType.WIFI
        }
        val proto = try {
            PrinterProtocol.valueOf(protocol)
        } catch (_: Exception) {
            PrinterProtocol.IPP
        }
        val formats = if (supportedFormatsCsv.isNotBlank()) {
            supportedFormatsCsv.split(",").map { it.trim() }
        } else {
            listOf("application/pdf")
        }

        return Printer(
            id = id,
            name = name,
            host = host,
            port = port,
            connectionType = connType,
            protocol = proto,
            resourcePath = resourcePath,
            make = make,
            model = model,
            supportedFormats = formats,
            supportsColor = supportsColor,
            supportsDuplex = supportsDuplex,
            isOnline = isOnline,
            isDefault = isDefault,
            lastUsedAt = lastUsedAt,
            isManual = isManual
        )
    }

    companion object {
        fun fromPrinter(
            printer: Printer,
            capabilitiesJson: String? = null,
            capabilitiesUpdatedAt: Long = 0L,
            tlsFingerprint: String? = null
        ): SavedPrinterEntity {
            return SavedPrinterEntity(
                id = printer.id,
                name = printer.name,
                host = printer.host,
                port = printer.port,
                connectionType = printer.connectionType.name,
                protocol = printer.protocol.name,
                resourcePath = printer.resourcePath,
                make = printer.make,
                model = printer.model,
                supportedFormatsCsv = printer.supportedFormats.joinToString(","),
                supportsColor = printer.supportsColor,
                supportsDuplex = printer.supportsDuplex,
                isDefault = printer.isDefault,
                lastUsedAt = printer.lastUsedAt,
                isManual = printer.isManual,
                capabilitiesJson = capabilitiesJson,
                capabilitiesUpdatedAt = capabilitiesUpdatedAt,
                tlsFingerprint = tlsFingerprint
            )
        }
    }
}
