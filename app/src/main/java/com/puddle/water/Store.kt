package com.puddle.water

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps everything in one small file inside the app's private storage. */
class Store private constructor(context: Context) {
    private val file = File(context.filesDir, "puddle.json")
    private val tmp = File(context.filesDir, "puddle.json.tmp")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state

    @Synchronized
    fun reload() {
        _state.value = load()
    }

    @Synchronized
    fun update(change: (AppState) -> AppState) {
        val next = change(_state.value)
        _state.value = next
        save(next)
    }

    fun settings(change: (Settings) -> Settings) = update { it.copy(settings = change(it.settings)) }

    fun addDrink(ml: Double, drinkId: String) = update {
        it.copy(
            entries = it.entries + Entry(System.currentTimeMillis(), ml, drinkId),
            settings = it.settings.copy(lastDrinkId = drinkId),
        )
    }

    fun removeEntry(entry: Entry) = update { s ->
        val i = s.entries.indexOf(entry)
        if (i < 0) s else s.copy(entries = s.entries.toMutableList().apply { removeAt(i) })
    }

    fun clearDay(keep: (Entry) -> Boolean) = update { s -> s.copy(entries = s.entries.filter(keep)) }

    private fun load(): AppState {
        val source = when {
            file.exists() -> file
            tmp.exists() -> tmp
            else -> return AppState()
        }
        return try {
            parse(JSONObject(source.readText()))
        } catch (e: Exception) {
            AppState()
        }
    }

    private fun save(state: AppState) {
        try {
            tmp.writeText(toJson(state).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun parse(o: JSONObject): AppState {
        val s = o.optJSONObject("settings") ?: JSONObject()
        val d = Settings()
        val seenArr = s.optJSONArray("seen")
        val seen = if (seenArr == null) d.seen
        else (0 until seenArr.length()).map { seenArr.getString(it) }.toSet() + "frog"
        val settings = Settings(
            onboarded = s.optBoolean("onboarded", d.onboarded),
            unit = s.optString("unit", d.unit),
            goalMl = s.optDouble("goalMl", d.goalMl),
            weightLbs = s.optInt("weightLbs", d.weightLbs),
            activity = s.optInt("activity", d.activity),
            remindersOn = s.optBoolean("remindersOn", d.remindersOn),
            startHour = s.optInt("startHour", d.startHour),
            endHour = s.optInt("endHour", d.endHour),
            intervalMin = s.optInt("intervalMin", d.intervalMin),
            smartSkip = s.optBoolean("smartSkip", d.smartSkip),
            quickAddMl = s.optDouble("quickAddMl", d.quickAddMl),
            activeCritter = s.optString("activeCritter", d.activeCritter),
            lastDrinkId = s.optString("lastDrinkId", d.lastDrinkId),
            lastReminderAt = s.optLong("lastReminderAt", d.lastReminderAt),
            seen = seen,
        )
        val arr = o.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until arr.length()).mapNotNull {
            val e = arr.optJSONObject(it) ?: return@mapNotNull null
            Entry(e.optLong("t"), e.optDouble("ml", 0.0), e.optString("d", "water"))
        }
        return AppState(settings, entries)
    }

    private fun toJson(st: AppState): JSONObject {
        val s = st.settings
        val so = JSONObject()
            .put("onboarded", s.onboarded)
            .put("unit", s.unit)
            .put("goalMl", s.goalMl)
            .put("weightLbs", s.weightLbs)
            .put("activity", s.activity)
            .put("remindersOn", s.remindersOn)
            .put("startHour", s.startHour)
            .put("endHour", s.endHour)
            .put("intervalMin", s.intervalMin)
            .put("smartSkip", s.smartSkip)
            .put("quickAddMl", s.quickAddMl)
            .put("activeCritter", s.activeCritter)
            .put("lastDrinkId", s.lastDrinkId)
            .put("lastReminderAt", s.lastReminderAt)
            .put("seen", JSONArray(s.seen.toList()))
        val arr = JSONArray()
        for (e in st.entries) {
            arr.put(JSONObject().put("t", e.time).put("ml", e.ml).put("d", e.drinkId))
        }
        return JSONObject().put("version", 1).put("settings", so).put("entries", arr)
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
