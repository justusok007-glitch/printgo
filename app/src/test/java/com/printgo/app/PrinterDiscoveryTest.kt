package com.printgo.app

import com.printgo.app.data.discovery.IpValidator
import com.printgo.app.data.discovery.TxtRecordParser
import com.printgo.app.model.ConnectionType
import com.printgo.app.model.Printer
import com.printgo.app.model.PrinterBrandDetector
import com.printgo.app.model.PrinterProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterDiscoveryTest {

    @Test
    fun `TxtRecordParser correctly parses standard mDNS TXT attributes`() {
        val attributes = mapOf(
            "ty" to "HP Color LaserJet Pro M479fdw",
            "usb_mfg" to "HP",
            "usb_mdl" to "Color LaserJet Pro M479fdw",
            "rp" to "ipp/printer",
            "pdl" to "application/pdf,image/urf,image/jpeg",
            "color" to "T",
            "duplex" to "T",
            "note" to "2nd Floor Office"
        )

        val parsed = TxtRecordParser.parseStringMap(attributes)

        assertEquals("HP", parsed.make)
        assertEquals("Color LaserJet Pro M479fdw", parsed.model)
        assertEquals("ipp/printer", parsed.resourcePath)
        assertEquals(listOf("application/pdf", "image/urf", "image/jpeg"), parsed.supportedFormats)
        assertTrue(parsed.supportsColor)
        assertTrue(parsed.supportsDuplex)
        assertEquals("2nd Floor Office", parsed.note)
    }

    @Test
    fun `TxtRecordParser handles missing attributes with sensible defaults`() {
        val emptyAttributes = emptyMap<String, String>()
        val parsed = TxtRecordParser.parseStringMap(emptyAttributes)

        assertEquals("ipp/print", parsed.resourcePath)
        assertEquals(listOf("application/pdf"), parsed.supportedFormats)
        assertFalse(parsed.supportsColor)
        assertFalse(parsed.supportsDuplex)
    }

    @Test
    fun `PrinterBrandDetector identifies major printer brands`() {
        val hpBrand = PrinterBrandDetector.detect("HP", "LaserJet Pro", "HP LaserJet")
        assertEquals("HP", hpBrand.displayName)
        assertEquals("HP", hpBrand.initials)

        val canonBrand = PrinterBrandDetector.detect(null, "Canon imageRUNNER", "Office Canon")
        assertEquals("Canon", canonBrand.displayName)
        assertEquals("CN", canonBrand.initials)

        val epsonBrand = PrinterBrandDetector.detect("Epson", "EcoTank ET-4760", null)
        assertEquals("Epson", epsonBrand.displayName)
        assertEquals("EP", epsonBrand.initials)

        val brotherBrand = PrinterBrandDetector.detect("Brother", "HL-L2350DW", null)
        assertEquals("Brother", brotherBrand.displayName)
        assertEquals("BR", brotherBrand.initials)

        val genericBrand = PrinterBrandDetector.detect("CustomCorp", "ThermalX", "TicketPrinter")
        assertEquals("Generic", genericBrand.displayName)
        assertEquals("CU", genericBrand.initials)
    }

    @Test
    fun `IpValidator validates IPv4 addresses and hostnames`() {
        // Valid IPv4
        assertTrue(IpValidator.isValidIpOrHostname("192.168.1.100"))
        assertTrue(IpValidator.isValidIpOrHostname("10.0.0.1"))
        assertTrue(IpValidator.isValidIpOrHostname("172.16.254.1"))

        // Invalid IPv4
        assertFalse(IpValidator.isValidIpOrHostname("999.999.999.999"))
        assertFalse(IpValidator.isValidIpOrHostname("192.168.1.256"))
        assertFalse(IpValidator.isValidIpOrHostname("192.168.1"))

        // Valid hostnames
        assertTrue(IpValidator.isValidIpOrHostname("printer.local"))
        assertTrue(IpValidator.isValidIpOrHostname("office-printer.lan"))
        assertTrue(IpValidator.isValidIpOrHostname("hp-laserjet"))

        // Invalid hostnames
        assertFalse(IpValidator.isValidIpOrHostname("printer with space"))
        assertFalse(IpValidator.isValidIpOrHostname("printer/slash"))
        assertFalse(IpValidator.isValidIpOrHostname(""))
    }

    @Test
    fun `IpValidator validates ports correctly`() {
        assertTrue(IpValidator.isValidPort(631))
        assertTrue(IpValidator.isValidPort(9100))
        assertTrue(IpValidator.isValidPort(80))

        assertFalse(IpValidator.isValidPort(0))
        assertFalse(IpValidator.isValidPort(-1))
        assertFalse(IpValidator.isValidPort(70000))
    }

    @Test
    fun `IpValidator validate helper returns structured result`() {
        val valid = IpValidator.validate("192.168.1.50", "631")
        assertTrue(valid is IpValidator.ValidationResult.Valid)
        assertEquals("192.168.1.50", (valid as IpValidator.ValidationResult.Valid).host)
        assertEquals(631, valid.port)

        val invalidHost = IpValidator.validate("bad host name", "631")
        assertTrue(invalidHost is IpValidator.ValidationResult.Error)

        val invalidPort = IpValidator.validate("192.168.1.50", "99999")
        assertTrue(invalidPort is IpValidator.ValidationResult.Error)
    }

    @Test
    fun `Printer de-duplication prefers secure IPPS protocol for same host and name`() {
        val printerIpp = Printer(
            id = "wifi_192.168.1.100_631_HP",
            name = "HP LaserJet",
            host = "192.168.1.100",
            port = 631,
            connectionType = ConnectionType.WIFI,
            protocol = PrinterProtocol.IPP
        )

        val printerIpps = Printer(
            id = "wifi_192.168.1.100_631_HP_Secure",
            name = "HP LaserJet",
            host = "192.168.1.100",
            port = 631,
            connectionType = ConnectionType.WIFI,
            protocol = PrinterProtocol.IPPS
        )

        val map = mutableMapOf<String, Printer>()
        val dedupeKey = "${printerIpp.host}_${printerIpp.name.lowercase()}"

        // Add plain IPP first
        map[dedupeKey] = printerIpp
        assertEquals(PrinterProtocol.IPP, map[dedupeKey]?.protocol)

        // Upgrade to IPPS when discovered
        val existing = map[dedupeKey]
        if (existing == null || (existing.protocol != PrinterProtocol.IPPS && printerIpps.protocol == PrinterProtocol.IPPS)) {
            map[dedupeKey] = printerIpps
        }

        assertEquals(1, map.size)
        assertEquals(PrinterProtocol.IPPS, map[dedupeKey]?.protocol)
    }
}
