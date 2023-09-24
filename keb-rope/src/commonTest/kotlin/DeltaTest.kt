package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeltaTest {

    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test
    fun testSimpleEdit() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val str = delta.applyToString("hello world")
        assertEquals("herald", str.toString())
        assertEquals(6, str.length)
    }

    @Test
    fun testFactor() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, deletes) = delta.factor()
        assertEquals("heraello world", inserts.applyToString("hello world").toString())
        assertEquals("hld", deletes.deleteFromString("hello world"))
    }

    @Test
    fun testFactorCanYieldSameResult() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, deletes) = delta.factor()
        val transform = deletes.transformExpand(inserts.getInsertedSubset())
        assertEquals("herald", transform.deleteFromString("heraello world"))
    }

    @Test
    fun testGetInsertedSubset() {
        val delta = simpleEdit(1..<9, Rope("era").root, 11)
        val (inserts, _) = delta.factor()
        assertEquals("hello world", inserts.getInsertedSubset().deleteFromString("heraello world"))
    }

    @Test
    fun testTransformExpand() {
        val str1 = "01259DGJKNQTUVWXYcdefghkmopqrstvwxy"
        val deletions = str1.findDeletions(simpleString)
        val delta = simpleEdit(10..<12, Rope("+").root, str1.length)
        assertEquals("01259DGJKN+UVWXYcdefghkmopqrstvwxy", delta.applyToString(str1).toString())
        val (inserts, _) = delta.factor()
        assertEquals("01259DGJKN+QTUVWXYcdefghkmopqrstvwxy", inserts.applyToString(str1).toString())
        val transformExpand = inserts.transformExpand(deletions, false)
        assertEquals(
            "0123456789ABCDEFGHIJKLMN+OPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
            transformExpand.applyToString(simpleString).toString()
        )
        val transformExpandAfter = inserts.transformExpand(deletions, true)
        assertEquals(
            "0123456789ABCDEFGHIJKLMNOP+QRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
            transformExpandAfter.applyToString(simpleString).toString()
        )
    }

    @Test
    fun testTransformShrink() {
        val delta = simpleEdit(10..<12, Rope("+").root, simpleString.length)
        val (inserts, _) = delta.factor()
        assertEquals(
            "0123456789+ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
            inserts.applyToString(simpleString).toString()
        )
        val str1 = "0345678BCxyz"
        val deletions = str1.findDeletions(simpleString)
        val transformShrink = inserts.transformShrink(deletions)
        assertEquals("0345678+BCxyz", transformShrink.applyToString(str1).toString())
        val str2 = "356789ABCx"
        val deletions2 = str2.findDeletions(simpleString)
        val transformShrink2 = inserts.transformShrink(deletions2)
        assertEquals("356789+ABCx", transformShrink2.applyToString(str2).toString())
    }

    @Test
    fun testInsertsIterator() {
        val str = "0123456789"
        val delta = buildDelta(10) {
            replace(2..<2, Rope("a"))
            delete(3..<5)
            replace(6..<8, Rope("b"))
        }
        assertEquals("01a25b89", delta.applyToString(str).toString())
        val insertsIterator = delta.insertsIterator()
        var hasNext = insertsIterator.hasNext()
        assertTrue(hasNext)
        assertEquals(DeltaRegion(2, 2, 1), insertsIterator.next())
        hasNext = insertsIterator.hasNext()
        assertTrue(hasNext)
        assertEquals(DeltaRegion(6, 5, 1), insertsIterator.next())
        hasNext = insertsIterator.hasNext()
        assertFalse(hasNext)
    }

    @Test
    fun testInsertsIterator2() {
        val str = "0123456789"
        val delta = buildDelta(10) {
            replace(2..<2, Rope("a"))
            delete(3..<5)
            replace(6..<8, Rope("b"))
        }
        assertEquals("01a25b89", delta.applyToString(str).toString())
        val insertsIterator = delta.insertsIterator()
        var hasNext = insertsIterator.hasNext()
        assertTrue(hasNext)
        assertEquals(DeltaRegion(2, 2, 1), insertsIterator.next())
        val deltaElement = delta.changes[2]
        println(deltaElement)
        println(delta.changes)
        println(delta.changes.size)
    }

    @Test
    fun testDeletesIterator() {
        val str = "0123456789"
        val delta = buildDelta<RopeLeaf>(10) {
            delete(0..<2)
            delete(4..<6)
            delete(8..<10)
        }
        assertEquals("2367", delta.applyToString(str).toString())
        val deletesIterator = delta.deletesIterator()
        var hasNext = deletesIterator.hasNext()
        assertTrue(hasNext)
        assertEquals(DeltaRegion(0, 0, 2), deletesIterator.next())
        hasNext = deletesIterator.hasNext()
        assertTrue(hasNext)
        assertEquals(DeltaRegion(4, 2, 2), deletesIterator.next())
        hasNext = deletesIterator.hasNext()
        assertTrue(hasNext)
        assertEquals(DeltaRegion(8, 4, 2), deletesIterator.next())
        hasNext = deletesIterator.hasNext()
        assertFalse(hasNext)
    }
}

fun DeltaRopeNode.applyToString(input: String): Rope {
    return applyTo(Rope(input))
}