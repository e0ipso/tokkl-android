package com.github.e0ipso.tokkl.projects

import androidx.annotation.StringRes
import com.github.e0ipso.tokkl.R
import java.math.BigDecimal
import java.util.Locale

/**
 * Toggl's palette for new projects, as Tokkl's `PROJECT_COLORS` in packages/common/colors.ts, with
 * the names the web app's ColorField gives each swatch, so a colour is never told by its look
 * alone.
 */
enum class ProjectPalette(val hex: String, @StringRes val label: Int) {
    BLUE("#0b83d9", R.string.project_colour_blue),
    PURPLE("#9e5bd9", R.string.project_colour_purple),
    PINK("#d94182", R.string.project_colour_pink),
    ORANGE("#e36a00", R.string.project_colour_orange),
    AMBER("#bf7000", R.string.project_colour_amber),
    YELLOW("#c7af14", R.string.project_colour_yellow),
    RED("#d92b2b", R.string.project_colour_red),
    GREEN("#2da608", R.string.project_colour_green),
    TEAL("#06a893", R.string.project_colour_teal),
    SALMON("#c9806b", R.string.project_colour_salmon),
    INDIGO("#465bb3", R.string.project_colour_indigo),
    MAGENTA("#990099", R.string.project_colour_magenta),
    OLIVE("#566614", R.string.project_colour_olive),
    SLATE("#525266", R.string.project_colour_slate),
    ;

    companion object {
        /** The palette colour [hex] names in any case, or null for a colour outside the palette. */
        fun of(hex: String): ProjectPalette? = entries.firstOrNull { it.hex.equals(hex, ignoreCase = true) }

        /** The colour the web app offers a new project when the workspace has [projects] projects. */
        fun forNewProject(projects: Int): ProjectPalette = entries[projects.mod(entries.size)]
    }
}

/** A field of the project form that can hold a mistake. */
enum class ProjectField { NAME, COLOR, RATE, CURRENCY, ESTIMATE }

/**
 * What the project form holds, as typed. [rate], [currency] and [estimate] are text, so a value
 * the user is still typing survives until Save checks it.
 */
data class ProjectDraft(
    val name: String,
    val color: String,
    val clientId: Long?,
    val billable: Boolean,
    val rate: String,
    val currency: String,
    val estimate: String,
) {

    /**
     * The fields to send, or the mistakes in the order the form shows the fields, each with the
     * web app's message, so the form can show each one and focus the first.
     */
    fun check(): DraftCheck {
        val rate = parseOptionalAmount(rate)
        val code = currency.trim().uppercase(Locale.ROOT)
        val estimate = parseOptionalAmount(estimate)
        val color = color.trim().lowercase(Locale.ROOT)
        val mistakes = buildMap {
            if (name.isBlank()) put(ProjectField.NAME, R.string.project_name_missing)
            if (!isHexColor(color)) put(ProjectField.COLOR, R.string.project_colour_invalid)
            if (rate == Amount.Invalid) put(ProjectField.RATE, R.string.project_rate_invalid)
            if (code.isNotEmpty() && !CURRENCY.matches(code)) put(ProjectField.CURRENCY, R.string.project_currency_invalid)
            if (estimate == Amount.Invalid) put(ProjectField.ESTIMATE, R.string.project_estimate_invalid)
        }
        if (mistakes.isNotEmpty()) return DraftCheck.Invalid(mistakes)
        return DraftCheck.Valid(
            ProjectFields(
                name = name.trim(),
                color = color,
                clientId = clientId,
                billable = billable,
                rate = (rate as? Amount.Value)?.value,
                currency = code.ifEmpty { null },
                estimatedHours = (estimate as? Amount.Value)?.value,
            ),
        )
    }

    companion object {

        /** The form for [project] as the server sent it, or for a new project in [newColor]. */
        fun of(project: Project?, newColor: ProjectPalette): ProjectDraft =
            if (project == null) {
                ProjectDraft("", newColor.hex, clientId = null, billable = false, rate = "", currency = "", estimate = "")
            } else {
                ProjectDraft(
                    name = project.name,
                    color = project.color?.takeIf(::isHexColor)?.lowercase(Locale.ROOT) ?: newColor.hex,
                    clientId = project.clientId,
                    billable = project.billable,
                    rate = amountText(project.rate),
                    currency = project.currency.orEmpty(),
                    estimate = amountText(project.estimatedHours),
                )
            }

        private val CURRENCY = Regex("[A-Z]{3}")
    }
}

/** What [ProjectDraft.check] found. */
sealed interface DraftCheck {
    data class Valid(val fields: ProjectFields) : DraftCheck

    /** Each field with a mistake, and the string resource that says what to fix, in the form's order. */
    data class Invalid(val mistakes: Map<ProjectField, Int>) : DraftCheck
}

/** A `#rrggbb` colour, as Tokkl's `isHexColor` accepts. */
fun isHexColor(value: String): Boolean = HEX_COLOR.matches(value)

private val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")

/** An optional amount field: empty, a number of 0 or more, or anything else. */
internal sealed interface Amount {
    data object Empty : Amount

    data class Value(val value: Double) : Amount

    data object Invalid : Amount
}

/**
 * The web app's `parseOptionalAmount`: empty means no value, the first comma counts as the decimal
 * point, and anything but a finite number of 0 or more is a mistake. JavaScript's `Number` also
 * reads hex, binary and octal literals; those count as mistakes here.
 */
internal fun parseOptionalAmount(raw: String): Amount {
    val text = raw.trim().replaceFirst(',', '.')
    if (text.isEmpty()) return Amount.Empty
    if (!DECIMAL.matches(text)) return Amount.Invalid
    val value = text.toDouble()
    return if (value.isFinite() && value >= 0) Amount.Value(value) else Amount.Invalid
}

/** A decimal number as JavaScript's `Number` reads one, so not Kotlin's `1d` or `1f`. */
private val DECIMAL = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

/** [value] as the web app writes it into a field: 50 as "50", 12.5 as "12.5", null as "". */
internal fun amountText(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let { BigDecimal(it.toString()).stripTrailingZeros().toPlainString() }.orEmpty()
