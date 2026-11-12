package info.mayuragarkar.calculator.engine

class CalcError(message: String) : Exception(message)

enum class Group(val title: String) {
    BASIC("Basic"),
    INTEGER("Whole numbers"),
    PRIMES("Primes & divisors"),
    POWERS("Powers & more"),
    COUNTING("Counting & sequences"),
    CONVERT("Bases & checks"),
}

enum class Op(val label: String, val title: String, val group: Group) {
    ADD("+", "Addition", Group.BASIC),
    SUBTRACT("−", "Subtraction", Group.BASIC),
    MULTIPLY("×", "Multiplication", Group.BASIC),
    DIVIDE("÷", "Division", Group.BASIC),
    COMPARE("x ? y", "Comparison", Group.BASIC),
    MOD("x mod y", "Modulo", Group.INTEGER),
    MOD_POW("xʸ mod z", "Modular power", Group.INTEGER),
    MOD_INVERSE("x⁻¹ mod y", "Modular inverse", Group.INTEGER),
    SHIFT_LEFT("x << y", "Left shift", Group.INTEGER),
    SHIFT_RIGHT("x >> y", "Right shift", Group.INTEGER),
    IS_PRIME("Prime?", "Primality test", Group.PRIMES),
    NEXT_PRIME("Next prime", "Next prime after x", Group.PRIMES),
    PREV_PRIME("Prev prime", "Previous prime before x", Group.PRIMES),
    GCD("gcd", "Greatest common divisor", Group.PRIMES),
    LCM("lcm", "Least common multiple", Group.PRIMES),
    POWER("xʸ", "Power", Group.POWERS),
    SQUARE("x²", "Square", Group.POWERS),
    CUBE("x³", "Cube", Group.POWERS),
    FACTORIAL("x!", "Factorial", Group.POWERS),
    LN("ln x", "Natural logarithm", Group.POWERS),
    NCR("C(x, y)", "Combinations", Group.COUNTING),
    NPR("P(x, y)", "Permutations", Group.COUNTING),
    FIBONACCI("Fib(x)", "Fibonacci number", Group.COUNTING),
    CATALAN("Cat(x)", "Catalan number", Group.COUNTING),
    TO_BASE("x in base y", "Base conversion", Group.CONVERT),
    BITS("Bits", "Bit view", Group.CONVERT),
    CHECK_DIGITS("Check digits", "Check digits (card, ISBN, IBAN)", Group.CONVERT),
}

object CalcEngine {
    fun evaluate(op: Op, x: String, y: String = "", z: String = ""): String = Native.evaluate(op.name, x, y, z)

    fun cancel() = Native.cancel()
}

private object Native {
    init {
        System.loadLibrary("bigcalc")
    }

    @JvmStatic external fun evaluate(op: String, x: String, y: String, z: String): String

    @JvmStatic external fun cancel()
}
