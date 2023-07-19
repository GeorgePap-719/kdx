package keb.server.model

import keb.ropes.RopeLeaf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The editor's core data structure, which represents a text.
 * It stores a [rope's leaves][RopeLeaf] in a set.
 */
@Serializable(with = TextSerializer::class)
data class Text(val leaves: List<RopeLeaf>) {
    val leavesSize = leaves.size
    val length: Int by lazy { leaves.sumOf { it.weight } }

    fun add(index: Int, element: RopeLeaf): Text {
        val newLeaves = buildList {
            val iterator = leaves.iterator()
            for (i in 0..<leavesSize) {
                if (i == index) add(element)
                if (iterator.hasNext()) add(iterator.next())
            }
            if (index == leavesSize) add(element) // for addLast operation
        }
        return Text(newLeaves)
    }

    fun addLast(element: RopeLeaf): Text = add(leavesSize, element)

    fun addFirst(element: RopeLeaf): Text = add(0, element)

    fun remove(index: Int): Text {
        if (leavesSize == 1) return Text(emptyList())
        val newLeaves = buildList {
            for (i in 0..<leavesSize) {
                val iterator = leaves.iterator()
                if (i == index) continue
                if (iterator.hasNext()) add(iterator.next())
            }
        }
        return Text(newLeaves)
    }
}

object TextSerializer : KSerializer<Text> {
    private val ropeLeafListSerializer = ListSerializer(RopeLeafSerializer)
    override val descriptor: SerialDescriptor = ropeLeafListSerializer.descriptor
    override fun serialize(encoder: Encoder, value: Text) = ropeLeafListSerializer.serialize(encoder, value.leaves)
    override fun deserialize(decoder: Decoder): Text = Text(ropeLeafListSerializer.deserialize(decoder))
}

object RopeLeafSerializer : KSerializer<RopeLeaf> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RopeLeaf") {
        element<String>("chars")
        element<Int>("lineCount")
    }

    override fun serialize(encoder: Encoder, value: RopeLeaf) {
        encoder.encodeString(value.chars)
        encoder.encodeInt(value.lineCount)
    }

    override fun deserialize(decoder: Decoder): RopeLeaf {
        val chars = decoder.decodeString()
        val lineCount = decoder.decodeInt()
        return RopeLeaf(chars, lineCount)
    }
}