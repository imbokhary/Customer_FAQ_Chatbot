package com.example.customer_faq_chatbot

import org.json.JSONArray
import java.io.InputStream

data class FaqEntry(
    val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val keywords: List<String>,
    val source: String
) {
    fun searchableText(): String = buildString {
        appendLine("Category: $category")
        appendLine("Question: $question")
        appendLine("Answer: $answer")
        if (keywords.isNotEmpty()) append("Keywords: ${keywords.joinToString(", ")}")
    }
}

data class FaqMatch(
    val entry: FaqEntry,
    val similarity: Double
)

object FaqRepository {
    fun load(input: InputStream): List<FaqEntry> = input.bufferedReader().use { reader ->
        val faqArray = JSONArray(reader.readText())
        require(faqArray.length() > 0) { "FAQ data contains no entries." }
        val entries = (0 until faqArray.length()).map { index ->
            val faq = faqArray.getJSONObject(index)
            val keywords = faq.getJSONArray("keywords")
            FaqEntry(
                id = faq.getString("id").trim().also {
                    require(it.isNotEmpty()) { "FAQ entry $index has an empty id." }
                },
                category = faq.getString("category").trim(),
                question = faq.getString("question").trim().also {
                    require(it.isNotEmpty()) { "FAQ entry $index has an empty question." }
                },
                answer = faq.getString("answer").trim().also {
                    require(it.isNotEmpty()) { "FAQ entry $index has an empty answer." }
                },
                keywords = (0 until keywords.length()).map { keywords.getString(it).trim() }
                    .filter(String::isNotEmpty),
                source = faq.optString("source").trim().ifEmpty {
                    "FAQ ${faq.getString("id")}"
                }
            )
        }
        require(entries.map(FaqEntry::id).distinct().size == entries.size) {
            "FAQ entry ids must be unique."
        }
        entries
    }
}

class FaqRetriever(
    entries: List<FaqEntry>,
    private val embedQuery: (String) -> FloatArray,
    private val embedDocument: (String) -> FloatArray,
    private val minimumSimilarity: Double = DEFAULT_MINIMUM_SIMILARITY
) {
    private val indexedEntries: List<Pair<FaqEntry, FloatArray>>

    init {
        require(entries.isNotEmpty()) { "FAQ retriever requires at least one entry." }
        require(minimumSimilarity in -1.0..1.0) {
            "Minimum similarity must be between -1 and 1."
        }
        indexedEntries = entries.map { entry -> entry to embedDocument(entry.searchableText()) }
    }

    fun search(query: String, limit: Int = DEFAULT_RESULT_LIMIT): List<FaqMatch> {
        require(limit > 0) { "Result limit must be positive." }
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        val queryEmbedding = embedQuery(normalizedQuery)
        return indexedEntries
            .map { (entry, embedding) ->
                FaqMatch(entry, cosineSimilarity(queryEmbedding, embedding))
            }
            .filter { it.similarity >= minimumSimilarity }
            .sortedByDescending(FaqMatch::similarity)
            .take(limit)
    }

    companion object {
        const val DEFAULT_MINIMUM_SIMILARITY = 0.35
        const val DEFAULT_RESULT_LIMIT = 3

        internal fun cosineSimilarity(first: FloatArray, second: FloatArray): Double {
            require(first.size == second.size) {
                "Embedding dimensions differ (${first.size} and ${second.size})."
            }
            var dotProduct = 0.0
            var firstMagnitude = 0.0
            var secondMagnitude = 0.0
            for (index in first.indices) {
                val firstValue = first[index].toDouble()
                val secondValue = second[index].toDouble()
                dotProduct += firstValue * secondValue
                firstMagnitude += firstValue * firstValue
                secondMagnitude += secondValue * secondValue
            }
            if (firstMagnitude == 0.0 || secondMagnitude == 0.0) return 0.0
            return dotProduct / (kotlin.math.sqrt(firstMagnitude) * kotlin.math.sqrt(secondMagnitude))
        }
    }
}
