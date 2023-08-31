package keb.ropes

import kotlin.test.Test

class MultisetTest {

    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test //TODO: this fails ..
    fun testApplyTo() {
        val ranges = listOf(
            0 to 1,
            2 to 4,
            6 to 11,
            13 to 14,
            15 to 18,
            19 to 23,
            24 to 26,
            31 to 32,
            33 to 35,
            36 to 37,
            40 to 44,
            45 to 48,
            49 to 51,
            52 to 57,
            58 to 59,
        )
        val subset = buildSubset {
            for ((start, end) in ranges) add(start, end)
            paddingToLength(simpleString.length)
        }
        println(subset)
    }
}