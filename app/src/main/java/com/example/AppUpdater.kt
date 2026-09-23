package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

const val SIGNATURE_MISMATCH_EXPLANATION =
    "This update can't install over your current app because it was signed differently. Please uninstall the current app, then install this update fresh. Your account and data are safe — everything is stored online, not on this device."

data class AppUpdateInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val forceUpdate: Boolean = true
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class ReadyToInstall(val apkFile: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    data class SignatureMismatch(
        val message: String = SIGNATURE_MISMATCH_EXPLANATION,
        val apkFile: File? = null
    ) : DownloadState()
}

// Firebase Realtime Database node that holds the latest-release info.
// If you ever rename the node in the Firebase console, change it here too.
private const val UPDATE_NODE = "appUpdate"
private const val DB_URL = "https://uranium-tv-core-default-rtdb.firebaseio.com"

// Safe readers: a wrong type typed into the console (e.g. "3" as text instead of the number 3)
// must never crash the app - they just fall back to a default.
private fun DataSnapshot.intOf(vararg keys: String): Int {
    for (k in keys) {
        val v = child(k).value?.toString()?.trim()?.toDoubleOrNull()
        if (v != null) return v.toInt()
    }
    return 0
}

private fun DataSnapshot.textOf(vararg keys: String): String? {
    for (k in keys) {
        val v = child(k).value?.toString()
        if (!v.isNullOrBlank()) return v
    }
    return null
}

/**
 * Checks the Firebase `appUpdate` node for a newer version than current BuildConfig.VERSION_CODE.
 * If found, displays a non-dismissible blocking update UI that downloads the APK and launches the installer.
 */
@Composable
fun AppUpdateChecker(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    val scope = rememberCoroutineScope()

    // Listen to Firebase Realtime Database for the update node.
    // Any problem here (offline, permission denied, bad data) fails OPEN: the app just keeps working.
    DisposableEffect(Unit) {
        val updateRef = try {
            FirebaseDatabase.getInstance(DB_URL).getReference(UPDATE_NODE)
        } catch (e: Throwable) {
            null
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (!snapshot.exists()) {
                        updateInfo = null
                        return
                    }
                    val latestCode = snapshot.intOf("latestVersionCode", "versionCode")
                    val latestName = snapshot.textOf("latestVersionName", "versionName") ?: ""
                    val apkUrl = snapshot.textOf("apkUrl", "url") ?: ""
                    val releaseNotes = snapshot.textOf("releaseNotes", "notes")
                        ?: "Performance improvements and bug fixes."
                    val force = (snapshot.child("forceUpdate").value as? Boolean) ?: true

                    updateInfo = if (latestCode > BuildConfig.VERSION_CODE && apkUrl.isNotBlank()) {
                        AppUpdateInfo(
                            latestVersionCode = latestCode,
                            latestVersionName = latestName.ifEmpty { "v$latestCode" },
                            apkUrl = apkUrl,
                            releaseNotes = releaseNotes,
                            forceUpdate = force
                        )
                    } else {
                        null
                    }
                } catch (e: Throwable) {
                    updateInfo = null
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        updateRef?.addValueEventListener(listener)
        onDispose { updateRef?.removeEventListener(listener) }
    }

    fun startDownload(url: String) {
        scope.launch {
            downloadState = DownloadState.Downloading(0f, 0L, 0L)
            withContext(Dispatchers.IO) {
                try {
                    var currentUrl = url
                    var connection: HttpURLConnection
                    var redirects = 0
                    // Handle HTTP 301/302/303/307 redirects (common on GitHub Releases)
                    while (true) {
                        connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                            instanceFollowRedirects = false
                            setRequestProperty("Accept-Encoding", "identity")
                        }
                        val status = connection.responseCode
                        if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                            status == HttpURLConnection.HTTP_MOVED_TEMP ||
                            status == HttpURLConnection.HTTP_SEE_OTHER ||
                            status == 307
                        ) {
                            val newUrl = connection.getHeaderField("Location")
                            connection.disconnect()
                            if (newUrl != null && redirects < 5) {
                                currentUrl = newUrl
                                redirects++
                                continue
                            }
                        }
                        break
                    }

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        downloadState = DownloadState.Error("Server returned code ${connection.responseCode}")
                        return@withContext
                    }

                    val totalBytes = connection.contentLength.toLong()
                    val outputFile = File(context.cacheDir, "update.apk")
                    if (outputFile.exists()) outputFile.delete()

                    connection.inputStream.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var downloaded: Long = 0
                            var lastEmitTime = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastEmitTime > 150) {
                                    lastEmitTime = now
                                    val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                                    downloadState = DownloadState.Downloading(progress, downloaded, totalBytes)
                                }
                            }
                        }
                    }

                    // Sanity checks so a bad upload gives a clear message instead of a vague installer error
                    if (totalBytes > 0 && outputFile.length() != totalBytes) {
                        outputFile.delete()
                        downloadState = DownloadState.Error("Download was incomplete. Please retry.")
                        return@withContext
                    }
                    val archiveInfo = context.packageManager.getPackageArchiveInfo(outputFile.absolutePath, 0)
                    if (archiveInfo == null) {
                        outputFile.delete()
                        downloadState = DownloadState.Error("The downloaded file is not a valid APK. Check apkUrl in Firebase.")
                        return@withContext
                    }
                    if (archiveInfo.packageName != context.packageName) {
                        outputFile.delete()
                        downloadState = DownloadState.Error("The downloaded APK is for a different app.")
                        return@withContext
                    }
                    if (PackageInfoCompat.getLongVersionCode(archiveInfo) <= BuildConfig.VERSION_CODE.toLong()) {
                        outputFile.delete()
                        downloadState = DownloadState.Error("The uploaded APK is not newer than this app. Raise versionCode in app/build.gradle.kts and rebuild.")
                        return@withContext
                    }

                    // Proactively detect signature mismatch before even attempting install
                    if (checkSignatureMismatch(context, outputFile)) {
                        downloadState = DownloadState.SignatureMismatch(
                            message = SIGNATURE_MISMATCH_EXPLANATION,
                            apkFile = outputFile
                        )
                        return@withContext
                    }

                    downloadState = DownloadState.ReadyToInstall(outputFile)
                    // Automatically trigger installer once downloaded
                    withContext(Dispatchers.Main) {
                        installApk(context, outputFile) { explanation ->
                            downloadState = DownloadState.SignatureMismatch(explanation, outputFile)
                        }
                    }
                } catch (e: Exception) {
                    downloadState = DownloadState.Error(e.localizedMessage ?: "Download failed. Please check internet.")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        updateInfo?.let { info ->
            // Prevent backing out while update is required
            BackHandler(enabled = true) {
                // Forced update - back press ignored
            }

            ForcedUpdateDialog(
                info = info,
                downloadState = downloadState,
                onStartDownload = { startDownload(info.apkUrl) },
                onInstallNow = { file ->
                    installApk(context, file) { explanation ->
                        downloadState = DownloadState.SignatureMismatch(explanation, file)
                    }
                }
            )
        }
    }
}

@Suppress("DEPRECATION")
fun getApkSignatures(context: Context, apkFile: File): List<ByteArray>? {
    if (!apkFile.exists()) return null
    val pm = context.packageManager
    val path = apkFile.absolutePath

    // 1. Try with GET_SIGNING_CERTIFICATES on Android P (API 28)+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            val pi = pm.getPackageArchiveInfo(path, flags)
            val signingInfo = pi?.signingInfo
            if (signingInfo != null) {
                val certs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                if (!certs.isNullOrEmpty()) {
                    return certs.map { it.toByteArray() }
                }
            }
            val sigs = pi?.signatures
            if (!sigs.isNullOrEmpty()) {
                return sigs.map { it.toByteArray() }
            }
        } catch (_: Throwable) {}
    }

    // 2. Legacy / fallback with GET_SIGNATURES
    try {
        val pi = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        val sigs = pi?.signatures
        if (!sigs.isNullOrEmpty()) {
            return sigs.map { it.toByteArray() }
        }
    } catch (_: Throwable) {}

    return null
}

@Suppress("DEPRECATION")
fun getInstalledAppSignatures(context: Context): List<ByteArray>? {
    val pm = context.packageManager
    val pkgName = context.packageName

    // 1. Try with GET_SIGNING_CERTIFICATES on Android P (API 28)+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
            val pi = pm.getPackageInfo(pkgName, flags)
            val signingInfo = pi.signingInfo
            if (signingInfo != null) {
                val certs = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                if (!certs.isNullOrEmpty()) {
                    return certs.map { it.toByteArray() }
                }
            }
            val sigs = pi.signatures
            if (!sigs.isNullOrEmpty()) {
                return sigs.map { it.toByteArray() }
            }
        } catch (_: Throwable) {}
    }

    try {
        val pi = pm.getPackageInfo(pkgName, PackageManager.GET_SIGNATURES)
        val sigs = pi.signatures
        if (!sigs.isNullOrEmpty()) {
            return sigs.map { it.toByteArray() }
        }
    } catch (_: Throwable) {}

    return null
}

private fun sha256Fingerprint(bytes: ByteArray): String {
    return try {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        digest.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        bytes.contentHashCode().toString()
    }
}

fun checkSignatureMismatch(context: Context, apkFile: File): Boolean {
    val installed = getInstalledAppSignatures(context) ?: return false
    val apk = getApkSignatures(context, apkFile) ?: return false

    if (installed.isEmpty() || apk.isEmpty()) return false

    val installedFingerprints = installed.map { sha256Fingerprint(it) }.toSet()
    val apkFingerprints = apk.map { sha256Fingerprint(it) }.toSet()

    // If there is ANY overlap, signatures match (e.g. key rotation or identical cert)
    val hasMatch = installedFingerprints.any { it in apkFingerprints }
    return !hasMatch
}

fun installApk(
    context: Context,
    apkFile: File,
    onSignatureMismatch: ((String) -> Unit)? = null
) {
    if (!apkFile.exists()) return

    // Proactively verify signatures before attempting install to prevent silent failure
    if (checkSignatureMismatch(context, apkFile)) {
        if (onSignatureMismatch != null) {
            onSignatureMismatch(SIGNATURE_MISMATCH_EXPLANATION)
        } else {
            android.widget.Toast.makeText(context, SIGNATURE_MISMATCH_EXPLANATION, android.widget.Toast.LENGTH_LONG).show()
        }
        return
    }

    // Check if permission to install unknown apps is granted on Android 8.0+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return
        }
    }

    val apkUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )

    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(installIntent)
    } catch (e: Exception) {
        if (checkSignatureMismatch(context, apkFile)) {
            onSignatureMismatch?.invoke(SIGNATURE_MISMATCH_EXPLANATION)
        } else {
            android.widget.Toast.makeText(
                context,
                "Unable to open installer: ${e.localizedMessage ?: "Unknown error"}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun ForcedUpdateDialog(
    info: AppUpdateInfo,
    downloadState: DownloadState,
    onStartDownload: () -> Unit,
    onInstallNow: (File) -> Unit
) {
    val context = LocalContext.current
    var canInstallPackages by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canInstallPackages = context.packageManager.canRequestPackageInstalls()
        }
    }

    Dialog(
        onDismissRequest = { /* Non-dismissible forced update */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon with subtle glowing container
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = "Update Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Update Required",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "A new version of Uranium TV is available. Please update to keep using the app smoothly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Version Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CURRENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text("➔", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "NEW VERSION",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${info.latestVersionName} (${info.latestVersionCode})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "What's New",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Dynamic action/progress area based on download state
                    when (downloadState) {
                        is DownloadState.Idle -> {
                            Button(
                                onClick = onStartDownload,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download & Update", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            }
                        }

                        is DownloadState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    progress = { downloadState.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val downloadedMb = downloadState.downloadedBytes / (1024f * 1024f)
                                    val totalMb = downloadState.totalBytes / (1024f * 1024f)
                                    val percent = (downloadState.progress * 100).toInt()

                                    Text(
                                        text = if (totalMb > 0) String.format("%.1f MB / %.1f MB", downloadedMb, totalMb)
                                        else String.format("%.1f MB", downloadedMb),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )

                                    Text(
                                        text = "$percent%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        is DownloadState.ReadyToInstall -> {
                            Button(
                                onClick = { onInstallNow(downloadState.apkFile) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Install Update Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        is DownloadState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = downloadState.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Button(
                                    onClick = onStartDownload,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Retry Download", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        is DownloadState.SignatureMismatch -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = downloadState.message,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Start,
                                        lineHeight = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(uninstallIntent)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uninstall Current App", fontWeight = FontWeight.SemiBold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(browserIntent)
                                        } catch (_: Exception) {}
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download APK in Browser", style = MaterialTheme.typography.bodyMedium)
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                TextButton(onClick = onStartDownload) {
                                    Text(
                                        "Retry Download",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    // Android 8+ install permission note if not granted
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        var hasPerm by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
                        if (!hasPerm) {
                            Spacer(modifier = Modifier.height(10.dp))
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Text(
                                    "Tap here to allow app installs if prompted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
