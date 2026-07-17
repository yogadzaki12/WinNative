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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armsx2.runtime.MainActivityRuntime
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.bindPaneNav
import com.winlator.cmod.shared.ui.nav.paneNavItem
import java.io.File
import kotlinx.coroutines.launch
import kr.co.iefriends.pcsx2.NativeApp

internal fun memcardDir(context: Context): File =
    File(MainActivityRuntime.assetCopyRoot(context), "memcards").apply { mkdirs() }

internal fun listMemcards(context: Context): List<File> =
    memcardDir(context).listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("ps2", true) }
        .sortedBy { it.name.lowercase() }

internal fun humanSize(bytes: Long): String =
    when {
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

private data class RaSnapshot(
    val json: String,
    val loggedIn: Boolean,
    val userName: String,
    val score: Long,
    val items: List<com.armsx2.ui.achievements.AchievementItem>,
)

private fun sharedRaCreds(context: Context): Pair<String, String>? {
    val ra = context.getSharedPreferences("retro_achievements", Context.MODE_PRIVATE)
    val u = ra.getString("username", null)
    val t = ra.getString("token", null)
    return if (!u.isNullOrBlank() && !t.isNullOrBlank()) u to t else null
}

/** Log ARMSX2's RetroAchievements client in using WinNative's shared account
 *  (the username + API token stored by RetroAchievementsManager for the other
 *  consoles), so PS2 uses the same login rather than a separate one. PCSX2
 *  persists [Achievements] Username/Token and auto-logs-in when the client is
 *  rebuilt. Returns true if shared creds existed and were pushed. */
private fun pushSharedRaToArmsx2(context: Context): Boolean {
    val (u, t) = sharedRaCreds(context) ?: return false
    runCatching {
        NativeApp.setSetting("Achievements", "Enabled", "bool", "true")
        NativeApp.setSetting("Achievements", "Username", "string", u)
        NativeApp.setSetting("Achievements", "Token", "string", t)
        NativeApp.commitSettings()
        NativeApp.clearAchievementsHostOverride()
    }
    return true
}

internal fun queryName(context: Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()

internal fun uniqueMemcard(dir: File, name: String): File {
    dir.mkdirs()
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var target = File(dir, name)
    var i = 2
    while (target.exists()) target = File(dir, "$base-$i$ext").also { i++ }
    return target
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
            status = context.getString(R.string.retro_scr_no_game_serial)
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
            status = if (ok) context.getString(R.string.retro_scr_applied_cheats, chosen.size) else context.getString(R.string.retro_scr_couldnt_apply_cheats)
        }
    }

    Ps2OverlayScaffold(title = stringResource(R.string.retro_scr_cheats), onBack = onBack, action = {
        if (!loading && entries.isNotEmpty()) {
            TextButton(onClick = { apply() }, modifier = Modifier.paneNavItem(onActivate = { apply() })) { Text(stringResource(R.string.retro_scr_apply)) }
        }
    }) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(status.ifBlank { stringResource(R.string.retro_scr_no_cheats_found) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        val toggle = { selected = if (entry.name in selected) selected - entry.name else selected + entry.name }
                        Card(
                            Modifier.fillMaxWidth().paneNavItem(cornerRadius = 12.dp, onActivate = { toggle() }),
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

    suspend fun refresh() {
        val parsed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val raw = runCatching { NativeApp.getAchievementsJSON().orEmpty() }.getOrDefault("")
            val root = runCatching { org.json.JSONObject(raw) }.getOrNull()
            RaSnapshot(
                json = raw,
                loggedIn = root?.optBoolean("loggedIn") ?: false,
                userName = root?.optString("userName").orEmpty(),
                score = root?.optLong("score")?.coerceAtLeast(0) ?: 0L,
                items = com.armsx2.ui.achievements.parseAchievementItems(raw),
            )
        }
        json = parsed.json
        loggedIn = parsed.loggedIn
        userName = parsed.userName
        score = parsed.score
        items = parsed.items
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        refresh()
        if (!loggedIn) {
            val pushed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pushSharedRaToArmsx2(context) }
            if (pushed) {
                repeat(6) {
                    kotlinx.coroutines.delay(500)
                    refresh()
                    if (loggedIn) return@LaunchedEffect
                }
            }
        }
    }

    Ps2OverlayScaffold(title = stringResource(R.string.retro_scr_achievements), onBack = onBack) {
        if (!loggedIn) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.retro_scr_sign_in_ra), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(value = user, onValueChange = { user = it }, singleLine = true, label = { Text(stringResource(R.string.retro_scr_username)) })
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.retro_scr_password)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                val doLogin = {
                    if (!busy && user.isNotBlank() && pass.isNotBlank()) {
                        busy = true; error = ""
                        RetroAchievementsManager.login(context, user.trim(), pass) { ok, msg ->
                            if (ok) {
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pushSharedRaToArmsx2(context) }
                                    repeat(6) {
                                        kotlinx.coroutines.delay(500)
                                        refresh()
                                        if (loggedIn) return@launch
                                    }
                                    busy = false
                                }
                            } else {
                                busy = false
                                error = msg ?: context.getString(R.string.retro_scr_login_failed)
                            }
                        }
                    }
                    Unit
                }
                OutlinedButton(
                    enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
                    modifier = Modifier.paneNavItem(onActivate = doLogin),
                    onClick = doLogin,
                ) { Text(if (busy) stringResource(R.string.retro_scr_signing_in) else stringResource(R.string.retro_scr_sign_in)) }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(userName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.retro_scr_ra_score_summary, items.count { it.unlocked }, items.size, score),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.retro_scr_no_achievements), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { PaneNavRegistry() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val restore = activity?.window?.bindPaneNav(registry, onDismiss = onBack)
        onDispose { restore?.invoke() }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPaneNav provides registry) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.paneNavItem(onActivate = onBack)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.retro_scr_back), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                Spacer(Modifier.width(0.dp).weight(1f))
                action?.invoke()
            }
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}
