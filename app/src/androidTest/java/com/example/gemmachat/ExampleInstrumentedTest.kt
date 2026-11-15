package com.example.gemmachat

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.gemmachat", appContext.packageName)
    }

    @Test
    fun bundledEmbedderRetrievesRelevantFaq() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val embedder = TextEmbedder.createFromOptions(
            appContext,
            TextEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("universal_sentence_encoder.tflite")
                        .build()
                )
                .build()
        )

        embedder.use { textEmbedder ->
            val entries = appContext.assets.open("faq.json").use(FaqRepository::load)
            val retriever = FaqRetriever(
                entries = entries,
                embedQuery = { text -> embed(textEmbedder, text) },
                embedDocument = { text -> embed(textEmbedder, text) }
            )

            val result = retriever.search("let me know payment method")

            assertEquals("payment-methods", result.first().entry.id)
        }
    }

    private fun embed(embedder: TextEmbedder, text: String): FloatArray =
        embedder.embed(text)
            .embeddingResult()
            .embeddings()
            .first { it.headName().orElse("") == "response_encoding" }
            .floatEmbedding()
}