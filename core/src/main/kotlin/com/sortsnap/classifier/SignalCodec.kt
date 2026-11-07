package com.sortsnap.classifier

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Serialises evidence to/from the `signalsJson` column. */
object SignalCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(Signal.serializer())

    fun encode(signals: List<Signal>): String = json.encodeToString(serializer, signals)

    fun decode(raw: String?): List<Signal> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
