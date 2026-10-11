package com.printgo.app.data.local

import com.printgo.app.model.Marker
import com.printgo.app.model.PaperSize
import com.printgo.app.model.PrinterCapabilities
import org.json.JSONArray
import org.json.JSONObject

object CapabilitiesJsonAdapter {

    fun toJson(capabilities: PrinterCapabilities): String {
        val root = JSONObject()
        root.put("makeAndModel", capabilities.makeAndModel)
        root.put("state", capabilities.state)
        root.put("stateReasons", JSONArray(capabilities.stateReasons))
        root.put("isAcceptingJobs", capabilities.isAcceptingJobs)
        root.put("mediaDefault", capabilities.mediaDefault ?: "")
        root.put("mediaReady", JSONArray(capabilities.mediaReady))
        root.put("sidesSupported", JSONArray(capabilities.sidesSupported))
        root.put("colorSupported", capabilities.colorSupported)
        root.put("colorModesSupported", JSONArray(capabilities.colorModesSupported))
        root.put("qualitiesSupported", JSONArray(capabilities.qualitiesSupported))
        root.put("resolutionsSupported", JSONArray(capabilities.resolutionsSupported))
        root.put("copiesMax", capabilities.copiesMax)
        root.put("orientationsSupported", JSONArray(capabilities.orientationsSupported))
        root.put("documentFormatsSupported", JSONArray(capabilities.documentFormatsSupported))
        root.put("pageRangesSupported", capabilities.pageRangesSupported)
        root.put("numberUpSupported", JSONArray(capabilities.numberUpSupported))
        root.put("printScalingSupported", JSONArray(capabilities.printScalingSupported))
        root.put("location", capabilities.location ?: "")
        root.put("infoUrl", capabilities.infoUrl ?: "")
        root.put("lastUpdated", capabilities.lastUpdated)

        val mediaArray = JSONArray()
        for (m in capabilities.mediaSupported) {
            val mo = JSONObject()
            mo.put("name", m.name)
            mo.put("w", m.widthMm)
            mo.put("h", m.heightMm)
            mediaArray.put(mo)
        }
        root.put("mediaSupported", mediaArray)

        val markerArray = JSONArray()
        for (marker in capabilities.markerLevels) {
            val mo = JSONObject()
            mo.put("name", marker.name)
            mo.put("color", marker.colorHex)
            mo.put("level", marker.levelPercent)
            mo.put("type", marker.type)
            markerArray.put(mo)
        }
        root.put("markerLevels", markerArray)

        return root.toString()
    }

    fun fromJson(jsonStr: String): PrinterCapabilities? {
        return try {
            val root = JSONObject(jsonStr)

            val makeAndModel = root.optString("makeAndModel", "Printer")
            val state = root.optString("state", "Idle")
            val stateReasons = jsonArrayToStringList(root.optJSONArray("stateReasons"))
            val isAcceptingJobs = root.optBoolean("isAcceptingJobs", true)
            val mediaDefault = root.optString("mediaDefault").ifBlank { null }
            val mediaReady = jsonArrayToStringList(root.optJSONArray("mediaReady"))
            val sidesSupported = jsonArrayToStringList(root.optJSONArray("sidesSupported")).ifEmpty { listOf("one-sided") }
            val colorSupported = root.optBoolean("colorSupported", true)
            val colorModesSupported = jsonArrayToStringList(root.optJSONArray("colorModesSupported"))
            val qualitiesSupported = jsonArrayToStringList(root.optJSONArray("qualitiesSupported"))
            val copiesMax = root.optInt("copiesMax", 99)
            val orientationsSupported = jsonArrayToStringList(root.optJSONArray("orientationsSupported"))
            val documentFormatsSupported = jsonArrayToStringList(root.optJSONArray("documentFormatsSupported"))
            val pageRangesSupported = root.optBoolean("pageRangesSupported", true)
            val printScalingSupported = jsonArrayToStringList(root.optJSONArray("printScalingSupported"))
            val location = root.optString("location").ifBlank { null }
            val infoUrl = root.optString("infoUrl").ifBlank { null }
            val lastUpdated = root.optLong("lastUpdated", System.currentTimeMillis())

            val mediaList = mutableListOf<PaperSize>()
            val mediaArray = root.optJSONArray("mediaSupported")
            if (mediaArray != null) {
                for (i in 0 until mediaArray.length()) {
                    val mo = mediaArray.getJSONObject(i)
                    mediaList.add(PaperSize(mo.getString("name"), mo.getInt("w"), mo.getInt("h")))
                }
            }

            val markerList = mutableListOf<Marker>()
            val markerArray = root.optJSONArray("markerLevels")
            if (markerArray != null) {
                for (i in 0 until markerArray.length()) {
                    val mo = markerArray.getJSONObject(i)
                    markerList.add(Marker(mo.getString("name"), mo.getString("color"), mo.getInt("level"), mo.getString("type")))
                }
            }

            val resolutions = mutableListOf<Int>()
            val resArray = root.optJSONArray("resolutionsSupported")
            if (resArray != null) {
                for (i in 0 until resArray.length()) {
                    resolutions.add(resArray.getInt(i))
                }
            }

            val numberUp = mutableListOf<Int>()
            val numUpArray = root.optJSONArray("numberUpSupported")
            if (numUpArray != null) {
                for (i in 0 until numUpArray.length()) {
                    numberUp.add(numUpArray.getInt(i))
                }
            }

            PrinterCapabilities(
                makeAndModel = makeAndModel,
                state = state,
                stateReasons = stateReasons,
                isAcceptingJobs = isAcceptingJobs,
                mediaSupported = mediaList,
                mediaDefault = mediaDefault,
                mediaReady = mediaReady,
                sidesSupported = sidesSupported,
                colorSupported = colorSupported,
                colorModesSupported = colorModesSupported,
                qualitiesSupported = qualitiesSupported,
                resolutionsSupported = resolutions.ifEmpty { listOf(300, 600) },
                copiesMax = copiesMax,
                orientationsSupported = orientationsSupported,
                documentFormatsSupported = documentFormatsSupported,
                pageRangesSupported = pageRangesSupported,
                numberUpSupported = numberUp.ifEmpty { listOf(1, 2, 4) },
                printScalingSupported = printScalingSupported,
                markerLevels = markerList,
                location = location,
                infoUrl = infoUrl,
                lastUpdated = lastUpdated
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            list.add(arr.getString(i))
        }
        return list
    }
}
