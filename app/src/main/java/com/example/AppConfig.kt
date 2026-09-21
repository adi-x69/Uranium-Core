package com.example

object AppConfig {
    const val VIDEO_REFERER = "https://fmoviesunblocked.net/"

    // EmailJS (https://www.emailjs.com) - free tier, used to send the signup
    // OTP code with no backend/Cloud Function needed (keeps the project on
    // the free Spark plan). Fill these in after creating a free EmailJS
    // account: Service ID + Template ID from your Email Service/Template,
    // Public Key from Account > General.
    const val EMAILJS_SERVICE_ID = "service_4wth4p8"
    const val EMAILJS_TEMPLATE_ID = "template_z49th4v"
    const val EMAILJS_PUBLIC_KEY = "lDl6bQaYDZKOORov5"
    const val EMAILJS_PRIVATE_KEY = ""
}
