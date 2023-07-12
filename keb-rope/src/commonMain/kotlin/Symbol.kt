package keb.ropes

import kotlin.jvm.JvmField

internal class Symbol(@JvmField val symbol: String) {
    override fun toString(): String = "<$symbol>"
}