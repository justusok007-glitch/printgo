package com.printgo.app.data.ipp

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream

object IppDecoder {

    fun decode(bytes: ByteArray): IppMessage {
        return decode(ByteArrayInputStream(bytes))
    }

    fun decode(inputStream: InputStream): IppMessage {
        val input = DataInputStream(inputStream)

        // 1. Header (8 bytes)
        val versionMajor = input.readByte()
        val versionMinor = input.readByte()
        val code = input.readShort()
        val requestId = input.readInt()

        val attributeGroups = mutableListOf<IppAttributeGroup>()
        var currentGroupTag: Byte? = null
        val currentGroupAttrs = mutableListOf<IppAttribute>()
        var currentAttrName: String = ""

        try {
            while (true) {
                val tag = input.readByte()
                if (tag == IppTag.END_OF_ATTRIBUTES_TAG) {
                    break
                }

                // Delimiter tag indicates start of a new group
                if (isDelimiterTag(tag)) {
                    if (currentGroupTag != null) {
                        attributeGroups.add(IppAttributeGroup(currentGroupTag, currentGroupAttrs.toList()))
                        currentGroupAttrs.clear()
                    }
                    currentGroupTag = tag
                    currentAttrName = ""
                    continue
                }

                // Read attribute name
                val nameLength = input.readUnsignedShort()
                val attrName = if (nameLength > 0) {
                    val nameBytes = ByteArray(nameLength)
                    input.readFully(nameBytes)
                    String(nameBytes, Charsets.UTF_8).also { currentAttrName = it }
                } else {
                    currentAttrName
                }

                // Read value
                val value = decodeValue(input, tag)

                // Check if this attribute already exists in current group (multi-valued attribute)
                val existingIndex = currentGroupAttrs.indexOfLast { it.name == attrName }
                if (existingIndex != -1 && nameLength == 0) {
                    val existing = currentGroupAttrs[existingIndex]
                    currentGroupAttrs[existingIndex] = existing.copy(values = existing.values + value)
                } else {
                    currentGroupAttrs.add(IppAttribute(attrName, tag, listOf(value)))
                }
            }
        } catch (_: EOFException) {
            // Reached stream end
        }

        if (currentGroupTag != null && currentGroupAttrs.isNotEmpty()) {
            attributeGroups.add(IppAttributeGroup(currentGroupTag, currentGroupAttrs.toList()))
        }

        return IppMessage(
            versionMajor = versionMajor,
            versionMinor = versionMinor,
            code = code,
            requestId = requestId,
            attributeGroups = attributeGroups
        )
    }

    private fun decodeValue(input: DataInputStream, tag: Byte): IppValue {
        val valueLength = input.readUnsignedShort()

        return when (tag) {
            IppTag.BEG_COLLECTION -> {
                // BegCollection has length 0, decode members until END_COLLECTION
                val members = mutableMapOf<String, MutableList<IppValue>>()
                var currentMemberName = ""

                while (true) {
                    val subTag = input.readByte()
                    if (subTag == IppTag.END_COLLECTION) {
                        input.readUnsignedShort() // name length (0)
                        input.readUnsignedShort() // value length (0)
                        break
                    }

                    if (subTag == IppTag.MEMBER_ATTR_NAME) {
                        input.readUnsignedShort() // name length (0)
                        val memberNameLen = input.readUnsignedShort()
                        val memberNameBytes = ByteArray(memberNameLen)
                        input.readFully(memberNameBytes)
                        currentMemberName = String(memberNameBytes, Charsets.UTF_8)
                    } else {
                        input.readUnsignedShort() // name length (0)
                        val subValue = decodeValue(input, subTag)
                        if (currentMemberName.isNotEmpty()) {
                            members.getOrPut(currentMemberName) { mutableListOf() }.add(subValue)
                        }
                    }
                }
                IppValue.CollectionValue(members)
            }
            IppTag.INTEGER, IppTag.ENUM -> {
                if (valueLength == 4) {
                    IppValue.IntValue(input.readInt())
                } else {
                    val bytes = ByteArray(valueLength)
                    input.readFully(bytes)
                    IppValue.BinaryValue(tag, bytes)
                }
            }
            IppTag.BOOLEAN -> {
                val b = if (valueLength > 0) input.readByte() != 0.toByte() else false
                IppValue.BooleanValue(b)
            }
            IppTag.RESOLUTION -> {
                if (valueLength == 9) {
                    val x = input.readInt()
                    val y = input.readInt()
                    val units = input.readByte()
                    IppValue.ResolutionValue(x, y, units)
                } else {
                    val bytes = ByteArray(valueLength)
                    input.readFully(bytes)
                    IppValue.BinaryValue(tag, bytes)
                }
            }
            IppTag.RANGE_OF_INTEGER -> {
                if (valueLength == 8) {
                    val lower = input.readInt()
                    val upper = input.readInt()
                    IppValue.RangeOfIntValue(lower, upper)
                } else {
                    val bytes = ByteArray(valueLength)
                    input.readFully(bytes)
                    IppValue.BinaryValue(tag, bytes)
                }
            }
            IppTag.DATE_TIME -> {
                val bytes = ByteArray(valueLength)
                input.readFully(bytes)
                IppValue.DateTimeValue(bytes)
            }
            IppTag.UNSUPPORTED, IppTag.UNKNOWN, IppTag.NO_VALUE -> {
                if (valueLength > 0) input.skipBytes(valueLength)
                IppValue.OutOfBandValue(tag)
            }
            // String types
            IppTag.TEXT_WITHOUT_LANGUAGE,
            IppTag.NAME_WITHOUT_LANGUAGE,
            IppTag.TEXT_WITH_LANGUAGE,
            IppTag.NAME_WITH_LANGUAGE,
            IppTag.KEYWORD,
            IppTag.URI,
            IppTag.URI_SCHEME,
            IppTag.CHARSET,
            IppTag.NATURAL_LANGUAGE,
            IppTag.MIME_MEDIA_TYPE -> {
                val bytes = ByteArray(valueLength)
                input.readFully(bytes)
                IppValue.StringValue(tag, String(bytes, Charsets.UTF_8))
            }
            else -> {
                val bytes = ByteArray(valueLength)
                input.readFully(bytes)
                IppValue.BinaryValue(tag, bytes)
            }
        }
    }

    private fun isDelimiterTag(tag: Byte): Boolean {
        return tag in 0x01..0x0F && tag != IppTag.END_OF_ATTRIBUTES_TAG
    }
}
