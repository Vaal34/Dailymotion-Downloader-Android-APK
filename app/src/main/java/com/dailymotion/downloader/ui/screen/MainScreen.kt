package com.dailymotion.downloader.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailymotion.downloader.data.model.Download
import com.dailymotion.downloader.data.model.DownloadStatus
import com.dailymotion.downloader.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Couleurs Windows 95
private val Win95Gray = Color(0xFFC0C0C0)
private val Win95DarkGray = Color(0xFF808080)
private val Win95LightGray = Color(0xFFDFDFDF)
private val Win95Blue = Color(0xFF000080)
private val Win95White = Color(0xFFFFFFFF)
private val Win95Black = Color(0xFF000000)

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    sharedUrl: String? = null
) {
    var urlInput by remember { mutableStateOf(sharedUrl ?: "") }
    val downloads by viewModel.downloads.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrEmpty()) {
            urlInput = sharedUrl
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is MainViewModel.UiState.Success) {
            urlInput = ""
            kotlinx.coroutines.delay(3000)
            viewModel.resetUiState()
        } else if (uiState is MainViewModel.UiState.Error) {
            kotlinx.coroutines.delay(5000)
            viewModel.resetUiState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Win95Gray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Barre de titre Windows 95
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Win95Blue)
                    .padding(8.dp)
            ) {
                Text(
                    text = "Dailymotion Downloader",
                    color = Win95White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section de saisie d'URL
                Win95Panel {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Lien de la vidéo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Win95Black
                        )

                        Win95TextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = "Collez le lien Dailymotion ici"
                        )

                        Win95Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    viewModel.downloadVideo(urlInput)
                                }
                            },
                            enabled = urlInput.isNotBlank() && uiState !is MainViewModel.UiState.Loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (uiState is MainViewModel.UiState.Loading)
                                    "Téléchargement..."
                                else
                                    "Télécharger",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Messages de statut
                        when (val state = uiState) {
                            is MainViewModel.UiState.Success -> {
                                Text(
                                    text = state.message,
                                    color = Color(0xFF008000),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            is MainViewModel.UiState.Error -> {
                                Text(
                                    text = state.message,
                                    color = Color(0xFFFF0000),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            else -> {}
                        }
                    }
                }

                // Section historique
                Win95Panel {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Historique des téléchargements",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Win95Black,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (downloads.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aucun téléchargement",
                                    color = Win95DarkGray,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(downloads) { download ->
                                    DownloadItemWin95(
                                        download = download,
                                        onDelete = { viewModel.deleteDownload(download) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Win95Panel(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = Win95White,
            )
            .border(
                width = 2.dp,
                color = Win95DarkGray,
            )
            .background(Win95Gray)
    ) {
        content()
    }
}

@Composable
fun Win95TextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Win95DarkGray)
            .border(2.dp, Win95White)
            .background(Win95White)
            .padding(8.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Win95DarkGray,
                fontSize = 14.sp
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Win95Black,
                fontSize = 14.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Win95Button(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .border(2.dp, if (enabled) Win95White else Win95DarkGray)
            .border(2.dp, if (enabled) Win95DarkGray else Win95LightGray)
            .background(if (enabled) Win95Gray else Win95LightGray)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun DownloadItemWin95(
    download: Download,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Win95Panel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = download.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Win95Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (download.status) {
                            DownloadStatus.PENDING -> "En attente"
                            DownloadStatus.DOWNLOADING -> "Téléchargement ${download.progress}%"
                            DownloadStatus.COMPLETED -> "Terminé"
                            DownloadStatus.FAILED -> "Échec"
                            DownloadStatus.CANCELLED -> "Annulé"
                        },
                        fontSize = 11.sp,
                        color = when (download.status) {
                            DownloadStatus.COMPLETED -> Color(0xFF008000)
                            DownloadStatus.FAILED -> Color(0xFFFF0000)
                            else -> Win95DarkGray
                        }
                    )

                    if (download.status == DownloadStatus.COMPLETED && download.filePath != null) {
                        Text(
                            text = "• ${formatFileSize(download.fileSize)}",
                            fontSize = 11.sp,
                            color = Win95DarkGray
                        )
                    }
                }

                if (download.status == DownloadStatus.DOWNLOADING) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .border(1.dp, Win95DarkGray)
                            .background(Win95White)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(download.progress / 100f)
                                .background(Win95Blue)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (download.status == DownloadStatus.COMPLETED && download.filePath != null) {
                    Win95SmallButton(
                        onClick = {
                            val file = File(download.filePath)
                            if (file.exists()) {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        Uri.parse(file.absolutePath),
                                        "video/mp4"
                                    )
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(Intent.createChooser(intent, "Ouvrir avec"))
                            }
                        }
                    ) {
                        Text("Ouvrir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Win95SmallButton(onClick = onDelete) {
                    Text("Suppr.", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Win95SmallButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .border(1.dp, Win95White)
            .border(1.dp, Win95DarkGray)
            .background(Win95Gray)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        "%.1f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
}
