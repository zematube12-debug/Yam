package com.example.notes

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Gold = Color(0xFFB8935A)
private val Cream = Color(0xFFFBF7F0)
private val Ink = Color(0xFF2B2623)
private val WoodBackground = Color(0xFF1A1410)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Gold, background = Cream, surface = Cream,
                    onBackground = Ink, onSurface = Ink
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Cream) {
                    WeddingApp()
                }
            }
        }
    }
}

private fun copyPhoto(ctx: Context, uri: Uri, dir: File, i: Int): String? {
    return try {
        val name = "%013d_%03d.jpg".format(System.currentTimeMillis(), i)
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        input.use { src -> File(dir, name).outputStream().use { src.copyTo(it) } }
        name
    } catch (e: Exception) {
        null
    }
}

@Composable
fun WeddingApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("wedding", Context.MODE_PRIVATE) }
    val dir = remember { File(ctx.filesDir, "photos").apply { mkdirs() } }
    val scope = rememberCoroutineScope()

    var groom by remember { mutableStateOf(prefs.getString("groom", "") ?: "") }
    var bride by remember { mutableStateOf(prefs.getString("bride", "") ?: "") }
    var date by remember { mutableStateOf(prefs.getString("date", "") ?: "") }
    var place by remember { mutableStateOf(prefs.getString("place", "") ?: "") }

    val photos = remember { mutableStateListOf<String>().apply { addAll(dir.list()?.sorted() ?: emptyList()) } }
    val favs = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("fav", emptySet()) ?: emptySet()) } }

    var onCover by remember { mutableStateOf(true) }
    var showFavs by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    var slideshow by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(groom.isBlank() && bride.isBlank()) }

    fun saveFavs() { prefs.edit().putStringSet("fav", favs.toSet()).apply() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            scope.launch {
                val added = withContext(Dispatchers.IO) { uris.mapIndexedNotNull { i, uri -> copyPhoto(ctx, uri, dir, i) } }
                photos.addAll(added)
                importing = false
            }
        }
    }

    val visible = if (showFavs) photos.filter { it in favs } else photos.toList()
    val title = listOf(groom, bride).filter { it.isNotBlank() }.joinToString(" & ").ifBlank { "Our Wedding" }

    BackHandler(enabled = viewer != null || !onCover) {
        if (viewer != null) { viewer = null; slideshow = false } else { onCover = true }
    }

    Box(Modifier.fillMaxSize()) {
        if (onCover) {
            Cover(
                coverFile = photos.firstOrNull()?.let { File(dir, it) },
                groom = groom, bride = bride, date = date, place = place,
                onOpen = { onCover = false }, onEdit = { showSetup = true }
            )
        } else {
            Gallery(
                dir = dir, visible = visible, total = photos.size, showFavs = showFavs, title = title,
                onFilter = { showFavs = it }, onBack = { onCover = true }, onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onPlay = { if (visible.isNotEmpty()) { viewer = 0; slideshow = true } }, onOpen = { viewer = it }
            )
        }

        viewer?.let { start ->
            Viewer(
                dir = dir, files = visible, start = start, favs = favs, slideshow = slideshow,
                onSlideshow = { slideshow = it },
                onFav = { n -> if (n in favs) favs.remove(n) else favs.add(n); saveFavs() },
                onDelete = { n -> File(dir, n).delete(); photos.remove(n); favs.remove(n); saveFavs() },
                onClose = { viewer = null; slideshow = false }
            )
        }

        if (importing) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
        }
    }

    if (showSetup) {
        SetupDialog(
            groom = groom, bride = bride, date = date, place = place,
            onSave = { g, b, d, p ->
                groom = g; bride = b; date = d; place = p
                prefs.edit().putString("groom", g).putString("bride", b).putString("date", d).putString("place", p).apply()
                showSetup = false
            },
            onDismiss = { showSetup = false }
        )
    }
}

// 1. REALISTIC 3D COVER DESIGN
@Composable
fun Cover(coverFile: File?, groom: String, bride: String, date: String, place: String, onOpen: () -> Unit, onEdit: () -> Unit) {
    Box(Modifier.fillMaxSize().background(WoodBackground), contentAlignment = Alignment.Center) {
        // Book Body Shape
        val bookShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 32.dp, bottomEnd = 32.dp)
        
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 24.dp, top = 40.dp, bottom = 40.dp)
                .shadow(elevation = 30.dp, shape = bookShape) // Heavy 3D Shadow
                .clip(bookShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF3A2E27), Color(0xFF1C1612))))
        ) {
            if (coverFile != null) {
                AsyncImage(model = coverFile, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xCC000000)))))
            }
            
            // Realistic Book Spine (የማሰሪያው ጥቁረት)
            Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(50.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x44000000), Color.Transparent)))
            )
            
            // Gold Border line on cover
            Box(Modifier.fillMaxSize().padding(24.dp).border(BorderStroke(1.dp, Gold.copy(alpha = 0.5f))))

            IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
            }

            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("OUR WEDDING", color = Gold, fontSize = 14.sp, letterSpacing = 4.sp)
                Spacer(Modifier.height(24.dp))
                Text(groom, color = Color.White, fontFamily = FontFamily.Cursive, fontSize = 52.sp, textAlign = TextAlign.Center)
                Text("&", color = Gold, fontSize = 28.sp)
                Text(bride, color = Color.White, fontFamily = FontFamily.Cursive, fontSize = 52.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Text(listOf(date, place).filter { it.isNotBlank() }.joinToString("  •  "), color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
                OutlinedButton(onClick = onOpen, border = BorderStroke(1.dp, Gold)) { Text("Open Album", color = Gold) }
            }
        }
    }
}

// 2. POLAROID STYLE GALLERY
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gallery(dir: File, visible: List<String>, total: Int, showFavs: Boolean, title: String, onFilter: (Boolean) -> Unit, onBack: () -> Unit, onAdd: () -> Unit, onPlay: () -> Unit, onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        containerColor = Cream,
        topBar = {
            TopAppBar(
                title = { Text(title, fontFamily = FontFamily.Cursive, fontSize = 26.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (total > 0) IconButton(onClick = onPlay) { Icon(Icons.Default.PlayArrow, contentDescription = "Slideshow") }
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Add photos") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showFavs, onClick = { onFilter(false) }, label = { Text("All ($total)") })
                FilterChip(selected = showFavs, onClick = { onFilter(true) }, label = { Text("Favorites") })
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(visible) { i, name ->
                    // Polaroid Photo Style
                    Box(
                        Modifier
                            .shadow(6.dp, RoundedCornerShape(4.dp))
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                            .padding(bottom = 24.dp) // Extra space at bottom like real polaroid
                            .clickable { onOpen(i) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(File(dir, name)).crossfade(true).build(),
                            contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(0.9f).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

// 3. REALISTIC 3D PAGE CURL FLIP (የወረቀት መገለጥ አኒሜሽን)
@Composable
fun Viewer(dir: File, files: List<String>, start: Int, favs: List<String>, slideshow: Boolean, onSlideshow: (Boolean) -> Unit, onFav: (String) -> Unit, onDelete: (String) -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (files.size - 1).coerceAtLeast(0))) { files.size }
    val latest by rememberUpdatedState(files)
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(files.isEmpty()) { if (files.isEmpty()) onClose() }
    LaunchedEffect(slideshow) {
        while (slideshow && latest.isNotEmpty()) {
            delay(3500)
            if (latest.isNotEmpty()) pager.animateScrollToPage((pager.currentPage + 1) % latest.size)
        }
    }

    val current = files.getOrNull(pager.currentPage)

    Box(Modifier.fillMaxSize().background(WoodBackground).pointerInput(Unit) { detectTapGestures { } }) {
        
        HorizontalPager(
            state = pager, 
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2 // 3Dው smooth እንዲሆን 
        ) { page ->
            // 3D Math Logic (በጣም realistic የሆነ የገፅ መገለጥ ስሌት)
            val pageOffset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(-page.toFloat()) // ወደኋላ ያሉ ገጾች ከስር እንዲሆኑ
                    .graphicsLayer {
                        // ገጾቹን በአንድ ቦታ ላይ ደርቦ ለማስቀመጥ
                        translationX = pageOffset * size.width
                        
                        if (pageOffset > 0f) {
                            // ገጹ ወደ ግራ ሲገለጥ
                            transformOrigin = TransformOrigin(0f, 0.5f) // ልክ እንደ መፅሀፍ ከግራ በኩል ታስሮ እንዲገለጥ
                            rotationY = -180f * pageOffset.coerceIn(0f, 1f) // 3D Rotation
                            cameraDistance = 35 * density // 3D Perspective Depth
                            alpha = if (pageOffset > 0.5f) 0f else 1f // ገፁ 90 ዲግሪ ሲያልፍ እንዲጠፋ
                        } else {
                            // ከስር ያለ ያልተገለጠ ገፅ
                            alpha = 1f
                            rotationY = 0f
                        }
                    }
            ) {
                // Paper Background
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 32.dp)
                        .shadow(16.dp, RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(Color.White)
                ) {
                    files.getOrNull(page)?.let { ZoomImage(File(dir, it)) }

                    // ገፁ ሲገለጥ የሚኖረው ተፈጥሯዊ የጥላ (Shadow) ለውጥ
                    if (pageOffset > 0f && pageOffset <= 1f) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (pageOffset * 1.5f).coerceIn(0f, 0.6f))))
                    }
                    
                    // Paper Spine Shadow (የወረቀቱ ማሰሪያ አካባቢ ያለ መጥቆር)
                    Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(24.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0x33000000), Color.Transparent)))
                    )
                }
            }
        }

        // Top Control Bar
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))).padding(horizontal = 4.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            Text("${pager.currentPage + 1} / ${files.size}", color = Color.White, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (slideshow) {
                TextButton(onClick = { onSlideshow(false) }) { Text("Stop", color = Color.White) }
            } else {
                IconButton(onClick = { onSlideshow(true) }) { Icon(Icons.Default.PlayArrow, contentDescription = "Slideshow", tint = Color.White) }
            }
            if (current != null) {
                val isFav = current in favs
                IconButton(onClick = { onFav(current) }) { Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (isFav) Color(0xFFE57373) else Color.White) }
            }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White) }
        }
    }

    if (confirm && current != null) {
        AlertDialog(
            onDismissRequest = { confirm = false }, title = { Text("Delete photo?") }, text = { Text("This photo will be removed from the album.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete(current) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ZoomImage(file: File) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    AsyncImage(
        model = file, contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else { scale = 2.5f }
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent()
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
    )
}

@Composable
fun SetupDialog(groom: String, bride: String, date: String, place: String, onSave: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var g by remember { mutableStateOf(groom) }
    var b by remember { mutableStateOf(bride) }
    var d by remember { mutableStateOf(date) }
    var p by remember { mutableStateOf(place) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Wedding details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = g, onValueChange = { g = it }, label = { Text("Groom's name") }, singleLine = true)
                OutlinedTextField(value = b, onValueChange = { b = it }, label = { Text("Bride's name") }, singleLine = true)
                OutlinedTextField(value = d, onValueChange = { d = it }, label = { Text("Date (e.g. 12 Nov 2026)") }, singleLine = true)
                OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Place") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(g.trim(), b.trim(), d.trim(), p.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}