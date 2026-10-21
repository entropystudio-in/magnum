package com.cadence.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class FilterType { PEAK, LOW_SHELF, HIGH_SHELF }

data class EqFilter(
    val type: FilterType,
    val freq: Float,
    val gainDb: Float,
    val q: Float
)

/**
 * Shared EQ state. The audio thread reads [filters]/[preampDb]/[enabled]
 * and re-derives coefficients whenever [version] changes.
 */
object EqSettings {
    val bandFreqs = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private const val BAND_Q = 1.41f

    @Volatile var enabled: Boolean = true
    /** The user's own preamp slider. */
    @Volatile var preampDb: Float = 0f
    /** Preamp that ships inside the imported AutoEQ profile (already clip-safe for that curve). */
    @Volatile var autoEqPreampDb: Float = 0f
    @Volatile var autoEqFilters: List<EqFilter> = emptyList()
    @Volatile var autoEqName: String = ""
    @Volatile var autoEqEnabled: Boolean = true
    @Volatile var bandGains: FloatArray = FloatArray(10)
    @Volatile var version: Int = 0
        private set

    /** Filters actually applied: imported AutoEQ curve first, then the user's own bands. */
    fun activeFilters(): List<EqFilter> {
        val out = ArrayList<EqFilter>()
        if (autoEqEnabled) out.addAll(autoEqFilters)
        val gains = bandGains
        for (i in gains.indices) {
            if (gains[i] != 0f) out.add(EqFilter(FilterType.PEAK, bandFreqs[i], gains[i], BAND_Q))
        }
        return out
    }

    /**
     * AutoEQ's own preamp, plus the user's slider, minus headroom for the user's boosted bands
     * (AutoEQ already accounts for its own boosts) so the combined curve cannot clip.
     */
    fun effectivePreampDb(): Float {
        var maxBoost = 0f
        for (g in bandGains) if (g > maxBoost) maxBoost = g
        val auto = if (autoEqEnabled) autoEqPreampDb else 0f
        return auto + preampDb - maxBoost
    }

    fun touch() { version++ }

    fun setBand(index: Int, gain: Float) {
        val copy = bandGains.copyOf()
        copy[index] = gain
        bandGains = copy
        touch()
    }

    fun resetBands() {
        bandGains = FloatArray(10)
        touch()
    }

    fun loadAutoEq(name: String, text: String): Boolean {
        val parsed = parseParametricEq(text)
        if (parsed.second.isEmpty()) return false
        autoEqName = name
        autoEqFilters = parsed.second
        autoEqPreampDb = parsed.first
        autoEqEnabled = true
        touch()
        return true
    }

    fun clearAutoEq() {
        autoEqFilters = emptyList()
        autoEqName = ""
        autoEqPreampDb = 0f
        touch()
    }

    /**
     * Parses the AutoEq "ParametricEQ.txt" format:
     *   Preamp: -6.2 dB
     *   Filter 1: ON PK Fc 105 Hz Gain -2.4 dB Q 0.70
     *   Filter 2: ON LSC Fc 105 Hz Gain 5.0 dB Q 0.70
     */
    fun parseParametricEq(text: String): Pair<Float, List<EqFilter>> {
        var preamp = 0f
        val list = ArrayList<EqFilter>()
        val num = Regex("-?\\d+(\\.\\d+)?")
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("Preamp", ignoreCase = true)) {
                num.find(line)?.value?.toFloatOrNull()?.let { preamp = it }
            } else if (line.startsWith("Filter", ignoreCase = true)) {
                if (!line.contains(" ON ", ignoreCase = true)) continue
                val upper = line.uppercase(Locale.ROOT)
                val type = when {
                    upper.contains(" LSC ") || upper.contains(" LS ") -> FilterType.LOW_SHELF
                    upper.contains(" HSC ") || upper.contains(" HS ") -> FilterType.HIGH_SHELF
                    upper.contains(" PK ") || upper.contains(" PEQ ") -> FilterType.PEAK
                    else -> continue
                }
                val fc = Regex("FC\\s+(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toFloatOrNull()
                val gain = Regex("GAIN\\s+(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toFloatOrNull()
                val q = Regex("\\sQ\\s+(-?\\d+(\\.\\d+)?)").find(upper)?.groupValues?.get(1)?.toFloatOrNull() ?: 0.7f
                if (fc != null && gain != null) list.add(EqFilter(type, fc, gain, q))
            }
        }
        return preamp to list
    }

    // ---- persistence ----
    private const val PREFS = "cadence_eq"

    fun save(ctx: Context) {
        val o = JSONObject()
        o.put("enabled", enabled)
        o.put("autoEqEnabled", autoEqEnabled)
        o.put("autoEqName", autoEqName)
        o.put("preamp", preampDb.toDouble())
        o.put("autoEqPreamp", autoEqPreampDb.toDouble())
        o.put("bands", JSONArray(bandGains.map { it.toDouble() }))
        val f = JSONArray()
        for (x in autoEqFilters) {
            f.put(JSONObject().put("t", x.type.name).put("f", x.freq.toDouble())
                .put("g", x.gainDb.toDouble()).put("q", x.q.toDouble()))
        }
        o.put("autoEq", f)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("state", o.toString()).apply()
    }

    fun load(ctx: Context) {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("state", null) ?: return
        try {
            val o = JSONObject(s)
            enabled = o.optBoolean("enabled", true)
            autoEqEnabled = o.optBoolean("autoEqEnabled", true)
            autoEqName = o.optString("autoEqName", "")
            preampDb = o.optDouble("preamp", 0.0).toFloat()
            autoEqPreampDb = o.optDouble("autoEqPreamp", 0.0).toFloat()
            val b = o.optJSONArray("bands")
            if (b != null && b.length() == 10) bandGains = FloatArray(10) { b.getDouble(it).toFloat() }
            val f = o.optJSONArray("autoEq")
            if (f != null) {
                autoEqFilters = (0 until f.length()).map {
                    val j = f.getJSONObject(it)
                    EqFilter(FilterType.valueOf(j.getString("t")), j.getDouble("f").toFloat(),
                        j.getDouble("g").toFloat(), j.getDouble("q").toFloat())
                }
            }
            touch()
        } catch (_: Exception) { }
    }
}
