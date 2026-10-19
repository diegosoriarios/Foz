package com.example.foz.data

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object CalculatorEvaluator {
    private const val MAX_DECIMALS = 8

    fun evaluate(raw: String): String? {
        val normalized = raw.trim()
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')
            .filterNot { it.isWhitespace() }

        if (normalized.isEmpty()) return null
        if (normalized.none { it in "+-*/" }) return null

        return try {
            val tokens = tokenize(normalized) ?: return null
            val rpn = toRpn(tokens) ?: return null
            val value = evalRpn(rpn) ?: return null
            format(value)
        } catch (_: Exception) {
            null
        }
    }

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data class Op(val symbol: Char, val unary: Boolean = false) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(input: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var index = 0
        var expectOperand = true

        while (index < input.length) {
            val char = input[index]
            when {
                char.isDigit() || char == '.' -> {
                    val start = index
                    var dots = 0
                    while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
                        if (input[index] == '.') dots++
                        index++
                    }
                    if (dots > 1) return null
                    val text = input.substring(start, index)
                    if (text == ".") return null
                    val number = text.toBigDecimalOrNull() ?: return null
                    tokens.add(Token.Number(number))
                    expectOperand = false
                }
                char == '(' -> {
                    tokens.add(Token.LParen)
                    index++
                    expectOperand = true
                }
                char == ')' -> {
                    if (expectOperand) return null
                    tokens.add(Token.RParen)
                    index++
                    expectOperand = false
                }
                char == '+' || char == '-' || char == '*' || char == '/' -> {
                    if (expectOperand) {
                        if (char == '-') {
                            tokens.add(Token.Op('~', unary = true))
                            index++
                        } else if (char == '+') {
                            index++
                        } else {
                            return null
                        }
                    } else {
                        tokens.add(Token.Op(char))
                        index++
                        expectOperand = true
                    }
                }
                else -> return null
            }
        }

        if (expectOperand) return null
        return tokens
    }

    private fun toRpn(tokens: List<Token>): List<Token>? {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()

        fun precedence(token: Token.Op): Int = when {
            token.unary -> 4
            token.symbol == '*' || token.symbol == '/' -> 3
            else -> 2
        }

        for (token in tokens) {
            when (token) {
                is Token.Number -> output.add(token)
                is Token.Op -> {
                    while (true) {
                        val top = stack.lastOrNull() as? Token.Op ?: break
                        val topPrecedence = precedence(top)
                        val currentPrecedence = precedence(token)
                        val shouldPop = topPrecedence > currentPrecedence ||
                            (topPrecedence == currentPrecedence && !token.unary)
                        if (!shouldPop) break
                        output.add(stack.removeLast())
                    }
                    stack.addLast(token)
                }
                Token.LParen -> stack.addLast(token)
                Token.RParen -> {
                    var found = false
                    while (stack.isNotEmpty()) {
                        val top = stack.removeLast()
                        if (top == Token.LParen) {
                            found = true
                            break
                        }
                        output.add(top)
                    }
                    if (!found) return null
                }
            }
        }

        while (stack.isNotEmpty()) {
            val top = stack.removeLast()
            if (top == Token.LParen) return null
            output.add(top)
        }

        return output
    }

    private fun evalRpn(rpn: List<Token>): BigDecimal? {
        val stack = ArrayDeque<BigDecimal>()

        for (token in rpn) {
            when (token) {
                is Token.Number -> stack.addLast(token.value)
                is Token.Op -> {
                    if (token.unary) {
                        val value = stack.removeLastOrNull() ?: return null
                        stack.addLast(value.negate())
                    } else {
                        val right = stack.removeLastOrNull() ?: return null
                        val left = stack.removeLastOrNull() ?: return null
                        val result = when (token.symbol) {
                            '+' -> left.add(right)
                            '-' -> left.subtract(right)
                            '*' -> left.multiply(right)
                            '/' -> {
                                if (right.compareTo(BigDecimal.ZERO) == 0) return null
                                left.divide(right, MathContext.DECIMAL64)
                            }
                            else -> return null
                        }
                        stack.addLast(result)
                    }
                }
                else -> return null
            }
        }

        return if (stack.size == 1) stack.removeLast() else null
    }

    private fun format(value: BigDecimal): String {
        val rounded = if (value.scale() > MAX_DECIMALS) {
            value.setScale(MAX_DECIMALS, RoundingMode.HALF_UP)
        } else {
            value
        }
        val stripped = rounded.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
    }
}
