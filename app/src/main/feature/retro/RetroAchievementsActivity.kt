package com.winlator.cmod.feature.retro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextFieldDefaults
import coil.compose.AsyncImage
import com.winlator.cmod.shared.ui.nav.bindPaneNav
import com.winlator.cmod.shared.ui.nav.paneNavItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgDark = Color(0xFF12121B)
private val SurfaceDark = Color(0xFF171722)
private val CardBorder = Color(0xFF2A2A3A)
private val Accent = Color(0xFF1A9FFF)
private val AccentGlow = Color(0xFF58A6FF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF93A6BC)
private val Gold = Color(0xFFF2C14E)
private val Scrim = Color(0xFF000000)
private val StatusOnline = Color(0xFF3FB950)

class RetroAchievementsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_SYSTEM_ID = "system_id"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_IN_SESSION = "in_session"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val systemId = intent.getStringExtra(EXTRA_SYSTEM_ID)
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: "Retro Game"
        val romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: ""
        val inSession = intent.getBooleanExtra(EXTRA_IN_SESSION, false)
        setContent {
            com.winlator.cmod.shared.theme.WinNativeTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.winlator.cmod.shared.ui.nav.LocalPaneNav provides navRegistry,
                ) {
                    RetroAchievementsScreen(systemId, gameName, romPath, inSession) { finish() }
                }
            }
        }
    }

    private val navRegistry = com.winlator.cmod.shared.ui.nav.PaneNavRegistry()
    private var restoreNav: (() -> Unit)? = null

    override fun onResume() {
        super.onResume()
        restoreNav = window.bindPaneNav(navRegistry, onDismiss = { finish() })
    }

    override fun onPause() {
        restoreNav?.invoke()
        restoreNav = null
        super.onPause()
    }
}

@Composable
private fun RetroAchievementsScreen(
    systemId: String?,
    gameName: String,
    romPath: String,
    inSession: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val refreshScope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(RetroAchievementsManager.isLoggedIn(context)) }
    var enabled by remember { mutableStateOf(RetroAchievementsManager.isEnabled(context)) }
    var hardcore by remember { mutableStateOf(RetroAchievementsManager.isHardcorePreferred(context)) }
    var confirmHardcore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(loggedIn) }
    var summary by remember { mutableStateOf<RetroGameSummary?>(null) }
    var achievements by remember { mutableStateOf<List<RetroAchievement>>(emptyList()) }

    fun refresh() {
        refreshScope.launch {
            val s = withContext(Dispatchers.IO) { RetroAchievementsManager.getSummary() }
            val a = withContext(Dispatchers.IO) { RetroAchievementsManager.getAchievements() }
            if (s != null) summary = s
            if (a.isNotEmpty()) achievements = a
        }
    }

    DisposableEffect(Unit) {
        RetroAchievementsManager.stateListener = { refresh() }
        onDispose { RetroAchievementsManager.stateListener = null }
    }

    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            loading = false
            return@LaunchedEffect
        }
        if (!inSession) {
            RetroAchievementsManager.loadGameForBrowsing(context, systemId, romPath)
        }
        loading = RetroAchievementsManager.loadState == RetroAchievementsManager.LOAD_LOADING
        var attempts = 0
        while (RetroAchievementsManager.loadState == RetroAchievementsManager.LOAD_LOADING && attempts < 80) {
            delay(250)
            attempts++
        }
        summary = withContext(Dispatchers.IO) { RetroAchievementsManager.getSummary() }
        achievements = withContext(Dispatchers.IO) { RetroAchievementsManager.getAchievements() }
        loading = false
    }

    if (confirmHardcore) {
        RetroHardcoreConfirmDialog(
            onConfirm = {
                confirmHardcore = false
                hardcore = true
                RetroAchievementsManager.setHardcorePreferred(context, true)
            },
            onDismiss = { confirmHardcore = false },
        )
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Scrim.copy(alpha = 0.62f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() }
                .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        val dialogWidth = (maxWidth - 32.dp).coerceAtMost(560.dp)
        val dialogHeight = (maxHeight - 40.dp).coerceIn(340.dp, 680.dp)
        Surface(
            modifier =
                Modifier
                    .widthIn(min = 320.dp, max = dialogWidth)
                    .fillMaxWidth()
                    .height(dialogHeight)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            shape = RoundedCornerShape(16.dp),
            color = BgDark,
            border = BorderStroke(1.dp, CardBorder),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Header(gameName, summary, onClose)
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                !loggedIn -> LoginPane { user, pass, onResult ->
                    RetroAchievementsManager.login(context, user, pass) { ok, msg ->
                        onResult(ok, msg)
                        if (ok) loggedIn = true
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(32.dp))
                }
                else -> AchievementsList(
                    achievements = achievements,
                    summary = summary,
                    enabled = enabled,
                    hardcore = hardcore,
                    onEnabledChange = {
                        enabled = it
                        RetroAchievementsManager.setEnabled(context, it)
                    },
                    onHardcoreChange = {
                        if (it) {
                            confirmHardcore = true
                        } else {
                            hardcore = false
                            RetroAchievementsManager.setHardcorePreferred(context, false)
                        }
                    },
                    onLogout = {
                        RetroAchievementsManager.logout(context)
                        loggedIn = false
                        summary = null
                        achievements = emptyList()
                    },
                )
                }
                }
            }
        }
    }
}

@Composable
private fun Header(gameName: String, summary: RetroGameSummary?, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Gold, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "RETROACHIEVEMENTS",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                gameName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (summary != null && summary.total > 0) {
            Surface(color = Gold.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "${summary.unlocked} / ${summary.total}",
                    color = Gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(40.dp).paneNavItem(onActivate = onClose)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun LoginPane(onLogin: (String, String, (Boolean, String?) -> Unit) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "Sign in with your RetroAchievements account to earn and track achievements for retro games.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        val fieldColors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextSecondary,
                cursorColor = AccentGlow,
                focusedBorderColor = AccentGlow,
                unfocusedBorderColor = CardBorder,
                focusedLabelColor = AccentGlow,
                unfocusedLabelColor = TextSecondary,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
            )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color(0xFFE07B6B), style = MaterialTheme.typography.labelMedium) }
        val submit = {
            if (username.isBlank() || password.isBlank()) {
                error = "Enter your username and password"
            } else {
                busy = true
                error = null
                onLogin(username.trim(), password) { ok, msg ->
                    busy = false
                    if (!ok) error = msg ?: "Login failed"
                }
            }
        }
        Button(
            onClick = submit,
            enabled = !busy,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().paneNavItem(isEntry = true, onActivate = { submit() }),
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
            } else {
                Text("Sign In")
            }
        }
        Text(
            "Your password is only used once to obtain a login token, which is stored securely on device.",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AchievementsList(
    achievements: List<RetroAchievement>,
    summary: RetroGameSummary?,
    enabled: Boolean,
    hardcore: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onHardcoreChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
) {
    val ordered = achievements.sortedWith(
        compareByDescending<RetroAchievement> { it.unlocked }.thenBy { it.title },
    )
    Column(Modifier.fillMaxSize()) {
        if (summary != null && summary.total > 0) {
            LinearProgressIndicator(
                progress = { summary.unlocked.toFloat() / summary.total },
                color = Gold,
                trackColor = SurfaceDark,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingsRow("Achievements enabled", enabled, onEnabledChange)
            SettingsRow("Hardcore mode (no save states)", hardcore, onHardcoreChange)
            if (summary != null) {
                Text(
                    "${summary.pointsUnlocked} / ${summary.pointsTotal} points",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                )
            }
            if (achievements.isEmpty()) {
                Text(
                    "No achievements found for this game.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                ordered.forEach { AchievementRow(it) }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().paneNavItem(onActivate = onLogout)) {
                Text("Sign Out", color = TextSecondary)
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().paneNavItem(cornerRadius = 12.dp, onActivate = { onChange(!checked) }),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors =
                    androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent,
                        uncheckedTrackColor = SurfaceDark,
                        uncheckedBorderColor = CardBorder,
                    ),
            )
        }
    }
}

@Composable
private fun AchievementRow(ach: RetroAchievement) {
    val url = if (ach.unlocked) ach.badgeUrl else ach.badgeLockedUrl.ifBlank { ach.badgeUrl }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (ach.unlocked) Gold.copy(alpha = 0.07f) else SurfaceDark.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (ach.unlocked) Gold.copy(alpha = 0.25f) else CardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(BgDark),
                contentAlignment = Alignment.Center,
            ) {
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                        alpha = if (ach.unlocked) 1f else 0.5f,
                    )
                } else {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ach.title,
                    color = if (ach.unlocked) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ach.description.isNotBlank()) {
                    Text(
                        ach.description,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ach.progress.isNotBlank() && !ach.unlocked) {
                    Text(
                        ach.progress,
                        color = AccentGlow,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                "${ach.points}",
                color = if (ach.unlocked) Gold else TextSecondary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
