package keb.ropes

/**
 * Reads up to 32 chunks of characters and creates a node from them. The process is repeated until the end of [input].
 */
//TODO: this is only for bulk reading
fun read32Chunks(input: String): List<BTreeNode> { //TODO: make it private?
    val chunks = readChunksOf64Chars(input)
    return buildList {
        chunks.chunked(CHUNK_NUMBER).forEach { chunks ->
            val valueNode = chunks.joinToString(separator = "") { it }
//            add(BTreeNode(valueNode.length, valueNode))
            TODO()
        }
    }
}

private fun readChunksOf64Chars(input: String): List<String> {
    val chunks = mutableListOf<String>()
    val len = input.length
    var chunkCount = 0
    val chunkBuilder = StringBuilder()
    for (i in 0 until len) {
        if (chunkCount == CHUNK_SIZE) {
            val chunk = chunkBuilder.toString()
            chunks.add(chunk)
            chunkBuilder.clear()
            chunkCount = 0
        }
        if (i == len - 2) {
            chunkBuilder.append(input[i])
            val chunk = chunkBuilder.toString()
            chunks.add(chunk)
            break
        }
        chunkBuilder.append(input[i])
        chunkCount++
    }
    return chunks
}

const val CHUNK_NUMBER = 32
const val CHUNK_SIZE = 64