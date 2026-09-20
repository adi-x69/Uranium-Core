package com.example

object AppConfig {
    const val VIDEO_REFERER = "https://fmoviesunblocked.net/"

    // EmailJS (https://www.emailjs.com) - free tier, used to send the signup
    // OTP code with no backend/Cloud Function needed (keeps the project on
    // the free Spark plan). Fill these in after creating a free EmailJS
    // account: Service ID + Template ID from your Email Service/Template,
    // Public Key from Account > General.
    const val EMAILJS_SERVICE_ID = "YOUR_EMAILJS_SERVICE_ID"
    const val EMAILJS_TEMPLATE_ID = "YOUR_EMAILJS_TEMPLATE_ID"
    const val EMAILJS_PUBLIC_KEY = "YOUR_EMAILJS_PUBLIC_KEY"
}
