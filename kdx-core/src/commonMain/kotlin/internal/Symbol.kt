package kdx.internal

import kotlin.jvm.JvmField

/**
 * A symbol class that is used to define unique constants that are self-explanatory in debugger.
 */
internal class Symbol(@JvmField val symbol: String) {
    override fun toString(): String = "<$symbol>"
}