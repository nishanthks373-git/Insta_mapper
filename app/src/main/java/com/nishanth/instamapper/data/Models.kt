package com.nishanth.instamapper.data

data class ReelInfo(
    val url: String,
    val shortcode: String,
    val username: String? = null,
    val fullName: String? = null,
    val caption: String? = null,
    val thumbnailUrl: String? = null,
    val videoUrl: String? = null,
    val likes: Long? = null,
    val comments: Long? = null,
    val views: Long? = null,
    val takenAt: Long? = null,
    val location: TaggedLocation? = null,
    val audio: AudioInfo? = null,
    val sources: List<String> = emptyList(),
) {
    val hashtags: List<String>
        get() = caption?.let { text ->
            Regex("#([\\p{L}\\p{N}_]+)").findAll(text).map { it.groupValues[1] }.distinct().toList()
        } ?: emptyList()

    /** True when nothing more is worth fetching from another source. */
    val isComplete: Boolean
        get() = caption != null && username != null && videoUrl != null && audio != null

    val isEmpty: Boolean
        get() = caption == null && username == null && videoUrl == null && thumbnailUrl == null

    fun merge(o: ReelInfo): ReelInfo = copy(
        username = username ?: o.username,
        fullName = fullName ?: o.fullName,
        caption = caption ?: o.caption,
        thumbnailUrl = thumbnailUrl ?: o.thumbnailUrl,
        videoUrl = videoUrl ?: o.videoUrl,
        likes = likes ?: o.likes,
        comments = comments ?: o.comments,
        views = views ?: o.views,
        takenAt = takenAt ?: o.takenAt,
        location = mergeLocation(location, o.location),
        audio = audio ?: o.audio,
        sources = (sources + o.sources).distinct(),
    )

    private fun mergeLocation(a: TaggedLocation?, b: TaggedLocation?): TaggedLocation? {
        if (a == null) return b
        if (b == null) return a
        return a.copy(
            id = a.id ?: b.id,
            lat = a.lat ?: b.lat,
            lng = a.lng ?: b.lng,
            address = a.address ?: b.address,
            city = a.city ?: b.city,
        )
    }
}

data class TaggedLocation(
    val name: String,
    val id: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String? = null,
    val city: String? = null,
)

data class AudioInfo(
    val title: String?,
    val artist: String?,
    val audioId: String?,
    val isOriginal: Boolean,
) {
    val searchQuery: String
        get() = listOfNotNull(title?.takeUnless { isOriginal && it.equals("Original audio", true) }, artist)
            .joinToString(" ").trim()
}

data class Place(
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String? = null,
    val source: String,
) {
    val key: String get() = "${"%.3f".format(java.util.Locale.US, lat)},${"%.3f".format(java.util.Locale.US, lng)}"
}

data class Spot(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val address: String?,
    val reelUrl: String,
    val username: String?,
    val thumbnailUrl: String?,
    val savedAt: Long,
)

enum class AiProvider { NONE, GEMINI, CLAUDE }

data class AppSettings(
    val provider: AiProvider = AiProvider.NONE,
    val apiKey: String = "",
    val model: String = "",
) {
    val aiEnabled: Boolean get() = provider != AiProvider.NONE && apiKey.isNotBlank()
    val effectiveModel: String
        get() = model.ifBlank {
            when (provider) {
                AiProvider.GEMINI -> "gemini-2.5-flash"
                AiProvider.CLAUDE -> "claude-haiku-5-5"
                AiProvider.NONE -> ""
            }
        }
}
