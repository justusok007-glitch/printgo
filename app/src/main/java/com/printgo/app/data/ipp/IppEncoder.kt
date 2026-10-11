package com.printgo.app.data.ipp

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object IppEncoder {

    fun encode(message: IppMessage): ByteArray {
        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // 1. Header (8 bytes)
        out.writeByte(message.versionMajor.toInt())
        out.writeByte(message.versionMinor.toInt())
        out.writeShort(message.code.toInt())
        out.writeInt(message.requestId)

        // 2. Attribute groups
        for (group in message.attributeGroups) {
            out.writeByte(group.tag.toInt())
            for (attr in group.attributes) {
                encodeAttribute(out, attr)
            }
        }

        // 3. End-of-attributes tag
        out.writeByte(IppTag.END_OF_ATTRIBUTES_TAG.toInt())
        out.flush()

        return baos.toByteArray()
    }

    private fun encodeAttribute(out: DataOutputStream, attr: IppAttribute) {
        val nameBytes = attr.name.toByteArray(Charsets.UTF_8)
        var isFirst = true

        for (value in attr.values) {
            val tag = getTagForValue(value, attr.tag)
            out.writeByte(tag.toInt())

            if (isFirst) {
                out.writeShort(nameBytes.size)
                out.write(nameBytes)
                isFirst = false
            } else {
                out.writeShort(0) // Multi-valued: 0-length name
            }

            encodeValueData(out, value)
        }
    }

    private fun encodeValueData(out: DataOutputStream, value: IppValue) {
        when (value) {
            is IppValue.IntValue -> {
                out.writeShort(4)
                out.writeInt(value.value)
            }
            is IppValue.BooleanValue -> {
                out.writeShort(1)
                out.writeByte(if (value.value) 1 else 0)
            }
            is IppValue.StringValue -> {
                val bytes = value.value.toByteArray(Charsets.UTF_8)
                out.writeShort(bytes.size)
                out.write(bytes)
            }
            is IppValue.ResolutionValue -> {
                out.writeShort(9)
                out.writeInt(value.xRes)
                out.writeInt(value.yRes)
                out.writeByte(value.units.toInt())
            }
            is IppValue.RangeOfIntValue -> {
                out.writeShort(8)
                out.writeInt(value.lower)
                out.writeInt(value.upper)
            }
            is IppValue.DateTimeValue -> {
                out.writeShort(value.bytes.size)
                out.write(value.bytes)
            }
            is IppValue.BinaryValue -> {
                out.writeShort(value.bytes.size)
                out.write(value.bytes)
            }
            is IppValue.OutOfBandValue -> {
                out.writeShort(0)
            }
            is IppValue.CollectionValue -> {
                out.writeShort(0) // begCollection value length is 0
                for ((memberName, memberValues) in value.members) {
                    val memberNameBytes = memberName.toByteArray(Charsets.UTF_8)
                    // Write memberAttrName
                    out.writeByte(IppTag.MEMBER_ATTR_NAME.toInt())
                    out.writeShort(0)
                    out.writeShort(memberNameBytes.size)
                    out.write(memberNameBytes)

                    for (subVal in memberValues) {
                        val subTag = getTagForValue(subVal, IppTag.KEYWORD)
                        out.writeByte(subTag.toInt())
                        out.writeShort(0)
                        encodeValueData(out, subVal)
                    }
                }
                // endCollection
                out.writeByte(IppTag.END_COLLECTION.toInt())
                out.writeShort(0)
                out.writeShort(0)
            }
        }
    }

    private fun getTagForValue(value: IppValue, defaultTag: Byte): Byte {
        return when (value) {
            is IppValue.IntValue -> IppTag.INTEGER
            is IppValue.BooleanValue -> IppTag.BOOLEAN
            is IppValue.StringValue -> value.tag
            is IppValue.ResolutionValue -> IppTag.RESOLUTION
            is IppValue.RangeOfIntValue -> IppTag.RANGE_OF_INTEGER
            is IppValue.DateTimeValue -> IppTag.DATE_TIME
            is IppValue.BinaryValue -> value.tag
            is IppValue.CollectionValue -> IppTag.BEG_COLLECTION
            is IppValue.OutOfBandValue -> value.tag
        }
    }

    fun createGetPrinterAttributesRequest(
        printerUri: String,
        requestId: Int = 1,
        requestedAttributes: List<String> = listOf("all")
    ): IppMessage {
        val opAttributes = mutableListOf(
            IppAttribute("attributes-charset", IppTag.CHARSET, listOf(IppValue.StringValue(IppTag.CHARSET, "utf-8"))),
            IppAttribute("attributes-natural-language", IppTag.NATURAL_LANGUAGE, listOf(IppValue.StringValue(IppTag.NATURAL_LANGUAGE, "en"))),
            IppAttribute("printer-uri", IppTag.URI, listOf(IppValue.StringValue(IppTag.URI, printerUri)))
        )

        if (requestedAttributes.isNotEmpty()) {
            val reqValues = requestedAttributes.map { IppValue.StringValue(IppTag.KEYWORD, it) }
            opAttributes.add(IppAttribute("requested-attributes", IppTag.KEYWORD, reqValues))
        }

        return IppMessage(
            versionMajor = 1,
            versionMinor = 1,
            code = IppMessage.OP_GET_PRINTER_ATTRIBUTES,
            requestId = requestId,
            attributeGroups = listOf(
                IppAttributeGroup(IppTag.OPERATION_ATTRIBUTES_TAG, opAttributes)
            )
        )
    }
}
