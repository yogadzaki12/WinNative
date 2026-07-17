package com.winlator.cmod.feature.retro

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armsx2.runtime.MainActivityRuntime
import java.io.File
import kotlinx.coroutines.launch
import kr.co.iefriends.pcsx2.NativeApp

private fun memcardDir(context: Context): File =
    File(MainActivityRuntime.assetCopyRoot(context), "memcards").apply { mkdirs() }

private fun listMemcards(context: Context): List<File> =
    memcardDir(context).listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("ps2", true) }
        .sortedBy { it.name.lowercase() }

private fun humanSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

private fun queryName(context: Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

private fun uniqueMemcard(dir: File, name: String): File {
    dir.mkdirs()
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var target = File(dir, name)
    var i = 2
    while (target.exists()) target = File(dir, "$base-$i$ext").also { i++ }
    return target
}

private fun assignSlot(context: Context, slot: Int, name: String) {
    runCatching {
        NativeApp.setSetting("MemoryCards", "Slot${slot}_Enable", "bool", "false")
        NativeApp.setSetting("MemoryCards", "Slot${slot}_Filename", "string", name)
        NativeApp.setSetting("MemoryCards", "Slot${slot}_Enable", "bool", "true")
        NativeApp.commitSettings()
    }
    context.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE)
        .edit().putString("wn.ps2.mc.slot$slot", name).apply()
}

@Composable
fun Ps2MemoryCardsScreen(
    context: Context,
    onBack: () -> Unit,
) {
    var cards by remember { mutableStateOf(listMemcards(context)) }
    val prefs = context.getSharedPreferences("ARMSX2", Context.MODE_PRIVATE)
    var slot1 by remember { mutableStateOf(prefs.getString("wn.ps2.mc.slot1", "").orEmpty()) }
    var slot2 by remember { mutableStateOf(prefs.getString("wn.ps2.mc.slot2", "").orEmpty()) }
    var showCreate by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<File?>(null) }

    fun reload() {
        cards = listMemcards(context)
        slot1 = prefs.getString("wn.ps2.mc.slot1", "").orEmpty()
        slot2 = prefs.getString("wn.ps2.mc.slot2", "").orEmpty()
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val name = queryName(context, uri) ?: "Imported.ps2"
                val fileName = if (name.endsWith(".ps2", true)) name else "$name.ps2"
                val target = uniqueMemcard(memcardDir(context), fileName)
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
                target.length() > 0L && NativeApp.isMemoryCard(target.name)
            }.getOrDefault(false)
            android.widget.Toast.makeText(context, if (ok) "Imported memory card" else "Import failed", android.widget.Toast.LENGTH_SHORT).show()
            reload()
        }
    }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val src = exportTarget
        if (uri != null && src != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(src.readBytes()) }
                true
            }.getOrDefault(false)
            android.widget.Toast.makeText(context, if (ok) "Exported ${src.name}" else "Export failed", android.widget.Toast.LENGTH_SHORT).show()
        }
        exportTarget = null
    }

    Ps2OverlayScaffold(title = "Memory Cards", onBack = onBack, action = {
        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
            Icon(Icons.Outlined.FileDownload, contentDescription = "Import", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = { showCreate = true }) {
            Icon(Icons.Outlined.Add, contentDescription = "Create", tint = MaterialTheme.colorScheme.onSurface)
        }
    }) {
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No memory cards yet. Tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cards) { card ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(card.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                humanSize(card.length()) +
                                    (if (card.name == slot1) "  •  Slot 1" else "") +
                                    (if (card.name == slot2) "  •  Slot 2" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { assignSlot(context, 1, card.name); reload() }) { Text("Slot 1") }
                                OutlinedButton(onClick = { assignSlot(context, 2, card.name); reload() }) { Text("Slot 2") }
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { exportTarget = card; exportLauncher.launch(card.name) }) {
                                    Icon(Icons.Outlined.FileUpload, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                if (card.name != slot1 && card.name != slot2) {
                                    IconButton(onClick = { card.delete(); reload() }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("MemoryCard") }
        var sizeType by remember { mutableStateOf(1) }
        val sizeLabels = listOf("8 MB", "16 MB", "32 MB", "64 MB")
        AlertDialog(
            onDismissRequest = { showCreate = false },
            confirmButton = {
                TextButton(onClick = {
                    val safe = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "MemoryCard" }
                    val fileName = if (safe.endsWith(".ps2", true)) safe else "$safe.ps2"
                    runCatching { NativeApp.createMemoryCard(fileName, 1, sizeType.coerceIn(1, 4)) }
                    showCreate = false
                    reload()
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
            title = { Text("New Memory Card") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                    Spacer(Modifier.height(12.dp))
                    Text("Size", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sizeLabels.forEachIndexed { idx, label ->
                            OutlinedButton(
                                onClick = { sizeType = idx + 1 },
                                colors = if (sizeType == idx + 1) {
                                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                } else {
                                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                },
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun Ps2CheatsScreen(
    context: Context,
    onBack: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var serial by remember { mutableStateOf("") }
    var crc by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<com.armsx2.PatchRepo.Entry>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        loading = true
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val s = runCatching { NativeApp.getGameSerial() }.getOrNull()?.takeIf { it.isNotBlank() }
            val c = runCatching { NativeApp.getGameCRC() }.getOrNull()?.takeIf { it.length == 8 }
            if (s == null) null
            else if (c != null) com.armsx2.PatchRepo.fetchForGame(s, c) else com.armsx2.PatchRepo.fetchForSerial(s)
        }
        if (result == null) {
            status = "No game serial to look up cheats for."
        } else {
            title = result.gametitle
            serial = result.serial
            crc = result.crc
            entries = result.entries
            status = result.error.orEmpty()
            val installed = runCatching {
                val dir = File(MainActivityRuntime.assetCopyRoot(context), "cheats")
                val f = if (crc.isNotBlank()) File(dir, "${serial}_$crc.pnach") else File(dir, "$serial.pnach")
                if (f.isFile) com.armsx2.PatchRepo.parseInstalled(f.readText(), "cheats").second
                    .filter { it.enabled }.map { it.name }.toSet()
                else emptySet()
            }.getOrDefault(emptySet())
            selected = installed
        }
        loading = false
    }

    fun apply() {
        val chosen = entries.filter { it.name in selected }
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val dir = File(MainActivityRuntime.assetCopyRoot(context), "cheats").apply { mkdirs() }
                    val fileName = if (crc.isNotBlank()) "${serial}_$crc.pnach" else "$serial.pnach"
                    File(dir, fileName).writeText(com.armsx2.PatchRepo.buildPnach(title, chosen))
                    NativeApp.setSetting("EmuCore", "EnableCheats", "bool", "true")
                    NativeApp.commitSettings()
                    val all = entries.mapNotNull { it.name.takeIf(String::isNotBlank) }.distinct().toTypedArray()
                    val on = chosen.mapNotNull { it.name.takeIf(String::isNotBlank) }.distinct().toTypedArray()
                    NativeApp.setEnabledPatches(true, all, on)
                    NativeApp.reloadPatches()
                    true
                }.getOrDefault(false)
            }
            status = if (ok) "Applied ${chosen.size} cheat(s)." else "Couldn't apply cheats."
        }
    }

    Ps2OverlayScaffold(title = "Cheats", onBack = onBack, action = {
        if (!loading && entries.isNotEmpty()) {
            TextButton(onClick = { apply() }) { Text("Apply") }
        }
    }) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(status.ifBlank { "No cheats found for this game." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Column(Modifier.fillMaxSize()) {
                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries) { entry ->
                        val checked = entry.name in selected
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    if (entry.description.isNotBlank()) {
                                        Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                androidx.compose.material3.Switch(
                                    checked = checked,
                                    onCheckedChange = {
                                        selected = if (checked) selected - entry.name else selected + entry.name
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Ps2AchievementsScreen(
    context: Context,
    onBack: () -> Unit,
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var json by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0L) }
    var items by remember { mutableStateOf<List<com.armsx2.ui.achievements.AchievementItem>>(emptyList()) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun refresh() {
        json = runCatching { NativeApp.getAchievementsJSON().orEmpty() }.getOrDefault("")
        val root = runCatching { org.json.JSONObject(json) }.getOrNull()
        loggedIn = root?.optBoolean("loggedIn") ?: false
        userName = root?.optString("userName").orEmpty()
        score = root?.optLong("score")?.coerceAtLeast(0) ?: 0L
        items = com.armsx2.ui.achievements.parseAchievementItems(json)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    Ps2OverlayScaffold(title = "Achievements", onBack = onBack) {
        if (!loggedIn) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Sign in to RetroAchievements", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(value = user, onValueChange = { user = it }, singleLine = true, label = { Text("Username") })
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
                    onClick = {
                        busy = true; error = ""
                        scope.launch {
                            val err = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching { NativeApp.loginAchievements(user.trim(), pass) }.getOrDefault("Login failed")
                            }
                            busy = false
                            if (err.isNullOrBlank()) refresh() else error = err
                        }
                    },
                ) { Text(if (busy) "Signing in…" else "Sign In") }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(userName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${items.count { it.unlocked }}/${items.size} • $score pts",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No achievements for this game.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items) { a ->
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (a.iconUrl.isNotBlank()) {
                                        coil.compose.AsyncImage(
                                            model = a.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier.width(48.dp).height(48.dp),
                                            alpha = if (a.unlocked) 1f else 0.35f,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(a.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (a.progress.isNotBlank()) {
                                            Text(a.progress, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text("${a.points}", color = if (a.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
fun Ps2OverlayScaffold(
    title: String,
    onBack: () -> Unit,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.width(0.dp).weight(1f))
            action?.invoke()
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}
