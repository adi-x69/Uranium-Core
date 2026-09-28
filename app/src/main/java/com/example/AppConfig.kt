package com.example

object AppConfig {

    // Built-in referer rules (NetMirror official extension v3.20.9 & streaming player CDN rules).
    // If the URL contains the key, that Referer and Origin are sent. First match wins.
    val REFERER_RULES: Map<String, String> = linkedMapOf(
        "hakunaymatata.com" to "https://mzfi.me/",
        "mzfi.me" to "https://mzfi.me/",
        "encrypt.proxy22.shop" to "https://bet.watch22.shop/",
        "cdndash.proxy22.shop" to "https://bet.watch22.shop/",
        "proxy22.shop" to "https://bet.watch22.shop/",
        "watch22.shop" to "https://bet.watch22.shop/",
        "watch21.shop" to "https://bet.watch21.shop/",
        "movieboxonline.net" to "https://movieboxonline.net/",
        "netmirror" to "https://netmirror.studio/"
    )

    // Sent together with the Referer whenever video requests are made.
    const val VIDEO_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /**
     * Headers for a request URL:
     *  1. Switch is paused                 -> nothing (like the extension's Pause button)
     *  2. A built-in rule matches          -> Referer + User-Agent + Origin
     *  3. Fallback for video streams       -> Default NetMirror referer (https://mzfi.me/) + User-Agent
     *  4. Custom headers (user's own list) -> always added, and override the above
     * Call HeaderSettings.ensureLoaded(context) once before using this.
     */
    fun resolveVideoHeaders(videoUrl: String): Map<String, String> {
        if (!HeaderSettings.enabled) return emptyMap()

        val out = LinkedHashMap<String, String>()
        val matched = REFERER_RULES.entries.firstOrNull { videoUrl.contains(it.key, ignoreCase = true) }
        val referer = matched?.value ?: if (videoUrl.contains("http", ignoreCase = true)) "https://mzfi.me/" else null
        if (referer != null) {
            out["Referer"] = referer
            out["Origin"] = referer.trimEnd('/')
        }
        out["User-Agent"] = VIDEO_USER_AGENT
        out["Accept"] = "*/*"
        out["Accept-Language"] = "en-US,en;q=0.9"

        HeaderSettings.customHeaders.forEach { out[it.name] = it.value }
        return out
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
