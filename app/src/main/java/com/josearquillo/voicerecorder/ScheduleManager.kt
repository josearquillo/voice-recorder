package com.josearquillo.voicerecorder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScheduledRecording(
    val id: String,
    val startMillis: Long,
    val endMillis: Long
) {
    fun durationMillis(): Long = endMillis - startMillis
}

object ScheduleManager {
    private const val PREFS_NAME = "scheduled_recordings"
    private const val KEY_SCHEDULES = "schedules"

    fun getSchedules(context: Context): MutableList<ScheduledRecording> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCHEDULES, "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<ScheduledRecording>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(ScheduledRecording(
                id = obj.getString("id"),
                startMillis = obj.getLong("start"),
                endMillis = obj.getLong("end")
            ))
        }
        return list.sortedBy { it.startMillis }.toMutableList()
    }

    fun saveSchedules(context: Context, schedules: List<ScheduledRecording>) {
        val array = JSONArray()
        schedules.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("start", s.startMillis)
            obj.put("end", s.endMillis)
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCHEDULES, array.toString())
            .apply()
    }

    fun addSchedule(context: Context, startMillis: Long, endMillis: Long): ScheduledRecording {
        val list = getSchedules(context)
        val schedule = ScheduledRecording(
            id = UUID.randomUUID().toString(),
            startMillis = startMillis,
            endMillis = endMillis
        )
        list.add(schedule)
        saveSchedules(context, list)
        return schedule
    }

    fun removeSchedule(context: Context, id: String) {
        val list = getSchedules(context)
        list.removeAll { it.id == id }
        saveSchedules(context, list)
    }

    fun getActiveSchedules(context: Context): List<ScheduledRecording> {
        val now = System.currentTimeMillis()
        return getSchedules(context).filter { it.endMillis > now }
    }
}
