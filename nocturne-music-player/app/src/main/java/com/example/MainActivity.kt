package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import coil.compose.AsyncImage
import com.example.media.ArtworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.ui.theme.AccentColorsList
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val viewModel: MusicViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()

            MyApplicationTheme(
                themeMode = settings.themeMode,
                accentColorIndex = settings.accentColorIndex,
                fontSize = settings.fontSize
            ) {
                MainAppLayout(viewModel = viewModel, settings = settings)
            }
        }
    }
}

@Composable
fun MainAppLayout(
    viewModel: MusicViewModel,
    settings: PlayerSettings
) {
    val haptic = LocalHapticFeedback.current
    var activeTab by rememberSaveable { mutableStateOf("library") }
    var showNowPlaying by remember { mutableStateOf(false) }

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val importingState by viewModel.importingState.collectAsState()

    val gradientBackground = Brush.verticalGradient(
        colors = if (settings.themeMode == "LIGHT") {
            listOf(Color(0xFFF3F4FA), Color(0xFFE3E5F3))
        } else {
            listOf(Color(0xFF0A0C16), Color(0xFF13172E))
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main content based on active tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (settings.animationsEnabled) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "tab_transition"
                    ) { tab ->
                        TabContent(tab = tab, viewModel = viewModel, settings = settings)
                    }
                } else {
                    TabContent(tab = activeTab, viewModel = viewModel, settings = settings)
                }

                // Global importing status Toast/Banner overlay
                importingState?.let { msg ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom Player controller (Mini Player) + Navigation Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
            ) {
                // Persistent mini player
                currentSong?.let { song ->
                    MiniPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        settings = settings,
                        onClick = {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showNowPlaying = true
                        }
                    )
                }

                // Rounded custom glassmorphic bottom navigation
                BottomNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { tab ->
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        activeTab = tab
                    }
                )
            }
        }

        // Expanded/Full-Screen Now Playing overlay
        if (showNowPlaying && currentSong != null) {
            NowPlayingScreen(
                song = currentSong!!,
                isPlaying = isPlaying,
                viewModel = viewModel,
                settings = settings,
                onClose = { showNowPlaying = false }
            )
        }
    }
}

// -----------------------------------------------------
// Primary Screens Content Routing
// -----------------------------------------------------

@Composable
fun TabContent(
    tab: String,
    viewModel: MusicViewModel,
    settings: PlayerSettings
) {
    when (tab) {
        "library" -> LibraryScreen(viewModel = viewModel, settings = settings)
        "playlists" -> PlaylistsScreen(viewModel = viewModel, settings = settings)
        "search" -> SearchScreen(viewModel = viewModel, settings = settings)
        "settings" -> SettingsScreen(viewModel = viewModel, settings = settings)
    }
}

// -----------------------------------------------------
// Navigation Bar
// -----------------------------------------------------

@Composable
fun BottomNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    val navItems = listOf(
        Triple("library", "Library", Icons.Default.LibraryMusic),
        Triple("playlists", "Playlists", Icons.Default.FeaturedPlayList),
        Triple("search", "Search", Icons.Default.Search),
        Triple("settings", "Settings", Icons.Default.Settings)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { (route, label, icon) ->
                val isSelected = activeTab == route
                val backgroundAlpha by animateFloatAsState(if (isSelected) 0.15f else 0f, label = "tab_pill")
                val tintColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = backgroundAlpha))
                        .clickable { onTabSelected(route) }
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .testTag("nav_tab_$route"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tintColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------
// Library Screen
// -----------------------------------------------------

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    settings: PlayerSettings
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val songList by viewModel.songs.collectAsState()
    var showGeneratorDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = getFileName(context, uri)
            viewModel.importSongFromUri(uri, fileName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // Top row header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "MUSIC PLAYER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quick Import & Synthesizer Trigger
            Row {
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showGeneratorDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Procedural Zen Synth",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        filePickerLauncher.launch("audio/*")
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (songList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Your library is empty. Import songs from your device to start listening – everything stays on this device.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            filePickerLauncher.launch("audio/*")
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Songs", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showGeneratorDialog = true
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Zen Ambient", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(songList, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        viewModel = viewModel,
                        hapticEnabled = settings.hapticFeedbackEnabled,
                        onPlay = {
                            viewModel.playSong(song, songList)
                        }
                    )
                }
            }
        }
    }

    // Procedural Generator Dialog
    if (showGeneratorDialog) {
        ProceduralSynthDialog(
            onDismiss = { showGeneratorDialog = false },
            onGenerate = { name, type ->
                viewModel.generateZenSynthTrack(name, type)
                showGeneratorDialog = false
            },
            onDownloadDemo = { title, artist, album, url ->
                viewModel.downloadPremiumDemoTrack(title, artist, album, url)
                showGeneratorDialog = false
            }
        )
    }
}

@Composable
fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    showLetter: Boolean = true
) {
    val context = LocalContext.current
    var artworkFile by remember(song.id, song.filePath) {
        mutableStateOf<File?>(null)
    }

    LaunchedEffect(song.id, song.filePath) {
        withContext(Dispatchers.IO) {
            artworkFile = ArtworkHelper.getArtworkFile(context, song.filePath)
        }
    }

    val file = artworkFile
    if (file != null && file.exists()) {
        AsyncImage(
            model = file,
            contentDescription = "Album artwork for ${song.title}",
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback beautiful generative/colored dynamic gradient
        val colors = ArtworkHelper.getPlaceholderGradient(song.title, song.artist)
        val brush = Brush.linearGradient(
            colors = listOf(Color(colors[0]), Color(colors[1]))
        )
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            if (showLetter && song.title.isNotEmpty()) {
                Text(
                    text = song.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    viewModel: MusicViewModel,
    hapticEnabled: Boolean,
    onPlay: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var expandedMenu by remember { mutableStateOf(false) }
    var showPlaylistsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(
                song = song,
                modifier = Modifier.size(52.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Box {
                IconButton(
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        expandedMenu = true
                    }
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Song options")
                }

                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            expandedMenu = false
                            showPlaylistsDialog = true
                        },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Song") },
                        onClick = {
                            expandedMenu = false
                            viewModel.deleteSongFromLibrary(song)
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }

    if (showPlaylistsDialog) {
        val playlistsFlow by viewModel.playlists.collectAsState()
        Dialog(onDismissRequest = { showPlaylistsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Add to Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (playlistsFlow.isEmpty()) {
                        Text(
                            text = "No playlists found. Create one in the Playlists tab!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(playlistsFlow) { playlist ->
                                TextButton(
                                    onClick = {
                                        viewModel.addSongToUserPlaylist(playlist.id, song.id)
                                        showPlaylistsDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FeaturedPlayList, contentDescription = null)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showPlaylistsDialog = false },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// Playlists Screen
// -----------------------------------------------------

@Composable
fun PlaylistsScreen(
    viewModel: MusicViewModel,
    settings: PlayerSettings
) {
    val haptic = LocalHapticFeedback.current
    val playlistsFlow by viewModel.playlistsWithSongs.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<PlaylistWithSongs?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MUSIC PLAYER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Playlists",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // "New Playlist" tall capsule button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showCreateDialog = true
                },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "New Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (playlistsFlow.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Create playlists to organise your music",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(playlistsFlow, key = { it.playlist.id }) { pws ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedPlaylistForDetail = pws
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pws.playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${pws.songs.size} song" + (if (pws.songs.size == 1) "" else "s") + if (pws.playlist.description.isNotEmpty()) " • ${pws.playlist.description}" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Playlist Details Overlay Screen
    selectedPlaylistForDetail?.let { activePws ->
        PlaylistDetailScreen(
            playlistWithSongs = activePws,
            viewModel = viewModel,
            settings = settings,
            onClose = { selectedPlaylistForDetail = null }
        )
    }

    // New Playlist Dialog
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        var playlistDesc by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Create Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist Name") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = playlistDesc,
                        onValueChange = { playlistDesc = it },
                        label = { Text("Description (Optional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateDialog = false },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (playlistName.isNotBlank()) {
                                    viewModel.createNewPlaylist(playlistName, playlistDesc)
                                    showCreateDialog = false
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create")
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// Playlist Detail Screen Overlay
// -----------------------------------------------------

@Composable
fun PlaylistDetailScreen(
    playlistWithSongs: PlaylistWithSongs,
    viewModel: MusicViewModel,
    settings: PlayerSettings,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val playlistsFlow by viewModel.playlistsWithSongs.collectAsState()
    val pws = playlistsFlow.firstOrNull { it.playlist.id == playlistWithSongs.playlist.id } ?: playlistWithSongs

    var showAddSongsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
                }

                Row {
                    IconButton(
                        onClick = {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddSongsDialog = true
                        }
                    ) {
                        Icon(imageVector = Icons.Default.PlaylistAdd, contentDescription = "Add songs")
                    }
                    IconButton(
                        onClick = {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteUserPlaylist(pws.playlist)
                            onClose()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete playlist",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlist Meta Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pws.playlist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (pws.playlist.description.isNotEmpty()) {
                        Text(
                            text = pws.playlist.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "${pws.songs.size} track" + (if (pws.songs.size == 1) "" else "s"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Play All button
            if (pws.songs.isNotEmpty()) {
                Button(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.playSong(pws.songs.first(), pws.songs)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Playlist", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Playlist songs list
            if (pws.songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "This playlist is empty.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { showAddSongsDialog = true }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add songs from your library")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(pws.songs) { song ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playSong(song, pws.songs)
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongArtwork(
                                    song = song,
                                    modifier = Modifier.size(44.dp),
                                    cornerRadius = 8.dp
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }

                                Text(
                                    text = formatDuration(song.durationMs),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )

                                IconButton(
                                    onClick = {
                                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.removeSongFromUserPlaylist(pws.playlist.id, song.id)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RemoveCircleOutline,
                                        contentDescription = "Remove song",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Songs Checklist Dialog
    if (showAddSongsDialog) {
        val librarySongs by viewModel.songs.collectAsState()

        Dialog(onDismissRequest = { showAddSongsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Add Songs to Playlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (librarySongs.isEmpty()) {
                        Text(
                            text = "Import songs to your library first!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(librarySongs) { song ->
                                val alreadyInPlaylist = pws.songs.any { it.id == song.id }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (alreadyInPlaylist) {
                                                viewModel.removeSongFromUserPlaylist(pws.playlist.id, song.id)
                                            } else {
                                                viewModel.addSongToUserPlaylist(pws.playlist.id, song.id)
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = alreadyInPlaylist,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                viewModel.addSongToUserPlaylist(pws.playlist.id, song.id)
                                            } else {
                                                viewModel.removeSongFromUserPlaylist(pws.playlist.id, song.id)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showAddSongsDialog = false },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Finished")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// Search Screen
// -----------------------------------------------------

@Composable
fun SearchScreen(
    viewModel: MusicViewModel,
    settings: PlayerSettings
) {
    val haptic = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    val songsFlow by viewModel.songs.collectAsState()

    val filteredSongs = remember(query, songsFlow) {
        if (query.isBlank()) {
            emptyList()
        } else {
            songsFlow.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MUSIC PLAYER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search your library") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            query = ""
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (query.isBlank()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Find songs by title, artist, or album",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else if (filteredSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tracks match '$query'",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredSongs, key = { it.id }) { song ->
                    SongListItem(
                        song = song,
                        viewModel = viewModel,
                        hapticEnabled = settings.hapticFeedbackEnabled,
                        onPlay = {
                            viewModel.playSong(song, filteredSongs)
                        }
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------
// Settings Screen
// -----------------------------------------------------

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    settings: PlayerSettings
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "MUSIC PLAYER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 1. DISPLAY SECTION
        Text(
            text = "DISPLAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Theme Toggle
                Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSegmentedRow(
                    options = listOf("LIGHT", "DARK", "AUTO"),
                    selected = settings.themeMode,
                    onSelected = { viewModel.updateThemeMode(it) },
                    labelProvider = {
                        when (it) {
                            "LIGHT" -> "☀ Light"
                            "DARK" -> "☾ Dark"
                            else -> "☖ Auto"
                        }
                    },
                    hapticEnabled = settings.hapticFeedbackEnabled
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Accent Color selector
                Text("Accent color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccentColorsList.forEachIndexed { index, color ->
                        val isSelected = settings.accentColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.updateAccentColorIndex(index)
                                }
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (index == 3) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Font Size Selector
                Text("Font size", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSegmentedRow(
                    options = listOf("S", "M", "L", "XL"),
                    selected = settings.fontSize,
                    onSelected = { viewModel.updateFontSizeValue(it) },
                    labelProvider = { it },
                    hapticEnabled = settings.hapticFeedbackEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. PLAYER SECTION
        Text(
            text = "PLAYER",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Player Style
                Text("Player style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSegmentedRow(
                    options = listOf("COMPACT", "EXPANDED", "MINIMALIST", "DETAILED"),
                    selected = settings.playerStyle,
                    onSelected = { viewModel.updatePlayerStyleValue(it) },
                    labelProvider = { it.lowercase().replaceFirstChar { char -> char.uppercase() } },
                    hapticEnabled = settings.hapticFeedbackEnabled
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Playback speed
                Text("Default speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSegmentedRow(
                    options = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f),
                    selected = settings.playbackSpeed,
                    onSelected = { viewModel.updatePlaybackSpeedValue(it) },
                    labelProvider = { "${it}x" },
                    hapticEnabled = settings.hapticFeedbackEnabled
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Audio Quality
                Text("Audio quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSegmentedRow(
                    options = listOf("LOW", "MEDIUM", "HIGH", "LOSSLESS"),
                    selected = settings.audioQuality,
                    onSelected = { viewModel.updateAudioQualityValue(it) },
                    labelProvider = { it.lowercase().replaceFirstChar { char -> char.uppercase() } },
                    hapticEnabled = settings.hapticFeedbackEnabled
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. AUDIO & FEEDBACK SECTION
        Text(
            text = "AUDIO & FEEDBACK",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Haptic feedback switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptic feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = { viewModel.updateHapticFeedbackValue(it) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Animations switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Animations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = settings.animationsEnabled,
                        onCheckedChange = { viewModel.updateAnimationsValue(it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. LANGUAGE & REGION SECTION
        Text(
            text = "LANGUAGE & REGION",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                val languages = listOf("English", "Español", "Français", "Deutsch", "日本語", "中文")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(languages) { lang ->
                        val isSelected = settings.language == lang
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable {
                                    if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.updateLanguageValue(lang)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. ABOUT SECTION
        Text(
            text = "ABOUT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "1.0.0 (100)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.resetSettingsToDefaults()
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reset to defaults", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Settings",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "MUSIC PLAYER • All songs stay on your device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            textAlign = TextAlign.Center
        )
    }
}

// -----------------------------------------------------
// Generic Segmented Row Component
// -----------------------------------------------------

@Composable
fun <T> SettingsSegmentedRow(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    hapticEnabled: Boolean
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val animColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "pill_bg_anim"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(animColor)
                    .clickable {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onSelected(option)
                    }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(option),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp
                    ),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -----------------------------------------------------
// Mini Player Component
// -----------------------------------------------------

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    settings: PlayerSettings,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val position by viewModel.currentPosition.collectAsState()
    val progress = if (song.durationMs > 0) position.toFloat() / song.durationMs else 0f

    // Gestures for Swipe-to-Skip
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "swipe_offset")
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    // Pulsing shadow scale for glowing disk halo
    val pulseScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.25f else 1.0f,
        animationSpec = if (isPlaying) {
            infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            snap()
        },
        label = "glow_pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -swipeThresholdPx) {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.nextTrack()
                        } else if (offsetX > swipeThresholdPx) {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.previousTrack()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = {
                        offsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount
                    }
                )
            }
            .clickable { onClick() }
            .testTag("mini_player"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            // Elegant micro progress indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Disk artwork with active glowing halo pulse
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp)
                ) {
                    // Pulsing glow behind disk
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    )

                    // Spinning album disk inside Mini Player
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (isPlaying) 360f else 0f,
                        animationSpec = if (isPlaying) {
                            infiniteRepeatable(
                                animation = tween(12000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        } else {
                            snap()
                        },
                        label = "mini_art_rotation"
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.tertiary,
                                        MaterialTheme.colorScheme.primary
                                    )
                                )
                            )
                            .graphicsLayer { rotationZ = rotationAngle },
                        contentAlignment = Alignment.Center
                    ) {
                        // Vinyl record center lines
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f))
                        )
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Animated micro equalizer
                        if (isPlaying) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                repeat(3) { index ->
                                    val barHeight by animateFloatAsState(
                                        targetValue = if (isPlaying) (4f + (index * 3f) + (Math.random() * 8f).toFloat()) else 4f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(durationMillis = 300 + index * 100, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "mini_eq_$index"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(barHeight.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(1.dp)
                                            )
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Play / Pause control with tactile visual feedback
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.togglePlayPause()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Skip Next control with tactile visual feedback
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.nextTrack()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip track",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// Now Playing Full-Screen View
// -----------------------------------------------------

@Composable
fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    settings: PlayerSettings,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val position by viewModel.currentPosition.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val progress = if (song.durationMs > 0) position.toFloat() / song.durationMs else 0f

    val playerStyle = settings.playerStyle
    val songColors = ArtworkHelper.getPlaceholderGradient(song.title, song.artist)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(songColors[0]).copy(alpha = 0.28f),
            Color(songColors[1]).copy(alpha = 0.08f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(backgroundBrush)
            .windowInsetsPadding(WindowInsets.statusBars)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* block clicks */ }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Top action header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Playback Presentation according to "Player Style" Setting
            when (playerStyle) {
                "COMPACT" -> {
                    // Small visual presentation with simple card
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Card(
                            modifier = Modifier
                                .size(240.dp)
                                .shadow(16.dp, RoundedCornerShape(28.dp)),
                            shape = RoundedCornerShape(28.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                        ) {
                            SongArtwork(
                                song = song,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 28.dp,
                                showLetter = true
                            )
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                "EXPANDED" -> {
                    // Beautiful floating turntable disk art (Default style)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val rotationAngle by animateFloatAsState(
                            targetValue = if (isPlaying) 360f else 0f,
                            animationSpec = if (isPlaying) {
                                infiniteRepeatable(
                                    animation = tween(16000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                )
                            } else {
                                snap()
                            },
                            label = "turntable_rotation"
                        )

                        // Circular turntable album frame with an elegant glowing pulse halo behind it
                        Box(
                            modifier = Modifier
                                .size(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Pulsing glowing background halo
                            val pulseScale by animateFloatAsState(
                                targetValue = if (isPlaying) 1.08f else 1.0f,
                                animationSpec = if (isPlaying) {
                                    infiniteRepeatable(
                                        animation = tween(2000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    )
                                } else {
                                    snap()
                                },
                                label = "album_glowing_pulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color(songColors[0]).copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .border(6.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    .graphicsLayer { rotationZ = rotationAngle },
                                contentAlignment = Alignment.Center
                            ) {
                                // Turntable grooved vinyl record simulation
                                Box(
                                    modifier = Modifier
                                        .size(250.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F0F11))
                                        .border(2.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                                ) {
                                    // Fine grooved rings
                                    Box(
                                        modifier = Modifier
                                            .size(210.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.03f), CircleShape)
                                            .align(Alignment.Center)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color.White.copy(alpha = 0.03f), CircleShape)
                                            .align(Alignment.Center)
                                    )

                                    // Inner custom artwork ring in the vinyl center
                                    SongArtwork(
                                        song = song,
                                        modifier = Modifier
                                            .size(100.dp)
                                            .align(Alignment.Center),
                                        cornerRadius = 50.dp,
                                        showLetter = false
                                    )

                                    // Spindle hole
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                            .align(Alignment.Center)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(44.dp))

                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                "MINIMALIST" -> {
                    // Bare typography & generous white space
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Card(
                            modifier = Modifier
                                .size(260.dp)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            SongArtwork(
                                song = song,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 16.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 36.sp,
                                lineHeight = 42.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                "DETAILED" -> {
                    // Advanced audio telemetry details and visual equalizer!
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // High quality technical card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongArtwork(
                                    song = song,
                                    modifier = Modifier.size(64.dp),
                                    cornerRadius = 12.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "AUDIO STREAM METADATA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Codec: FLAC / PCM", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("Sample: 44.1 kHz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Bitrate: 1411 kbps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text("Channel: Lossless Stereo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                            }
                        }

                        // Live Equalizer visual bars
                        LiveEqualizer(isPlaying = isPlaying)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Audio seek bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = { newProgress ->
                        viewModel.seekTo((newProgress * song.durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(position),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "-" + formatDuration((song.durationMs - position).coerceAtLeast(0)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Primary Media Controllers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleShuffle()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous button
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.previousTrack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Giant Circular Play/Pause FAB
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.nextTrack()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode Toggle
                IconButton(
                    onClick = {
                        if (settings.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleRepeatMode()
                    }
                ) {
                    val repeatIcon = when (repeatMode) {
                        "ONE" -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    val isRepeatActive = repeatMode != "NONE"
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------
// Equalizer Visualizer (Detailed style)
// -----------------------------------------------------

@Composable
fun LiveEqualizer(isPlaying: Boolean) {
    val barCount = 10
    val heights = remember { mutableStateListOf(*Array(barCount) { 0.1f }) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                for (i in 0 until barCount) {
                    heights[i] = (0.15f + Math.random().toFloat() * 0.85f)
                }
                delay(120)
            }
        } else {
            for (i in 0 until barCount) {
                heights[i] = 0.1f
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until barCount) {
            val animHeight by animateFloatAsState(
                targetValue = heights[i],
                animationSpec = tween(120, easing = LinearOutSlowInEasing),
                label = "eq_bar"
            )
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight(animHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
        }
    }
}

// -----------------------------------------------------
// Procedural Synthesizer Dialog
// -----------------------------------------------------

@Composable
fun ProceduralSynthDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, String) -> Unit,
    onDownloadDemo: (String, String, String, String) -> Unit
) {
    var customName by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("lofi") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Offline Synth & Demos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Generate synthesized background tracks completely offline or fetch high-quality royalty-free demo tracks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Title field
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Track Title") },
                    placeholder = { Text("e.g. Midnight Solitude") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Track mood selector
                Text(
                    "Select Synth Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("lofi", "sleep", "relax").forEach { type ->
                        val isSelected = selectedGenre == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedGenre = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val title = if (customName.isBlank()) "Cosmic ${selectedGenre.uppercase()} Drone" else customName
                        onGenerate(title, selectedGenre)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Zen Track (Offline)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Premium Demo audio links (lofi beat, acoustic guitar)
                Text(
                    "OR PRE-FETCH DEMOS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                OutlinedButton(
                    onClick = {
                        onDownloadDemo(
                            "Warm Lofi Beat",
                            "Offline Vibe Maker",
                            "Chilled Royalty Free Samples",
                            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Chilled Lofi Sample")
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = {
                        onDownloadDemo(
                            "Acoustic Melodies",
                            "Woodland Strummer",
                            "Strings of Serenity",
                            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Acoustic Sample")
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

// -----------------------------------------------------
// SAF Display File Name Helper
// -----------------------------------------------------

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}

// -----------------------------------------------------
// Duration Format Helper
// -----------------------------------------------------

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
