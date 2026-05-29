package com.example.util

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GeminiSuggestion(
    val category: String,
    val tag: String,
    val merchant: String
)

object GeminiCategorizer {
    private const val TAG = "GeminiCategorizer"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Gemini API Request Models
    private data class GeminiRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig? = null
    )

    private data class Content(
        val parts: List<Part>
    )

    private data class Part(
        val text: String
    )

    private data class GenerationConfig(
        val responseMimeType: String = "application/json",
        val temperature: Float = 0.1f
    )

    // Gemini API Response Models
    private data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    private data class Candidate(
        val content: ResponseContent?
    )

    private data class ResponseContent(
        val parts: List<ResponsePart>?
    )

    private data class ResponsePart(
        val text: String?
    )

    /**
     * Calls Gemini API to categorize a transaction and suggest a relevant tag.
     */
    suspend fun suggestCategoryAndTag(
        description: String,
        amount: Double,
        type: String,
        fallbackCategory: String,
        fallbackTag: String
    ): GeminiSuggestion = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured. Using local defaults.")
            val cleanDesc = description.split(" ").firstOrNull()?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "Transaction"
            return@withContext GeminiSuggestion(fallbackCategory, fallbackTag, cleanDesc)
        }

        val prompt = """
            Analyze this bank transaction and suggest:
            1. Category (Must be exactly one of: Food, Transport, Rent/Bills, Shopping, Salary, Entertainment, Health, Investment, Refunds, Other)
            2. Tag (A single concise lowercase subcategory/label, e.g., 'coffee', 'rideshare', 'subway', 'groceries', 'subscription', 'electronics', 'apparel', 'gym', 'bonus')
            3. Merchant (Convert muddy transaction text into a clean merchant name, e.g., 'DUNKIN DONUTS #4823' becomes 'Dunkin Donuts', or 'VZWRLSS*MY BILL' becomes 'Verizon')
            
            Transaction details:
            - Description / Sms text: "$description"
            - Amount: ${if (amount > 0.0) amount else "Unknown"}
            - Type: "$type"
            
            Respond only with a single JSON object containing these keys: "category", "tag" and "merchant". No formatting ticks or wrapper code block outside of valid JSON list.
        """.trimIndent()

        val requestData = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig()
        )

        val adapterRequest = moshi.adapter(GeminiRequest::class.java)
        val jsonRequest = adapterRequest.toJson(requestData)

        val request = Request.Builder()
            .url("$BASE_URL?key=$apiKey")
            .header("Content-Type", "application/json")
            .post(jsonRequest.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini API call failed: Code ${response.code}, Message: ${response.message}")
                    val cleanDesc = description.split(" ").firstOrNull()?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "Transaction"
                    return@withContext GeminiSuggestion(fallbackCategory, fallbackTag, cleanDesc)
                }

                val responseBodyStr = response.body?.string() ?: ""
                val responseAdapter = moshi.adapter(GeminiResponse::class.java)
                val geminiResponse = responseAdapter.fromJson(responseBodyStr)

                val replyText = geminiResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (replyText != null) {
                    val suggestionAdapter = moshi.adapter(GeminiSuggestion::class.java)
                    val cleanJson = cleanJsonResponse(replyText)
                    val suggestion = suggestionAdapter.fromJson(cleanJson)
                    if (suggestion != null) {
                        return@withContext suggestion
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini categorization: ", e)
        }

        // Return fallback if anything fails
        val cleanDesc = description.split(" ").firstOrNull()?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "Transaction"
        GeminiSuggestion(fallbackCategory, fallbackTag, cleanDesc)
    }

    private fun cleanJsonResponse(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.substringAfter("```").substringBeforeLast("```").trim()
        }
        return clean
    }
}
