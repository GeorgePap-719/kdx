package kdx

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

    /**
     * This test-suite tests three transformation scenarios.
     *
     * 1) We should be able to apply the difference of [str1] and [str2] to [simpleString] through a coordinate transformation.
     * The [result] is "result" of this difference. More technically, [result] should contain the difference of [str1] and [str2],
     * and we expect characters that are missing and present in both subsets to be present in [result],
     * as well as characters that are only in [str2] (as it's the base string).
     * 2) Applying [transformShrink] between the difference of [str1] and [str2] and the `deletes1` (the "deletions" between [str1] and the [simpleString]),
     * and then using the resulting "transform" on the result of scenario `1.` should yield the [str2].
     * In this scenario, "mentally" we can think of this operation as a necessary step to apply `deletes1` subset on [simpleString],
     * if we have already applied another subset (`deletes3` in our case) on it.
     * 3) Applying [transformUnion] on the difference of [str1] and [str2] to [simpleString] through a coordinate transformation,
     * yields the [str2] (the base string) as a result.
     * This happens because [transformUnion] preservers the "deletes" from both subsets.
     */
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