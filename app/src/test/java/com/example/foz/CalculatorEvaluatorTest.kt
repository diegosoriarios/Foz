package com.example.foz

import com.example.foz.data.CalculatorEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEvaluatorTest {
    @Test
    fun addition() = assertEquals("4", CalculatorEvaluator.evaluate("2+2"))

    @Test
    fun subtraction() = assertEquals("1", CalculatorEvaluator.evaluate("3-2"))

    @Test
    fun multiplication() = assertEquals("6", CalculatorEvaluator.evaluate("2*3"))

    @Test
    fun division() = assertEquals("2.5", CalculatorEvaluator.evaluate("10/4"))

    @Test
    fun precedence() = assertEquals("14", CalculatorEvaluator.evaluate("2+3*4"))

    @Test
    fun parentheses() = assertEquals("20", CalculatorEvaluator.evaluate("(2+3)*4"))

    @Test
    fun unaryMinus() = assertEquals("-3", CalculatorEvaluator.evaluate("-5+2"))

    @Test
    fun doubleNegation() = assertEquals("5", CalculatorEvaluator.evaluate("--5"))

    @Test
    fun decimalDot() = assertEquals("3", CalculatorEvaluator.evaluate("1.5*2"))

    @Test
    fun decimalComma() = assertEquals("3", CalculatorEvaluator.evaluate("1,5*2"))

    @Test
    fun unicodeOperators() = assertEquals("6", CalculatorEvaluator.evaluate("2×3"))

    @Test
    fun whitespaceIgnored() = assertEquals("6", CalculatorEvaluator.evaluate(" 2 * 3 "))

    @Test
    fun divisionByZero() = assertNull(CalculatorEvaluator.evaluate("1/0"))

    @Test
    fun invalidCharacters() = assertNull(CalculatorEvaluator.evaluate("2+a"))

    @Test
    fun plainNumberIsNotExpression() = assertNull(CalculatorEvaluator.evaluate("42"))

    @Test
    fun trailingOperator() = assertNull(CalculatorEvaluator.evaluate("2+"))

    @Test
    fun unbalancedParentheses() = assertNull(CalculatorEvaluator.evaluate("2*(3+4"))

    @Test
    fun emptyInput() = assertNull(CalculatorEvaluator.evaluate("   "))
}
