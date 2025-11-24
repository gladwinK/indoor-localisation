package com.example.indoor_localisation_again.data

import com.example.indoor_localisation_again.model.AccessPointReading
import org.json.JSONArray
import org.json.JSONObject

object AccessPointConverters {
    fun toJson(readings: List<AccessPointReading>): String {
        val array = JSONArray()
        readings.forEach { reading ->
            val obj = JSONObject()
            obj.put("bssid", reading.bssid)
            obj.put("ssid", reading.ssid)
            obj.put("rssi", reading.rssi)
            obj.put("frequency", reading.frequency)
            obj.put("ageMs", reading.ageMs)
            array.put(obj)
        }
        return array.toString()
    }

    fun fromJson(raw: String): List<AccessPointReading> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        val list = mutableListOf<AccessPointReading>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                AccessPointReading(
                    bssid = obj.optString("bssid"),
                    ssid = obj.optString("ssid"),
                    rssi = obj.optInt("rssi"),
                    frequency = obj.optInt("frequency"),
                    ageMs = obj.optLong("ageMs")
                )
            )
        }
        return list
    }
}
