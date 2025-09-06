package com.tanasi.streamflix.models.cablevisionhd

import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.providers.CableVisionHDProvider
import org.jsoup.nodes.Document

fun Document.toTvShows(): List<TvShow> {

    val listaNegra = listOf(
        "Mundo Latam \uD83C\uDF10",
        "Donar con Paypal"
    )

    val channels = this.select("div.channels > div")

    return channels.mapNotNull { channelElement ->
        val linkElement = channelElement.selectFirst("a.channel-link")

        val href = linkElement?.attr("href")
        val name = linkElement?.selectFirst("img")?.attr("alt")
        var poster = linkElement?.selectFirst("img")?.attr("src")

        if (name in listaNegra) {
            return@mapNotNull null
        }

        if (href.isNullOrEmpty() || name.isNullOrEmpty() || poster.isNullOrEmpty()) {
            return@mapNotNull null
        }

        if (!poster.startsWith("http")) {
            poster = "${CableVisionHDProvider.baseUrl}/${poster.removePrefix("/")}"
        }

        TvShow(
            id = href,
            title = name,
            poster = poster,
        )
    }
}