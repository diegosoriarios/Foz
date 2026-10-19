package com.example.foz.data

import com.example.foz.model.InstantAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class InstantAnswerRepository {
    // DuckDuckGo Instant Answer API + Wikipedia REST are used because they don't require API keys

    suspend fun fetchInstantAnswer(query: String, language: String = "en"): InstantAnswer? {
        return withContext(Dispatchers.IO) {
            fetchFromDuckDuckGo(query) ?: fetchFromWikipedia(query, language)
        }
    }

    private fun fetchFromDuckDuckGo(query: String): InstantAnswer? {
        return try {
            val url = "https://api.duckduckgo.com/?q=${URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1&skip_disambig=1"
            val response = URL(url).openConnection().inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)

            val abstractText = root.optString("AbstractText", "")
            val answer = root.optString("Answer", "")
            val heading = root.optString("Heading", "")
            val sourceUrl = if (root.isNull("AbstractURL")) null else root.optString("AbstractURL", "")
            val sourceName = root.optString("AbstractSource", "")

            val text = when {
                answer.isNotBlank() && answer != "null" -> answer
                abstractText.isNotBlank() -> abstractText
                else -> return null
            }

            InstantAnswer(
                heading = heading.ifBlank { null },
                text = text,
                sourceUrl = sourceUrl?.takeIf { it.isNotBlank() },
                sourceLabel = sourceName.ifBlank { "DuckDuckGo" }
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchFromWikipedia(query: String, language: String): InstantAnswer? {
        return try {
            val lang = if (language.length >= 2) language.take(2) else "en"
            val encoded = URLEncoder.encode(query.replace(' ', '_'), "UTF-8")
            val url = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encoded"
            val connection = URL(url).openConnection()
            connection.setRequestProperty("Accept", "application/json")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)

            val type = root.optString("type", "")
            if (type != "standard") return null

            val title = root.optString("title", "")
            val extract = root.optString("extract", "")
            if (extract.isBlank()) return null

            InstantAnswer(
                heading = title.ifBlank { null },
                text = extract,
                sourceUrl = if (root.isNull("content_urls")) null else
                    root.getJSONObject("content_urls").getJSONObject("desktop").optString("page", "").ifBlank { null },
                sourceLabel = "Wikipedia"
            )
        } catch (_: Exception) {
            null
        }
    }
}
