package com.example

import android.content.Context
import android.content.Intent
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
}

/**
 * Checks Firebase `appUpdate` node for a newer version than current BuildConfig.VERSION_CODE.
 * If found, displays a non-dismissible blocking update UI that downloads the APK and launches the installer.
 */
@Composable
fun AppUpdateChecker(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    val scope = rememberCoroutineScope()

    // Listen to Firebase Realtime Database for appUpdate node
    DisposableEffect(Unit) {
        val updateRef = FirebaseDatabase.getInstance().getReference("appUpdate")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    updateInfo = null
                    return
                }
                val latestCode = (snapshot.child("latestVersionCode").getValue(Long::class.java)
                    ?: snapshot.child("versionCode").getValue(Long::class.java)
                    ?: snapshot.child("latestVersionCode").getValue(Int::class.java)?.toLong()
                    ?: 0L).toInt()

                val latestName = snapshot.child("latestVersionName").getValue(String::class.java)
                    ?: snapshot.child("versionName").getValue(String::class.java)
                    ?: ""

                val apkUrl = snapshot.child("apkUrl").getValue(String::class.java)
                    ?: snapshot.child("url").getValue(String::class.java)
                    ?: ""

                val releaseNotes = snapshot.child("releaseNotes").getValue(String::class.java)
                    ?: snapshot.child("notes").getValue(String::class.java)
                    ?: "Performance improvements and bug fixes."

                val force = snapshot.child("forceUpdate").getValue(Boolean::class.java) ?: true

                if (latestCode > BuildConfig.VERSION_CODE && apkUrl.isNotBlank()) {
                    updateInfo = AppUpdateInfo(
                        latestVersionCode = latestCode,
                        latestVersionName = latestName.ifEmpty { "v$latestCode" },
                        apkUrl = apkUrl,
                        releaseNotes = releaseNotes,
                        forceUpdate = force
                    )
                } else {
                    updateInfo = null
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        updateRef.addValueEventListener(listener)
        onDispose { updateRef.removeEventListener(listener) }
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

                    downloadState = DownloadState.ReadyToInstall(outputFile)
                    // Automatically trigger installer once downloaded
                    withContext(Dispatchers.Main) {
                        installApk(context, outputFile)
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
                onInstallNow = { file -> installApk(context, file) }
            )
        }
    }
}

fun installApk(context: Context, apkFile: File) {
    if (!apkFile.exists()) return

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
    context.startActivity(installIntent)
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
