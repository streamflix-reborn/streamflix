package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request

class MailRuExtractor : Extractor() {
    override val name = "MailRu"
    override val mainUrl = "https://my.mail.ru"

    override suspend fun extract(link: String): Video {
        try {
            val videoId = extractVideoId(link) ?: throw Exception("Could not extract video ID from URL")
            
            val timestamp = System.currentTimeMillis()
            
            val metaUrl = "$mainUrl/+/video/meta/$videoId?xemail=&ajax_call=1&func_name=&mna=&mnb=&ext=1&_=$timestamp"
            
            val client = OkHttpClient.Builder().build()
            
            val request = Request.Builder()
                .url(metaUrl)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                throw Exception("Failed to fetch video metadata")
            }
            
            val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
            val videos = jsonResponse.getAsJsonArray("videos")
            
            if (videos == null || videos.size() == 0) {
                throw Exception("No videos found in response")
            }
            
            // Get the first available video
            var selectedVideo: JsonObject? = null
            for (i in 0 until videos.size()) {
                val video = videos.get(i).asJsonObject
                val streamUrl = video.get("url")?.asString ?: ""
                if (streamUrl.isNotEmpty()) {
                    selectedVideo = video
                    break
                }
            }
            
            if (selectedVideo == null) {
                throw Exception("No valid videos found")
            }
            
            val streamUrl = selectedVideo.get("url")?.asString ?: ""
            val finalUrl = if (streamUrl.startsWith("//")) {
                "https:$streamUrl"
            } else {
                streamUrl
            }
            
            return Video(
                source = finalUrl
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("MailRu extraction failed: ${e.message}")
        }
    }
    
    private fun extractVideoId(link: String): String? {
        val embedPattern = Regex("embed/([0-9]+)")
        val matchResult = embedPattern.find(link)
        return matchResult?.groupValues?.get(1)
    }
    
}
