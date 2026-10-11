package com.printgo.app.data.ipp

object IppTag {
    // Delimiter tags
    const val OPERATION_ATTRIBUTES_TAG: Byte = 0x01
    const val JOB_ATTRIBUTES_TAG: Byte = 0x02
    const val END_OF_ATTRIBUTES_TAG: Byte = 0x03
    const val PRINTER_ATTRIBUTES_TAG: Byte = 0x04
    const val UNSUPPORTED_ATTRIBUTES_TAG: Byte = 0x05

    // Out-of-band tags
    const val UNSUPPORTED: Byte = 0x10
    const val UNKNOWN: Byte = 0x12
    const val NO_VALUE: Byte = 0x13

    // Integer / Enum / Boolean
    const val INTEGER: Byte = 0x21
    const val BOOLEAN: Byte = 0x22
    const val ENUM: Byte = 0x23

    // OctetString / DateTime / Resolution / Range / Collection
    const val OCTET_STRING: Byte = 0x30
    const val DATE_TIME: Byte = 0x31
    const val RESOLUTION: Byte = 0x32
    const val RANGE_OF_INTEGER: Byte = 0x33
    const val BEG_COLLECTION: Byte = 0x34
    const val TEXT_WITH_LANGUAGE: Byte = 0x35
    const val NAME_WITH_LANGUAGE: Byte = 0x36
    const val END_COLLECTION: Byte = 0x37

    // String / Text / Name / Keywords / URIs
    const val TEXT_WITHOUT_LANGUAGE: Byte = 0x41
    const val NAME_WITHOUT_LANGUAGE: Byte = 0x42
    const val KEYWORD: Byte = 0x44
    const val URI: Byte = 0x45
    const val URI_SCHEME: Byte = 0x46
    const val CHARSET: Byte = 0x47
    const val NATURAL_LANGUAGE: Byte = 0x48
    const val MIME_MEDIA_TYPE: Byte = 0x49
    const val MEMBER_ATTR_NAME: Byte = 0x4A
}

sealed interface IppValue {
    data class IntValue(val value: Int) : IppValue
    data class BooleanValue(val value: Boolean) : IppValue
    data class StringValue(val tag: Byte, val value: String) : IppValue
    data class ResolutionValue(val xRes: Int, val yRes: Int, val units: Byte) : IppValue // 3 = dpi, 4 = dpcm
    data class RangeOfIntValue(val lower: Int, val upper: Int) : IppValue
    data class DateTimeValue(val bytes: ByteArray) : IppValue {
        override fun equals(other: Any?): Boolean = other is DateTimeValue && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
    data class BinaryValue(val tag: Byte, val bytes: ByteArray) : IppValue {
        override fun equals(other: Any?): Boolean = other is BinaryValue && tag == other.tag && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = 31 * tag + bytes.contentHashCode()
    }
    data class CollectionValue(val members: Map<String, List<IppValue>>) : IppValue
    data class OutOfBandValue(val tag: Byte) : IppValue
}

data class IppAttribute(
    val name: String,
    val tag: Byte,
    val values: List<IppValue>
) {
    fun asString(): String? = (values.firstOrNull() as? IppValue.StringValue)?.value
    fun asStrings(): List<String> = values.mapNotNull { (it as? IppValue.StringValue)?.value }
    fun asInt(): Int? = (values.firstOrNull() as? IppValue.IntValue)?.value
    fun asBoolean(): Boolean? = (values.firstOrNull() as? IppValue.BooleanValue)?.value
    fun asCollections(): List<Map<String, List<IppValue>>> = values.mapNotNull { (it as? IppValue.CollectionValue)?.members }
}

data class IppAttributeGroup(
    val tag: Byte,
    val attributes: List<IppAttribute>
) {
    fun get(name: String): IppAttribute? = attributes.firstOrNull { it.name == name }
}

data class IppMessage(
    val versionMajor: Byte = 1,
    val versionMinor: Byte = 1,
    val code: Short, // Operation ID for request, Status Code for response
    val requestId: Int = 1,
    val attributeGroups: List<IppAttributeGroup> = emptyList()
) {
    val isSuccess: Boolean
        get() = code.toInt() in 0x0000..0x00FF

    fun findAttribute(name: String): IppAttribute? {
        for (group in attributeGroups) {
            val attr = group.get(name)
            if (attr != null) return attr
        }
        return null
    }

    fun getString(name: String): String? = findAttribute(name)?.asString()
    fun getStrings(name: String): List<String> = findAttribute(name)?.asStrings() ?: emptyList()
    fun getInt(name: String): Int? = findAttribute(name)?.asInt()
    fun getBoolean(name: String): Boolean? = findAttribute(name)?.asBoolean()
    fun getCollections(name: String): List<Map<String, List<IppValue>>> = findAttribute(name)?.asCollections() ?: emptyList()

    companion object {
        const val OP_GET_PRINTER_ATTRIBUTES: Short = 0x000B

        // Standard IPP Status Codes
        const val STATUS_SUCCESSFUL_OK: Short = 0x0000
        const val STATUS_CLIENT_ERROR_NOT_FOUND: Short = 0x0406
    }
}
