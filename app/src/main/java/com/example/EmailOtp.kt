package com.example

import android.util.Log
import java.util.concurrent.TimeUnit
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

private val emailJsClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

sealed class OtpSendResult {
    object Success : OtpSendResult()
    data class Failure(val detail: String) : OtpSendResult()
}

/**
 * Sends a 6-digit code to [toEmail] via EmailJS's REST API.
 * Includes Origin and User-Agent headers, preventing 403 strict-mode rejections
 * from non-browser clients when a private key is not provided.
 */
suspend fun sendOtpEmail(toEmail: String, otpCode: String): OtpSendResult = withContext(Dispatchers.IO) {
    try {
        val payload = JSONObject().apply {
            put("service_id", AppConfig.EMAILJS_SERVICE_ID)
            put("template_id", AppConfig.EMAILJS_TEMPLATE_ID)
            put("user_id", AppConfig.EMAILJS_PUBLIC_KEY)
            if (AppConfig.EMAILJS_PRIVATE_KEY.isNotBlank()) {
                put("accessToken", AppConfig.EMAILJS_PRIVATE_KEY)
            }
            put("template_params", JSONObject().apply {
                put("to_email", toEmail)
                put("email", toEmail)
                put("otp_code", otpCode)
                put("code", otpCode)
            })
        }.toString()
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url("https://api.emailjs.com/api/v1.0/email/send")
            .header("Content-Type", "application/json")
            .header("Origin", "http://localhost")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) UraniumTV")
            .post(body)

        if (AppConfig.EMAILJS_PRIVATE_KEY.isNotBlank()) {
            requestBuilder.header("authorization", "Bearer ${AppConfig.EMAILJS_PRIVATE_KEY}")
        }

        val request = requestBuilder.build()
        emailJsClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Log.d("EmailOtp", "OTP sent successfully to $toEmail")
                OtpSendResult.Success
            } else {
                val errorBody = response.body?.string()?.take(300) ?: "(no response body)"
                Log.e("EmailOtp", "EmailJS returned code ${response.code}: $errorBody")
                OtpSendResult.Failure("HTTP ${response.code}: $errorBody")
            }
        }
    } catch (e: Exception) {
        Log.e("EmailOtp", "Exception sending OTP email", e)
        OtpSendResult.Failure("${e.javaClass.simpleName}: ${e.message}")
    }
}

