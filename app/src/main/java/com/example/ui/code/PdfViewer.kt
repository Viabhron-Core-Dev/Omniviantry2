package com.example.ui.code

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

@Composable
fun PdfViewer(file: File, modifier: Modifier = Modifier) {
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(file) {
        try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            fileDescriptor = fd
            pdfRenderer = renderer
        } catch (e: Exception) {
            error = e.message
        }
        
        onDispose {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        pdfRenderer?.let { renderer ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(renderer.pageCount) { index ->
                    PdfPageViewer(file = file, renderer = renderer, pageIndex = index)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        } ?: error?.let { e ->
            Text(text = "Error loading PDF: $e", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        } ?: run {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun PdfPageViewer(file: File, renderer: PdfRenderer, pageIndex: Int) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(renderer, pageIndex) {
        try {
            val page = renderer.openPage(pageIndex)
            val w = page.width * 2
            val h = page.height * 2
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // Render on white background
            bm.eraseColor(android.graphics.Color.WHITE)
            page.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap = bm
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Page Header & Mark to AI Action (Mini-Phase 5 Deliverable 2)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Page ${pageIndex + 1} of ${renderer.pageCount}", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = {
                    WorkspaceChatBridge.addStagedReference(
                        StagedReference.DocumentPage(
                            file = file,
                            pageIndex = pageIndex,
                            totalPages = renderer.pageCount
                        )
                    )
                    Toast.makeText(
                        context,
                        "Staged Page ${pageIndex + 1} to AI Basket (Total: ${WorkspaceChatBridge.getBasketCount()})",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Mark Page to AI", fontSize = 11.sp)
            }
        }

        bitmap?.let { b ->
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        } ?: run {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
