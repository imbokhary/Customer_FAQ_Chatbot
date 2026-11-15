package com.example.gemmachat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaqRetrieverTest {
    @Test
    fun searchRanksClosestFaqFirst() {
        val entries = listOf(
            faq("shipping", "shipping delivery"),
            faq("returns", "returns refund")
        )
        val vectors = mapOf(
            "shipping" to floatArrayOf(1f, 0f),
            "returns" to floatArrayOf(0f, 1f),
            "query" to floatArrayOf(0.9f, 0.1f)
        )
        val results = FaqRetriever(
            entries = entries,
            embedQuery = {
                vectors.getValue("query")
            },
            embedDocument = { text ->
                when {
                    text.startsWith("Category:") && "shipping delivery" in text ->
                        vectors.getValue("shipping")
                    text.startsWith("Category:") && "returns refund" in text ->
                        vectors.getValue("returns")
                    else -> error("Unexpected FAQ document text.")
                }
            },
            minimumSimilarity = 0.0
        )
            .search("where is my delivery")

        assertEquals("shipping", results.first().entry.id)
        assertTrue(results.first().similarity > results.last().similarity)
    }

    @Test
    fun searchRejectsFaqsBelowSimilarityThreshold() {
        val entries = listOf(faq("shipping", "shipping delivery"))
        val results = FaqRetriever(
            entries = entries,
            embedQuery = { floatArrayOf(0f, 1f) },
            embedDocument = { floatArrayOf(1f, 0f) }
        )
            .search("unrelated query")

        assertTrue(results.isEmpty())
    }

    @Test
    fun cosineSimilarityReturnsZeroForZeroVectors() {
        assertEquals(
            0.0,
            FaqRetriever.cosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f)),
            0.0
        )
    }

    private fun faq(id: String, question: String) = FaqEntry(
        id = id,
        category = "Test",
        question = question,
        answer = "Answer for $id",
        keywords = emptyList(),
        source = "Test FAQ"
    )
}
