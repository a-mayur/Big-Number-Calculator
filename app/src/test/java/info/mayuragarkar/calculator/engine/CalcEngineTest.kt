package info.mayuragarkar.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class CalcEngineTest {
    private fun eval(op: Op, x: String, y: String = "", z: String = "") = CalcEngine.evaluate(op, x, y, z)
    private fun fails(op: Op, x: String, y: String = "", z: String = ""): String =
        assertThrows(CalcError::class.java) { eval(op, x, y, z) }.message!!
    private fun approxValue(s: String): Double {
        assertTrue(s, s.startsWith("≈ "))
        return s.removePrefix("≈ ").toDouble()
    }

    @Test fun basicArithmetic() {
        assertEquals("100000000000000000000", eval(Op.ADD, "99999999999999999999", "1"))
        assertEquals("-0.75", eval(Op.SUBTRACT, "0.25", "1"))
        assertEquals("121932631137021795226185032733622923332237463801111263526900",
            eval(Op.MULTIPLY, "123456789012345678901234567890", "987654321098765432109876543210"))
        assertEquals("2.5", eval(Op.DIVIDE, "5", "2"))
        assertEquals("1000", eval(Op.MULTIPLY, "10", "100"))
    }

    @Test fun divisionKeeps100Places() {
        assertEquals("0." + "3".repeat(100), eval(Op.DIVIDE, "1", "3"))
        assertTrue(fails(Op.DIVIDE, "1", "0").contains("zero"))
    }

    @Test fun compare() {
        assertEquals("x > y", eval(Op.COMPARE, "10", "9.99"))
        assertEquals("x = y", eval(Op.COMPARE, "2.50", "2.5"))
        assertEquals("x < y", eval(Op.COMPARE, "-3", "2"))
    }

    @Test fun inputValidation() {
        assertTrue(fails(Op.ADD, "", "1").contains("Enter"))
        assertTrue(fails(Op.ADD, "abc", "1").contains("valid"))
        assertTrue(fails(Op.ADD, "1e999999999", "1").contains("valid"))
        assertEquals("5.5", eval(Op.ADD, "+5", ".5"))
        assertEquals("6", eval(Op.ADD, " 5. ", "1"))
        assertEquals("-2", eval(Op.ADD, "-1", "-1"))
        assertEquals("4", eval(Op.SQUARE, "2", "garbage"))
    }

    @Test fun modularArithmetic() {
        assertEquals("2", eval(Op.MOD, "-7", "3"))
        assertTrue(fails(Op.MOD, "7", "0").contains("greater than 0"))
        assertTrue(fails(Op.MOD, "7.5", "2").contains("whole"))
        assertEquals("4", eval(Op.MOD_INVERSE, "3", "11"))
        assertTrue(fails(Op.MOD_INVERSE, "4", "8").contains("common factor"))
    }

    @Test fun primality() {
        assertEquals("Prime", eval(Op.IS_PRIME, "97"))
        assertEquals("Prime", eval(Op.IS_PRIME, BigInteger.TWO.pow(127).subtract(BigInteger.ONE).toString()))
        assertEquals("Not prime", eval(Op.IS_PRIME, "1"))
        assertEquals("Not prime", eval(Op.IS_PRIME, "-7"))
        assertEquals("Not prime", eval(Op.IS_PRIME, "561"))
    }

    @Test fun shifts() {
        assertEquals(BigInteger.TWO.pow(100).toString(), eval(Op.SHIFT_LEFT, "1", "100"))
        assertEquals("1", eval(Op.SHIFT_RIGHT, "1024", "10"))
        assertEquals("4", eval(Op.SHIFT_LEFT, "16", "-2"))
        assertEquals("-1", eval(Op.SHIFT_RIGHT, "-5", "99999999999999"))
        assertEquals("0", eval(Op.SHIFT_LEFT, "0", "99999999999999"))
        assertTrue(fails(Op.SHIFT_LEFT, "1", "2000000000").contains("limit"))
    }

    @Test fun integerPowersAreExact() {
        assertEquals("1024", eval(Op.POWER, "2", "10"))
        assertEquals("-8", eval(Op.POWER, "-2", "3"))
        assertEquals("0.25", eval(Op.POWER, "2", "-2"))
        assertEquals("2.25", eval(Op.POWER, "1.5", "2"))
        assertEquals("1", eval(Op.POWER, "0", "0"))
        assertEquals("-1", eval(Op.POWER, "-1", "99999999999999999999999"))
        assertEquals(BigInteger.TEN.pow(1000).toString(), eval(Op.POWER, "10", "1000"))
        assertTrue(fails(Op.POWER, "0", "-1").contains("negative power"))
        assertTrue(fails(Op.POWER, "10", "20000000").contains("limit"))
        assertEquals("0.001", eval(Op.CUBE, "0.1"))
    }

    @Test fun fractionalPowersAreApproximate() {
        assertEquals("≈ 2", eval(Op.POWER, "4", "0.5"))
        assertEquals(1.4142135623730951, approxValue(eval(Op.POWER, "2", "0.5")), 1e-13)
        assertTrue(eval(Op.POWER, "10", "1000000.5").startsWith("≈ 3.16227"))
        assertTrue(eval(Op.POWER, "10", "1000000.5").endsWith("e1000000"))
        assertTrue(fails(Op.POWER, "-8", "0.5").contains("real number"))
    }

    @Test fun factorial() {
        assertEquals("1", eval(Op.FACTORIAL, "0"))
        assertEquals("120", eval(Op.FACTORIAL, "5"))
        assertEquals("15511210043330985984000000", eval(Op.FACTORIAL, "25"))
        var naive = BigInteger.ONE
        for (i in 2..1000) naive = naive.multiply(BigInteger.valueOf(i.toLong()))
        assertEquals(naive.toString(), eval(Op.FACTORIAL, "1000"))
        assertTrue(fails(Op.FACTORIAL, "-1").contains("0 or more"))
        assertTrue(fails(Op.FACTORIAL, "2.5").contains("whole"))
        assertTrue(fails(Op.FACTORIAL, "3000000").contains("limit"))
    }

    @Test fun modularPower() {
        assertEquals("445", eval(Op.MOD_POW, "4", "13", "497"))
        assertEquals("2790", eval(Op.MOD_POW, "65", "17", "3233"))
        assertEquals("65", eval(Op.MOD_POW, "2790", "2753", "3233"))
        assertEquals("4", eval(Op.MOD_POW, "3", "-1", "11"))
        assertEquals("0", eval(Op.MOD_POW, "5", "3", "1"))
        assertTrue(fails(Op.MOD_POW, "2", "3", "0").contains("greater than 0"))
        assertTrue(fails(Op.MOD_POW, "2", "3").contains("Enter a value for z"))
    }

    @Test fun primesAndDivisors() {
        assertEquals("101", eval(Op.NEXT_PRIME, "100"))
        assertEquals("2", eval(Op.NEXT_PRIME, "-5"))
        assertEquals("3", eval(Op.NEXT_PRIME, "2"))
        assertEquals(BigInteger.TEN.pow(100).add(BigInteger.valueOf(267)).toString(), eval(Op.NEXT_PRIME, "1" + "0".repeat(100)))
        assertEquals("97", eval(Op.PREV_PRIME, "100"))
        assertEquals("2", eval(Op.PREV_PRIME, "3"))
        assertTrue(fails(Op.PREV_PRIME, "2").contains("no prime"))
        assertEquals("6\n\nBézout: 12 × (-1) + 18 × 1 = 6", eval(Op.GCD, "12", "18"))
        assertEquals("6", eval(Op.GCD, "12, 18, 30"))
        assertEquals("60", eval(Op.LCM, "4 6", "10"))
        assertTrue(fails(Op.GCD, "12").contains("at least two"))
    }

    @Test fun counting() {
        assertEquals("10", eval(Op.NCR, "5", "2"))
        assertEquals("100891344545564193334812497256", eval(Op.NCR, "100", "50"))
        assertEquals("0", eval(Op.NCR, "3", "5"))
        assertEquals("20", eval(Op.NPR, "5", "2"))
        assertEquals("0", eval(Op.FIBONACCI, "0"))
        assertEquals("55", eval(Op.FIBONACCI, "10"))
        assertEquals("354224848179261915075", eval(Op.FIBONACCI, "100"))
        assertEquals("1", eval(Op.CATALAN, "0"))
        assertEquals("16796", eval(Op.CATALAN, "10"))
    }

    @Test fun basesAndBits() {
        assertEquals("FF", eval(Op.TO_BASE, "255", "16"))
        assertEquals("11111111", eval(Op.TO_BASE, "255", "2"))
        assertEquals("-Z", eval(Op.TO_BASE, "-35", "36"))
        assertTrue(fails(Op.TO_BASE, "10", "1").contains("2 to 36"))
        assertEquals("Bit length: 8\nOnes: 8\nTrailing zeros: 0", eval(Op.BITS, "255"))
        assertEquals("Bit length: 0\nOnes: 0\nTrailing zeros: –", eval(Op.BITS, "0"))
        assertEquals("Two's complement width: 8 bits\nOnes: 8\nTrailing zeros: 0\n\n11111111", eval(Op.BITS, "-1"))
        assertTrue(eval(Op.BITS, "-128").endsWith("\n10000000"))
    }

    @Test fun checkDigits() {
        assertEquals("Luhn (cards, IMEI): valid", eval(Op.CHECK_DIGITS, "79927398713"))
        assertTrue(eval(Op.CHECK_DIGITS, "79927398710").contains("should be 3"))
        assertTrue(eval(Op.CHECK_DIGITS, "978-0-306-40615-7").startsWith("ISBN-13 / EAN-13: valid"))
        assertTrue(eval(Op.CHECK_DIGITS, "0-306-40615-2").startsWith("ISBN-10: valid"))
        assertEquals("IBAN: valid", eval(Op.CHECK_DIGITS, "GB82 WEST 1234 5698 7654 32"))
        assertTrue(eval(Op.CHECK_DIGITS, "GB00 WEST 1234 5698 7654 32").contains("should be 82"))
        assertTrue(fails(Op.CHECK_DIGITS, "hello").contains("digits"))
    }

    @Test fun naturalLog() {
        assertEquals("≈ 0", eval(Op.LN, "1"))
        assertEquals(1.0, approxValue(eval(Op.LN, "2.718281828459045")), 1e-12)
        assertEquals(2302.585092994046, approxValue(eval(Op.LN, "1" + "0".repeat(1000))), 1e-9)
        assertTrue(fails(Op.LN, "0").contains("greater than 0"))
    }
}
