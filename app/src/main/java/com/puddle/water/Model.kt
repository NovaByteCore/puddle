package com.puddle.water

import kotlin.math.roundToLong

const val ML_PER_OZ = 29.5735

data class DrinkType(val id: String, val name: String, val factor: Double, val color: Long)

object Drinks {
    val all = listOf(
        DrinkType("water", "Water", 1.0, 0xFF4FC3F7),
        DrinkType("sparkling", "Sparkling water", 1.0, 0xFF80DEEA),
        DrinkType("herbal", "Herbal tea", 1.0, 0xFFAED581),
        DrinkType("coconut", "Coconut water", 1.0, 0xFFD7CCC8),
        DrinkType("sports", "Sports drink", 1.0, 0xFF81C784),
        DrinkType("tea", "Tea", 0.9, 0xFFC5A06A),
        DrinkType("milk", "Milk", 0.9, 0xFFDDE3EA),
        DrinkType("soup", "Soup", 0.9, 0xFFE57373),
        DrinkType("juice", "Juice", 0.85, 0xFFFFB74D),
        DrinkType("smoothie", "Smoothie", 0.85, 0xFFF48FB1),
        DrinkType("coffee", "Coffee", 0.8, 0xFF8D6E63),
        DrinkType("hotchoc", "Hot chocolate", 0.8, 0xFF795548),
        DrinkType("soda", "Soda", 0.7, 0xFFA1887F),
        DrinkType("energy", "Energy drink", 0.6, 0xFFFFEE58),
        DrinkType("beer", "Beer", -0.3, 0xFFFFCA28),
        DrinkType("wine", "Wine", -0.6, 0xFFAD1457),
        DrinkType("cocktail", "Cocktail", -0.8, 0xFFBA68C8),
    )

    fun byId(id: String): DrinkType = all.firstOrNull { it.id == id } ?: all[0]
}

data class Entry(val time: Long, val ml: Double, val drinkId: String) {
    val drink: DrinkType get() = Drinks.byId(drinkId)
    val effective: Double get() = ml * drink.factor
}

data class Settings(
    val onboarded: Boolean = false,
    val unit: String = "oz",
    val goalMl: Double = 64 * ML_PER_OZ,
    val weightLbs: Int = 150,
    val activity: Int = 1,
    val remindersOn: Boolean = true,
    val startHour: Int = 8,
    val endHour: Int = 21,
    val intervalMin: Int = 90,
    val smartSkip: Boolean = true,
    val quickAddMl: Double = 8 * ML_PER_OZ,
    val activeCritter: String = "frog",
    val lastDrinkId: String = "water",
    val lastReminderAt: Long = 0L,
    val seen: Set<String> = setOf("frog"),
)

data class AppState(
    val settings: Settings = Settings(),
    val entries: List<Entry> = emptyList(),
)

data class Critter(
    val id: String,
    val name: String,
    val species: String,
    val body: Long,
)

object Critters {
    val all = listOf(
        Critter("frog", "Puddle", "frog", 0xFF6CC46A),
        Critter("penguin", "Pip", "penguin", 0xFF34495E),
        Critter("cat", "Miso", "cat", 0xFFF4A259),
        Critter("duck", "Waddles", "duck", 0xFFFFD54F),
        Critter("turtle", "Shelly", "turtle", 0xFF9CCC65),
        Critter("axolotl", "Lotl", "axolotl", 0xFFF8A5C2),
        Critter("octopus", "Inky", "octopus", 0xFFB39DDB),
        Critter("whale", "Bubbles", "whale", 0xFF5C9BD6),
    )

    fun byId(id: String): Critter = all.firstOrNull { it.id == id } ?: all[0]
}

fun suggestedGoalMl(weightLbs: Int, activity: Int): Double {
    val bonus = when (activity) {
        0 -> 0.0
        1 -> 12.0
        else -> 24.0
    }
    val oz = weightLbs * 0.5 + bonus
    val rounded = ((oz / 4.0).roundToLong() * 4L).coerceIn(32L, 160L)
    return rounded * ML_PER_OZ
}

fun fmtAmount(ml: Double, unit: String): String =
    if (unit == "oz") (ml / ML_PER_OZ).roundToLong().toString()
    else ((ml / 10.0).roundToLong() * 10L).toString()

fun fmtWithUnit(ml: Double, unit: String): String = "${fmtAmount(ml, unit)} $unit"

fun cupSizesMl(unit: String): List<Double> =
    if (unit == "oz") listOf(8.0, 12.0, 16.0, 20.0).map { it * ML_PER_OZ }
    else listOf(250.0, 350.0, 500.0, 750.0)

fun quickAddChoicesMl(unit: String): List<Double> =
    if (unit == "oz") listOf(8.0, 12.0, 16.0).map { it * ML_PER_OZ }
    else listOf(250.0, 350.0, 500.0)
