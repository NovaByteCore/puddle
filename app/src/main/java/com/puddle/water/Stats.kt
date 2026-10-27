package com.puddle.water

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

data class Score(val total: Int, val intake: Int, val quality: Int, val pacing: Int)

data class Progress(val current: Double, val target: Double, val label: String, val goalText: String) {
    val done: Boolean get() = current >= target
    val fraction: Float get() = (current / target).coerceIn(0.0, 1.0).toFloat()
}

object Stats {
    fun dayOf(time: Long): LocalDate =
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()

    fun hourOf(time: Long): Double {
        val t = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
        return t.hour + t.minute / 60.0
    }

    fun effective(list: List<Entry>?): Double =
        (list ?: emptyList()).sumOf { it.effective }.coerceAtLeast(0.0)

    fun goalMet(list: List<Entry>?, goal: Double): Boolean = effective(list) >= goal

    fun currentStreak(days: Map<LocalDate, List<Entry>>, goal: Double): Int {
        var d = LocalDate.now()
        if (!goalMet(days[d], goal)) d = d.minusDays(1)
        var n = 0
        while (goalMet(days[d], goal)) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    fun bestStreak(days: Map<LocalDate, List<Entry>>, goal: Double): Int {
        val met = days.keys.filter { goalMet(days[it], goal) }.sorted()
        var best = 0
        var cur = 0
        var prev: LocalDate? = null
        for (d in met) {
            cur = if (prev != null && prev.plusDays(1) == d) cur + 1 else 1
            best = maxOf(best, cur)
            prev = d
        }
        return best
    }

    /**
     * Hydration score out of 100:
     *  70 for how close you are to your goal (after drink hydration values),
     *  15 for drink quality (share of plain, fully hydrating drinks),
     *  15 for pacing (drinking in each quarter of your day so far).
     */
    fun score(list: List<Entry>?, goal: Double, s: Settings, day: LocalDate): Score {
        val l = list ?: emptyList()
        if (l.isEmpty()) return Score(0, 0, 0, 0)
        val eff = effective(l)
        val intake = (minOf(eff / goal, 1.0) * 70).roundToInt()

        val raw = l.sumOf { it.ml }
        val good = l.filter { it.drink.factor >= 0.95 }.sumOf { it.ml }
        val quality = if (raw <= 0) 0 else (good / raw * 15).roundToInt()

        val start = s.startHour.toDouble()
        val end = maxOf(s.endHour.toDouble(), start + 4)
        val block = (end - start) / 4.0
        val now = LocalDateTime.now()
        val hoursNow = when {
            day == now.toLocalDate() -> now.hour + now.minute / 60.0
            day.isBefore(now.toLocalDate()) -> 24.0
            else -> 0.0
        }
        val hitBlocks = l.map { ((hourOf(it.time) - start) / block).toInt().coerceIn(0, 3) }.toSet()
        var elapsed = 0
        var hit = 0
        for (b in 0 until 4) {
            if (hoursNow >= start + b * block) {
                elapsed++
                if (b in hitBlocks) hit++
            }
        }
        val pacing = if (elapsed == 0) 15 else (hit.toDouble() / elapsed * 15).roundToInt()

        return Score(intake + quality + pacing, intake, quality, pacing)
    }
}

/** Everything the screens need, computed once per change. */
class Summary(val st: AppState) {
    val settings = st.settings
    val unit = settings.unit
    val goal = settings.goalMl
    val days: Map<LocalDate, List<Entry>> = st.entries.groupBy { Stats.dayOf(it.time) }
    val today: LocalDate = LocalDate.now()
    val todayList: List<Entry> = (days[today] ?: emptyList()).sortedByDescending { it.time }
    val todayEff = Stats.effective(todayList)
    val todayScore = Stats.score(todayList, goal, settings, today)
    val streak = Stats.currentStreak(days, goal)
    val best = Stats.bestStreak(days, goal)
    val goalsMet = days.count { Stats.effective(it.value) >= goal }
    val distinctDrinks = st.entries.map { it.drinkId }.toSet().size
    val totalMl = st.entries.filter { it.drink.factor > 0 }.sumOf { it.ml }
    val highScoreDays = days.count { (d, l) -> Stats.score(l, goal, settings, d).total >= 90 }

    fun dayEff(d: LocalDate) = Stats.effective(days[d])

    fun progress(id: String): Progress = when (id) {
        "penguin" -> count(goalsMet, 1, "goal", "Reach your daily goal once")
        "cat" -> count(best, 3, "days", "Hit a 3-day streak")
        "duck" -> count(distinctDrinks, 5, "drinks", "Try 5 different drinks")
        "turtle" -> count(best, 7, "days", "Hit a 7-day streak")
        "axolotl" -> count(highScoreDays, 5, "days", "Score 90 or more on 5 days")
        "octopus" -> {
            val target = if (unit == "oz") 1000 * ML_PER_OZ else 30000.0
            Progress(
                totalMl, target,
                "${fmtAmount(minOf(totalMl, target), unit)} / ${fmtWithUnit(target, unit)}",
                "Drink ${fmtWithUnit(target, unit)} in total",
            )
        }
        "whale" -> count(goalsMet, 30, "goals", "Reach your goal 30 times")
        else -> Progress(1.0, 1.0, "", "Your first buddy")
    }

    private fun count(n: Int, target: Int, noun: String, text: String) =
        Progress(n.toDouble(), target.toDouble(), "${minOf(n, target)} / $target $noun", text)

    val unlocked: Set<String> = Critters.all.filter { progress(it.id).done }.map { it.id }.toSet()

    fun isUnlocked(id: String) = id in unlocked || id in settings.seen
}
