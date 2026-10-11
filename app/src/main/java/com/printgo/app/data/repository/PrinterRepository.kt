package com.printgo.app.data.repository

import com.printgo.app.data.local.CapabilitiesJsonAdapter
import com.printgo.app.data.local.SavedPrinterDao
import com.printgo.app.data.local.SavedPrinterEntity
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PrinterRepository(private val dao: SavedPrinterDao) {

    val savedPrinters: Flow<List<Printer>> = dao.getAllPrinters().map { entities ->
        entities.map { it.toPrinter() }
    }

    suspend fun getPrinter(id: String): SavedPrinterEntity? {
        return dao.getPrinter(id)
    }

    suspend fun getDefaultPrinter(): Printer? {
        return dao.getDefaultPrinter()?.toPrinter()
    }

    suspend fun savePrinter(
        printer: Printer,
        capabilities: PrinterCapabilities? = null,
        tlsFingerprint: String? = null
    ) {
        val existing = dao.getPrinter(printer.id)
        val capJson = capabilities?.let { CapabilitiesJsonAdapter.toJson(it) } ?: existing?.capabilitiesJson
        val capTime = capabilities?.lastUpdated ?: existing?.capabilitiesUpdatedAt ?: 0L
        val finger = tlsFingerprint ?: existing?.tlsFingerprint

        dao.insertPrinter(
            SavedPrinterEntity.fromPrinter(
                printer = printer,
                capabilitiesJson = capJson,
                capabilitiesUpdatedAt = capTime,
                tlsFingerprint = finger
            )
        )
    }

    suspend fun deletePrinter(id: String) {
        dao.deletePrinter(id)
    }

    suspend fun setDefaultPrinter(id: String) {
        dao.clearDefaultFlags()
        dao.setDefault(id)
    }

    suspend fun renamePrinter(id: String, newName: String) {
        dao.renamePrinter(id, newName)
    }

    suspend fun updateLastUsed(id: String) {
        dao.updateLastUsed(id)
    }

    suspend fun updateResourcePath(id: String, newPath: String) {
        dao.updateResourcePath(id, newPath)
    }

    suspend fun updateTlsFingerprint(id: String, fingerprint: String) {
        dao.updateTlsFingerprint(id, fingerprint)
    }

    suspend fun saveCapabilities(id: String, capabilities: PrinterCapabilities) {
        val json = CapabilitiesJsonAdapter.toJson(capabilities)
        dao.updateCapabilities(id, json, capabilities.lastUpdated)
    }

    suspend fun loadCachedCapabilities(id: String): PrinterCapabilities? {
        val entity = dao.getPrinter(id) ?: return null
        val json = entity.capabilitiesJson ?: return null
        return CapabilitiesJsonAdapter.fromJson(json)
    }
}
