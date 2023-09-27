package kdx

fun createString(factor: Int): String {
    return buildString {
        var indexStr = 0
        for (i in 0 until factor) {
            if (indexStr == 10) indexStr = 0
            append(indexStr++)
        }
    }
}

const val SIZE_OF_LEAF = 64 * 32