package com.mkmehmood.everydaykeys.data

class UnitDef(val id: String, val name: String, val toBase: Double)
class UnitCat(val name: String, val units: List<UnitDef>, val temperature: Boolean = false)

/** Offline unit conversion for the keyboard's converter panel. */
object Units {
    val cats: List<UnitCat> = listOf(
        UnitCat("Length", listOf(
            UnitDef("mm", "mm", 0.001), UnitDef("cm", "cm", 0.01), UnitDef("m", "m", 1.0), UnitDef("km", "km", 1000.0),
            UnitDef("in", "in", 0.0254), UnitDef("ft", "ft", 0.3048), UnitDef("yd", "yd", 0.9144), UnitDef("mi", "mi", 1609.344)
        )),
        UnitCat("Weight", listOf(
            UnitDef("mg", "mg", 0.000001), UnitDef("g", "g", 0.001), UnitDef("kg", "kg", 1.0), UnitDef("t", "tonne", 1000.0),
            UnitDef("oz", "oz", 0.028349523125), UnitDef("lb", "lb", 0.45359237)
        )),
        UnitCat("Volume", listOf(
            UnitDef("ml", "ml", 0.001), UnitDef("l", "litre", 1.0), UnitDef("tsp", "tsp", 0.00492892159375),
            UnitDef("tbsp", "tbsp", 0.01478676478125), UnitDef("fl oz", "fl oz", 0.0295735295625),
            UnitDef("cup", "cup", 0.2365882365), UnitDef("gal", "gal (US)", 3.785411784)
        )),
        UnitCat("Speed", listOf(
            UnitDef("m/s", "m/s", 1.0), UnitDef("km/h", "km/h", 1000.0 / 3600.0),
            UnitDef("mph", "mph", 0.44704), UnitDef("kn", "knot", 1852.0 / 3600.0)
        )),
        UnitCat("Data", listOf(
            UnitDef("B", "B", 1.0), UnitDef("KB", "KB", 1024.0), UnitDef("MB", "MB", 1048576.0),
            UnitDef("GB", "GB", 1073741824.0), UnitDef("TB", "TB", 1099511627776.0)
        )),
        UnitCat("Temperature", listOf(UnitDef("C", "°C", 0.0), UnitDef("F", "°F", 0.0), UnitDef("K", "K", 0.0)), temperature = true)
    )

    fun convert(cat: UnitCat, v: Double, from: UnitDef, to: UnitDef): Double {
        if (cat.temperature) {
            val c = when (from.id) { "F" -> (v - 32) * 5 / 9; "K" -> v - 273.15; else -> v }
            return when (to.id) { "F" -> c * 9 / 5 + 32; "K" -> c + 273.15; else -> c }
        }
        return v * from.toBase / to.toBase
    }
}
