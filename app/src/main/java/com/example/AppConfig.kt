package com.example

object AppConfig {

    // Referer rules: if the video URL contains the key, that site's Referer is sent.
    // Order matters - the first matching key wins (linkedMapOf keeps insertion order).
    val REFERER_RULES: Map<String, String> = linkedMapOf(
        "hakunaymatata.com" to "https://movieboxonline.net/",
        "encrypt.proxy22.shop" to "https://bet.watch22.shop/",
        "cdndash.proxy22.shop" to "https://bet.watch22.shop/"
    )

    // Sent together with the Referer whenever a rule matches.
    const val VIDEO_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /**
     * Headers for a video URL:
     *  - a rule key is found in the URL -> Referer + User-Agent
     *  - no rule matches               -> empty map (no custom headers at all)
     * Matching is a plain, case-sensitive "contains" check.
     */
    fun resolveVideoHeaders(videoUrl: String): Map<String, String> {
        val referer = REFERER_RULES.entries
            .firstOrNull { videoUrl.contains(it.key) }
            ?.value
            ?: return emptyMap()

        return mapOf(
            "Referer" to referer,
            "User-Agent" to VIDEO_USER_AGENT
        )
    }

    // EmailJS (https://www.emailjs.com) - free tier, used to send the signup
    // OTP code with no backend/Cloud Function needed (keeps the project on
    // the free Spark plan). Fill these in after creating a free EmailJS
    // account: Service ID + Template ID from your Email Service/Template
    // Public Key from Account > General.
    const val EMAILJS_SERVICE_ID = "service_4wth4p8"
    const val EMAILJS_TEMPLATE_ID = "template_z49th4v"
    const val EMAILJS_PUBLIC_KEY = "lDl6bQaYDZKOORov5"
    const val EMAILJS_PRIVATE_KEY = ""
}
