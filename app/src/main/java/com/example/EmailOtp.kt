package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Firebase RTDB keys can't contain '.', so emails are stored with '.' -> ',' . */
fun sanitizeEmailKey(email: String): String = email.trim().lowercase().replace(".", ",")

fun generateOtpCode(): String = (100000..999999).random().toString()

private val emailJsClient = OkHttpClient()

/**
 * Sends a 6-digit code to [toEmail] via EmailJS's REST API - no backend/Cloud
 * Function required, so this works on Firebase's free Spark plan. Requires
 * an EmailJS template with {{to_email}} and {{otp_code}} variables; fill in
 * the three EMAILJS_* constants in AppConfig.kt first.
 */
suspend fun sendOtpEmail(toEmail: String, otpCode: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val payload = JSONObject().apply {
            put("service_id", AppConfig.EMAILJS_SERVICE_ID)
            put("template_id", AppConfig.EMAILJS_TEMPLATE_ID)
            put("user_id", AppConfig.EMAILJS_PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("otp_code", otpCode)
            })
        }.toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://api.emailjs.com/api/v1.0/email/send")
            .post(body)
            .build()
        emailJsClient.newCall(request).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }
}
