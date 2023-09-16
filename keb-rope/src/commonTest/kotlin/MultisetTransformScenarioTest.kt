package keb.ropes

import kotlin.test.Test
import kotlin.test.assertEquals

class MultisetTransformScenarioTest {

    private val simpleString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    @Test
    fun testTransforms() {
        transformCase(
            "02345678BCDFGHKLNOPQRTUVXZbcefghjlmnopqrstwx",
            "027CDGKLOTUbcegopqrw",
            "01279ACDEGIJKLMOSTUWYabcdegikopqruvwyz",
        )
        transformCase(
            "01234678DHIKLMNOPQRUWZbcdhjostvy",
            "136KLPQZvy",
            "13569ABCEFGJKLPQSTVXYZaefgiklmnpqruvwxyz",
        )
        transformCase(
            "0125789BDEFIJKLMNPVXabdjmrstuwy",
            "12BIJVXjmrstu",
            "12346ABCGHIJOQRSTUVWXYZcefghijklmnopqrstuvxz",
        )
        transformCase(
            "12456789ABCEFGJKLMNPQRSTUVXYadefghkrtwxz",
            "15ACEFGKLPRUVYdhrtx",
            "0135ACDEFGHIKLOPRUVWYZbcdhijlmnopqrstuvxy",
        )
        transformCase(
            "0128ABCDEFGIJMNOPQXYZabcfgijkloqruvy",
            "2CEFGMZabijloruvy",
            "2345679CEFGHKLMRSTUVWZabdehijlmnoprstuvwxyz",
        )
        transformCase(
            "01245689ABCDGJKLMPQSTWXYbcdfgjlmnosvy",
            "01245ABCDJLQSWXYgsv",
            "0123457ABCDEFHIJLNOQRSUVWXYZaeghikpqrstuvwxz",
        )
    }

    private fun transformCase(str1: String, str2: String, result: String) {
        val deletes1 = str1.findDeletions(simpleString)
        val deletes2 = str2.findDeletions(str1)
        val deletes3 = deletes2.transformExpand(deletes1)
        val str3 = deletes3.deleteFromString(simpleString)
        assertEquals(result, str3)
        assertEquals(str2, deletes1.transformShrink(deletes3).deleteFromString(str3))
        assertEquals(str2, deletes2.transformUnion(deletes1).deleteFromString(simpleString))
    }
}