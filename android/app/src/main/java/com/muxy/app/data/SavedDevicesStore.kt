package com.muxy.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SavedDevice(val name: String, val host: String, val port: Int) {
    val id: String get() = "$host:$port"
}

class SavedDevicesStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<SavedDevice> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SavedDevice.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun save(devices: List<SavedDevice>) {
        val raw = json.encodeToString(ListSerializer(SavedDevice.serializer()), devices)
        prefs.edit().putString(KEY, raw).apply()
    }

    companion object {
        private const val FILE_NAME = "muxy_devices"
        private const val KEY = "saved_devices"
    }
}
