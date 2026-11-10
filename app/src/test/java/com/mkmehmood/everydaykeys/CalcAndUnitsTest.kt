package com.mkmehmood.everydaykeys

import com.mkmehmood.everydaykeys.data.Calc
import com.mkmehmood.everydaykeys.data.UnitCat
import com.mkmehmood.everydaykeys.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcAndUnitsTest {
    @Test fun precedence() = assertEquals(7.0, Calc.eval("1+2*3")!!, 1e-9)
    @Test fun parentheses() = assertEquals(9.0, Calc.eval("(1+2)*3")!!, 1e-9)
    @Test fun percent() = assertEquals(0.5, Calc.eval("50%")!!, 1e-9)
    @Test fun symbolsAndThousandsSeparators() = assertEquals(1042.0, Calc.eval("1,000+6×7")!!, 1e-9)
    @Test fun divisionByZeroIsNull() = assertNull(Calc.eval("1/0"))
    @Test fun garbageIsNull() { assertNull(Calc.eval("abc")); assertNull(Calc.eval("2+")) }
    @Test fun formatTrimsZeros() {
        assertEquals("42", Calc.format(42.0))
        assertEquals("0.5", Calc.format(0.5))
        assertEquals("0.33333333", Calc.format(1.0 / 3))
    }

    private fun conv(cat: String, v: Double, from: String, to: String): Double {
        val c: UnitCat = Units.cats.first { it.name == cat }
        return Units.convert(c, v, c.units.first { it.id == from }, c.units.first { it.id == to })
    }

    @Test fun kilometresToMiles() = assertEquals(0.621371, conv("Length", 1.0, "km", "mi"), 1e-5)
    @Test fun poundsToKilograms() = assertEquals(0.45359237, conv("Weight", 1.0, "lb", "kg"), 1e-9)
    @Test fun gigabytesToMegabytes() = assertEquals(1024.0, conv("Data", 1.0, "GB", "MB"), 1e-9)
    @Test fun celsiusToFahrenheit() = assertEquals(212.0, conv("Temperature", 100.0, "C", "F"), 1e-9)
    @Test fun kelvinToCelsius() = assertEquals(-273.15, conv("Temperature", 0.0, "K", "C"), 1e-9)
    @Test fun sameUnitIsIdentity() = assertEquals(5.0, conv("Speed", 5.0, "mph", "mph"), 1e-9)
}
