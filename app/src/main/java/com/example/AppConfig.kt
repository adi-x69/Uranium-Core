package com.example

object AppConfig {

    // Built-in referer rules (same as the NetMirror extension / TV player).
    // If the URL contains the key, that Referer is sent. First match wins.
    val REFERER_RULES: Map<String, String> = linkedMapOf(
        "hakunaymatata.com" to "https://movieboxonline.net/",
        "encrypt.proxy22.shop" to "https://bet.watch22.shop/",
        "cdndash.proxy22.shop" to "https://bet.watch22.shop/"
    )

    // Sent together with the Referer whenever a built-in rule matches.
    const val VIDEO_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

    /**
     * Headers for a request URL:
     *  1. Switch is paused                 -> nothing (like the extension's Pause button)
     *  2. A built-in rule matches          -> Referer + User-Agent
     *  3. Custom headers (user's own list) -> always added, and override the above
     * Call HeaderSettings.ensureLoaded(context) once before using this.
     */
    fun resolveVideoHeaders(videoUrl: String): Map<String, String> {
        if (!HeaderSettings.enabled) return emptyMap()

        val out = LinkedHashMap<String, String>()
        REFERER_RULES.entries.firstOrNull { videoUrl.contains(it.key) }?.let {
            out["Referer"] = it.value
            out["User-Agent"] = VIDEO_USER_AGENT
        }
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
