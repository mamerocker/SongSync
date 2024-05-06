package pl.lambada.songsync.ui.screens

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.lambada.songsync.R
import pl.lambada.songsync.data.EmptyQueryException
import pl.lambada.songsync.data.MainViewModel
import pl.lambada.songsync.data.NoTrackFoundException
import pl.lambada.songsync.domain.model.Song
import pl.lambada.songsync.domain.model.SongInfo
import pl.lambada.songsync.ui.components.CommonTextField
import pl.lambada.songsync.ui.components.SongCard
import pl.lambada.songsync.util.ext.toLrcFile
import java.io.File
import java.io.FileNotFoundException
import java.net.UnknownHostException

/**
 * Composable function for BrowseScreen component.
 *
 * @param viewModel the [MainViewModel] instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    navController: NavController,
) {

    var nextSong: Song? by rememberSaveable { mutableStateOf(null) }
    if (viewModel.nextSong != null) {
        nextSong = viewModel.nextSong
        viewModel.nextSong = null
    }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            MediumTopAppBar(
                navigationIcon = {
                                 IconButton(
                                     onClick = {
                                         navController.popBackStack(
                                             navController.graph.startDestinationId,
                                             false
                                         )
                                     }
                                 ) {
                                     Icon(
                                         imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                         contentDescription = stringResource(R.string.back),
                                     )
                                 }
                },
                title = { Text(text = stringResource(id = R.string.search)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val generatedUsingString = stringResource(id = R.string.generated_using)

            // queryStatus: "Not submitted", "Pending", "Success", "Failed" - used to show different UI
            var queryStatus by rememberSaveable {
                mutableStateOf(
                    if (nextSong != null) QueryStatus.Pending else QueryStatus.NotSubmitted
                )
            }

            // querySong, queryArtist - used to store user input, offset - for search again
            var querySong by rememberSaveable { mutableStateOf(nextSong?.title ?: "") }
            var queryArtist by rememberSaveable { mutableStateOf(nextSong?.artist ?: "") }
            var offset by rememberSaveable { mutableIntStateOf(0) }

            // queryResult - used to store result of query, failReason - used to store error message if error occurs
            var queryResult: SongInfo? by rememberSaveable {
                mutableStateOf(
                    SongInfo(
                        songName = querySong, artistName = queryArtist
                    )
                )
            }
            var failReason: Exception? by rememberSaveable { mutableStateOf(null) }

            Spacer(modifier = Modifier.height(16.dp))
            if (nextSong != null) {
                Row {
                    Icon(
                        imageVector = Icons.Filled.Downloading,
                        contentDescription = null,
                        Modifier.padding(end = 5.dp)
                    )
                    Text(stringResource(R.string.local_song))
                }
                SongCard(
                    songName = nextSong?.title ?: stringResource(id = R.string.unknown),
                    artists = nextSong?.artist ?: stringResource(id = R.string.unknown),
                    coverUrl = nextSong?.imgUri?.toString()
                )
            }

            when (queryStatus) {
                QueryStatus.NotSubmitted -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CommonTextField(
                        value = querySong,
                        onValueChange = { querySong = it.toString() },
                        label = stringResource(id = R.string.song_name_no_args),
                        imeAction = ImeAction.Next,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CommonTextField(
                        value = queryArtist,
                        onValueChange = { queryArtist = it.toString() },
                        label = stringResource(R.string.artist_name_no_args),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        queryResult = SongInfo(
                            songName = querySong, artistName = queryArtist
                        )
                        offset = 0
                        queryStatus = QueryStatus.Pending
                    }) {
                        Text(text = stringResource(id = R.string.get_lyrics))
                    }
                }

                QueryStatus.Pending -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                    LaunchedEffect(Unit) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                queryResult = viewModel.getSongInfo(queryResult!!, offset)
                                queryStatus = QueryStatus.Success
                            } catch (e: Exception) {
                                when (e) {
                                    is UnknownHostException -> {
                                        queryStatus = QueryStatus.NoConnection
                                    }

                                    else -> {
                                        queryStatus = QueryStatus.Failed
                                        failReason = e
                                    }
                                }
                            }
                        }
                    }
                }

                QueryStatus.Success -> {
                    val result = queryResult!!
                    Row {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            Modifier.padding(end = 5.dp)
                        )
                        Text(stringResource(R.string.cloud_song))
                    }
                    SongCard(
                        songName = result.songName ?: stringResource(id = R.string.unknown),
                        artists = result.artistName ?: stringResource(id = R.string.unknown),
                        coverUrl = result.albumCoverLink,
                        modifier = Modifier.clickable { result.songLink?.let { uriHandler.openUri(it) } }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            enabled = viewModel.provider != Providers.LRCLIB,
                            onClick = {
                                offset += 1
                                queryResult = SongInfo(
                                    songName = querySong, artistName = queryArtist
                                )
                                queryStatus = QueryStatus.Pending
                            }) {
                            Text(text = stringResource(id = R.string.try_again))
                        }
                        OutlinedButton(
                            onClick = {
                                queryStatus = QueryStatus.NotSubmitted
                            }) {
                            Text(text = stringResource(id = R.string.edit))
                        }
                    }

                    var lyricsResult: String? by rememberSaveable { mutableStateOf(null) }
                    var lyricSuccess by rememberSaveable { mutableStateOf(LyricsStatus.NotSubmitted) }

                    LaunchedEffect(Unit) {
                        launch(Dispatchers.IO) {
                            try {
                                if (lyricSuccess == LyricsStatus.NotSubmitted) {
                                    lyricsResult = viewModel.getSyncedLyrics(result.songLink ?: "")
                                    if (lyricsResult == null)
                                        throw NullPointerException("lyricsResult is null")
                                    else
                                        lyricSuccess = LyricsStatus.Success
                                }
                            } catch (e: Exception) {
                                lyricsResult = e.toString()
                                lyricSuccess = LyricsStatus.Failed
                                if (e is FileNotFoundException || e is NullPointerException) {
                                    lyricsResult = null // Lyrics not found message
                                }
                            }
                        }
                    }

                    when (lyricSuccess) {
                        LyricsStatus.NotSubmitted -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            CircularProgressIndicator()
                        }

                        LyricsStatus.Success -> {
                            // must be non-null
                            val lyrics = lyricsResult!!

                            val isLegacyVersion = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                            val isInternalStorage = nextSong?.filePath?.contains("/storage/emulated/0/")
                                ?: true // true because it's not a local song

                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val lrc =
                                            "[ti:${result.songName}]\n" + "[ar:${result.artistName}]\n" + "[by:$generatedUsingString]\n" + lyrics
                                        val file = nextSong?.filePath?.toLrcFile() ?: File(
                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                            "SongSync/${result.songName} - ${result.artistName}.lrc"
                                        )
                                        if (!isLegacyVersion || isInternalStorage) {
                                            file.writeText(lrc)
                                        } else {
                                            val sd =
                                                context.externalCacheDirs[1].absolutePath.substring(
                                                    0,
                                                    context.externalCacheDirs[1].absolutePath.indexOf("/Android/data")
                                                )
                                            val path =
                                                nextSong!!.filePath?.toLrcFile()?.absolutePath?.substringAfter(
                                                    sd
                                                )?.split("/")?.dropLast(1)
                                            var sdCardFiles = DocumentFile.fromTreeUri(
                                                context,
                                                Uri.parse(viewModel.sdCardPath)
                                            )
                                            for (element in path!!) {
                                                for (sdCardFile in sdCardFiles!!.listFiles()) {
                                                    if (sdCardFile.name == element) {
                                                        sdCardFiles = sdCardFile
                                                    }
                                                }
                                            }
                                            sdCardFiles?.listFiles()?.forEach {
                                                if (it.name == file.name) {
                                                    it.delete()
                                                    return@forEach
                                                }
                                            }
                                            sdCardFiles?.createFile(
                                                "text/lrc",
                                                file.name
                                            )?.let {
                                                val outputStream = context.contentResolver.openOutputStream(it.uri)
                                                outputStream?.write(lrc.toByteArray())
                                                outputStream?.close()
                                            }
                                        }

                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.file_saved_to,
                                                file.absolutePath
                                            ),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                ) {
                                    Text(text = stringResource(R.string.save_lrc_file))
                                }
                                val clipboardManager = LocalClipboardManager.current
                                val copiedString = stringResource(R.string.lyrics_copied_to_clipboard)
                                OutlinedButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(lyrics))
                                        Toast.makeText(
                                            context,
                                            copiedString,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_lyrics_to_clipboard)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = lyrics, modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        LyricsStatus.Failed -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = lyricsResult
                                    ?: stringResource(id = R.string.this_track_has_no_lyrics)
                            )
                        }
                    }

                }

                QueryStatus.Failed -> {
                    AlertDialog(onDismissRequest = { queryStatus = QueryStatus.NotSubmitted },
                        confirmButton = {
                            Button(onClick = { queryStatus = QueryStatus.NotSubmitted }) {
                                Text(text = stringResource(id = R.string.ok))
                            }
                        },
                        title = { Text(text = stringResource(id = R.string.error)) },
                        text = {
                            when (failReason) {
                                is NoTrackFoundException -> {
                                    Text(
                                        text = stringResource(R.string.no_results)
                                    )
                                }

                                is EmptyQueryException -> {
                                    Text(
                                        text = stringResource(R.string.invalid_query)
                                    )
                                }

                                is FileNotFoundException -> { // Rate limit
                                    Column {
                                        Text(text = stringResource(R.string.spotify_api_rate_limit_reached))
                                        Text(text = stringResource(R.string.please_try_again_later))
                                        Text(text = stringResource(R.string.change_api_strategy))
                                    }
                                }

                                else -> {
                                    Text(text = failReason.toString())
                                }
                            }
                        })
                }

                QueryStatus.NoConnection -> {
                    AlertDialog(
                        onDismissRequest = { queryStatus = QueryStatus.NotSubmitted },
                        confirmButton = {
                            Button(onClick = { queryStatus = QueryStatus.NotSubmitted }) {
                                Text(text = stringResource(id = R.string.ok))
                            }
                        },
                        title = { Text(text = stringResource(id = R.string.error)) },
                        text = {
                            Text(text = stringResource(id = R.string.no_internet_server))
                        }
                    )
                }
            }
        }
    }
}

private enum class LyricsStatus {
    NotSubmitted, Success, Failed
}

private enum class QueryStatus {
    NotSubmitted, Pending, Success, Failed, NoConnection
}