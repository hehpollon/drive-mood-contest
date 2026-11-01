package io.github.hehpollon.drivemood.data

import android.content.Context
import io.github.hehpollon.drivemood.domain.TripEvent
import io.github.hehpollon.drivemood.domain.TripRecord
import org.json.JSONArray
import org.json.JSONObject

class TripStore(context: Context) {
    private val preferences = context.getSharedPreferences("drive_mood", Context.MODE_PRIVATE)

    fun records(): List<TripRecord> = runCatching {
        val array = JSONArray(preferences.getString("records", "[]"))
        (0 until array.length()).mapNotNull { index -> runCatching { decode(array.getJSONObject(index)) }.getOrNull() }
            .sortedByDescending { it.endedAt }
    }.getOrDefault(emptyList())

    fun save(record: TripRecord) {
        val records = (listOf(record) + records().filterNot { it.id == record.id }).take(60)
        preferences.edit().putString("records", JSONArray(records.map(::encode)).toString()).remove("active").apply()
    }

    fun checkpoint(record: TripRecord) { preferences.edit().putString("active", encode(record).toString()).apply() }
    fun recover() {
        val raw = preferences.getString("active", null) ?: return
        val record = runCatching { decode(JSONObject(raw)).copy(interrupted = true) }.getOrNull()
        if (record != null) save(record) else preferences.edit().remove("active").apply()
    }
    fun delete(id: String) { preferences.edit().putString("records", JSONArray(records().filterNot { it.id == id }.map(::encode)).toString()).apply() }
    fun voiceEnabled() = preferences.getBoolean("voice", true)
    fun setVoice(enabled: Boolean) { preferences.edit().putBoolean("voice", enabled).apply() }

    private fun encode(record: TripRecord): JSONObject = JSONObject().apply {
        put("version", 1); put("id", record.id); put("startedAt", record.startedAt); put("endedAt", record.endedAt)
        put("elapsedSeconds", record.elapsedSeconds); put("drivingSeconds", record.drivingSeconds)
        put("stableSeconds", record.stableSeconds); put("changedSeconds", record.changedSeconds)
        put("analyzedSeconds", record.analyzedSeconds); put("averageScore", record.averageScore ?: JSONObject.NULL)
        put("brakePresses", record.brakePresses); put("breathingCount", record.breathingCount)
        put("destination", record.destination ?: JSONObject.NULL); put("isDemo", record.isDemo); put("interrupted", record.interrupted)
        put("events", JSONArray(record.events.map { JSONObject().put("offset", it.offsetSeconds).put("kind", it.kind).put("message", it.message) }))
        put("scores", JSONArray(record.scores))
    }

    private fun decode(value: JSONObject): TripRecord {
        val events = value.optJSONArray("events") ?: JSONArray()
        val scores = value.optJSONArray("scores") ?: JSONArray()
        return TripRecord(
            value.getString("id"), value.getLong("startedAt"), value.getLong("endedAt"),
            value.getLong("elapsedSeconds"), value.getLong("drivingSeconds"), value.getLong("stableSeconds"),
            value.getLong("changedSeconds"), value.getLong("analyzedSeconds"),
            if (value.isNull("averageScore")) null else value.getInt("averageScore"),
            value.getInt("brakePresses"), value.getInt("breathingCount"),
            if (value.isNull("destination")) null else value.getString("destination"),
            value.getBoolean("isDemo"), value.optBoolean("interrupted"),
            (0 until events.length()).map { index -> events.getJSONObject(index).let { TripEvent(it.getLong("offset"), it.getString("kind"), it.getString("message")) } },
            (0 until scores.length()).map { scores.getInt(it) },
        )
    }
}
