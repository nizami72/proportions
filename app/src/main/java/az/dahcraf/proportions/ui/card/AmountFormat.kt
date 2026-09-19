package az.dahcraf.proportions.ui.card

import java.util.Locale
import kotlin.math.round

/** Displays quantities to the hundredth, but drops a trailing ".00" for whole numbers. */
fun formatAmount(value: Double): String {
    val rounded = round(value * 100) / 100
    return if (rounded == round(rounded)) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", rounded)
    }
}

fun parseAmount(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
