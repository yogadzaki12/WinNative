package com.winlator.cmod.feature.retro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.winlator.cmod.R
import com.winlator.cmod.feature.library.GameSettingsNav
import com.winlator.cmod.feature.shortcuts.LibraryShortcutArtwork
import com.winlator.cmod.runtime.container.Shortcut
import java.io.File
import com.winlator.cmod.shared.ui.nav.LocalPaneNav
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.paneHighlight
import com.winlator.cmod.shared.ui.nav.paneNavItem
import com.winlator.cmod.shared.ui.outlinedSwitchColors
import com.winlator.cmod.shared.ui.widget.chasingBorder

private val BgDeep = Color(0xFF11111C)
private val SidebarBg = Color(0xFF11111C)
private val ContentBg = Color(0xFF11111C)
private val CardSurface = Color(0xFF1C1C2A)
private val CardBorder = Color(0xFF2A2A3A)
private val InputSurface = Color(0xFF171722)
private val InputBorder = Color(0xFF2A2A3A)
private val AccentBlue = Color(0xFF1A9FFF)
private val TextPrimary = Color(0xFFF0F4FF)
private val TextSecondary = Color(0xFF7A8FA8)
private val TextDim = Color(0xFF6E7681)
private val DividerColor = Color(0xFF2A2A3A)
private val NavHighlight = Color(0xFF4FC3F7)
private val DangerRed = Color(0xFFFF6B6B)

private val LabelSize = 11.sp
private val ValueSize = 12.sp
private val GroupCorner = 12.dp
private val GroupPadding = 12.dp
private val FieldCorner = 8.dp
private val ItemGap = 10.dp
private val TightGap = 4.dp

private val SHADER_KEYS = listOf("default", "crt", "lcd", "sharp")
private val UPSCALE_KEYS = listOf("2x", "4x", "native")

class RetroSettingsState(
    val shortcut: Shortcut,
    private val context: android.content.Context? = null,
) {
    val system: RetroSystem? = RetroShortcuts.systemForShortcut(shortcut)
    val coreOptions: List<RetroCoreOption> = RetroCoreOptions.forSystem(system)
    val name: String = shortcut.getExtra("custom_name", shortcut.name)
    val romPath: String = shortcut.getExtra(RetroShortcuts.KEY_ROM)

    private val sysId: String? = system?.id

    var shader by mutableStateOf(
        shortcut.getExtra(RetroShortcuts.KEY_SHADER)
            .ifEmpty { if (context != null && sysId != null) RetroDefaults.shader(context, sysId) else "default" }
            .lowercase().let { if (it in SHADER_KEYS) it else "default" },
    )
    var sgsr by mutableStateOf(
        when {
            shortcut.getExtra(RetroShortcuts.KEY_SGSR).isNotEmpty() -> shortcut.getExtra(RetroShortcuts.KEY_SGSR) == "1"
            shortcut.getExtra(RetroShortcuts.KEY_SHADER, "default").lowercase() == "sgsr" -> true
            context != null && sysId != null -> RetroDefaults.sgsr(context, sysId)
            else -> false
        },
    )
    var upscale by mutableStateOf(
        shortcut.getExtra(RetroShortcuts.KEY_UPSCALE)
            .ifEmpty { if (context != null && sysId != null) RetroDefaults.upscale(context, sysId) else "native" }
            .lowercase().let { if (it in UPSCALE_KEYS) it else "native" },
    )
    var touchControls by mutableStateOf(
        shortcut.getExtra(RetroShortcuts.KEY_TOUCH_CONTROLS)
            .ifEmpty { if (context != null && sysId != null) (if (RetroDefaults.touchControls(context, sysId)) "1" else "0") else "1" } != "0",
    )
    var audio by mutableStateOf(
        shortcut.getExtra(RetroShortcuts.KEY_AUDIO)
            .ifEmpty { if (context != null && sysId != null) (if (RetroDefaults.audio(context, sysId)) "1" else "0") else "1" } != "0",
    )
    var hud by mutableStateOf(
        shortcut.getExtra(RetroShortcuts.KEY_HUD)
            .ifEmpty { if (context != null && sysId != null) (if (RetroDefaults.hud(context, sysId)) "1" else "0") else "0" } == "1",
    )
    val optionValues =
        mutableStateMapOf<String, String>().apply {
            coreOptions.forEach { option ->
                put(
                    option.key,
                    shortcut.getExtra(RetroShortcuts.VAR_PREFIX + option.key).ifEmpty {
                        if (context != null && sysId != null) {
                            RetroDefaults.coreOption(context, sysId, option.key, option.defaultValue)
                        } else {
                            option.defaultValue
                        }
                    },
                )
            }
        }
    val artworkSelected = mutableStateMapOf<LibraryShortcutArtwork.LibraryArtworkSlot, Boolean>()
    var currentSection by mutableIntStateOf(0)
    var biosRefresh by mutableIntStateOf(0)

    init {
        syncArtwork()
    }

    fun syncArtwork() {
        LibraryShortcutArtwork.LibraryArtworkSlot.entries.forEach { slot ->
            val path = shortcut.getExtra(slot.extraKey)
            artworkSelected[slot] = path.isNotBlank() && File(path).isFile
        }
    }

    fun save() {
        shortcut.putExtra(RetroShortcuts.KEY_SHADER, shader)
        shortcut.putExtra(RetroShortcuts.KEY_SGSR, if (sgsr) "1" else "0")
        shortcut.putExtra(RetroShortcuts.KEY_UPSCALE, upscale)
        shortcut.putExtra(RetroShortcuts.KEY_TOUCH_CONTROLS, if (touchControls) "1" else "0")
        shortcut.putExtra(RetroShortcuts.KEY_AUDIO, if (audio) "1" else "0")
        shortcut.putExtra(RetroShortcuts.KEY_HUD, if (hud) "1" else "0")
        coreOptions.forEach { option ->
            shortcut.putExtra(
                RetroShortcuts.VAR_PREFIX + option.key,
                optionValues[option.key] ?: option.defaultValue,
            )
        }
        shortcut.saveData()
    }
}

private data class RetroSection(
    val icon: ImageVector,
    val labelRes: Int,
)

private fun buildRetroSections(state: RetroSettingsState): List<RetroSection> {
    val sections = mutableListOf<RetroSection>()
    sections += RetroSection(Icons.Outlined.Tune, R.string.retro_gs_section_general)
    sections += RetroSection(Icons.Outlined.Monitor, R.string.retro_gs_section_graphics)
    if (state.system?.isExternal == true) {
        sections += RetroSection(Icons.Outlined.Bolt, R.string.retro_gs_section_performance)
        sections += RetroSection(Icons.Outlined.Speed, R.string.retro_gs_section_hud)
    }
    sections += RetroSection(Icons.Outlined.SportsEsports, R.string.retro_gs_section_input)
    sections += RetroSection(Icons.AutoMirrored.Outlined.VolumeUp, R.string.retro_gs_section_audio)
    if (state.system?.isExternal == true) {
        sections += RetroSection(Icons.Outlined.Public, R.string.retro_gs_section_online)
    }
    return sections
}

@Composable
fun RetroGameSettingsContent(
    state: RetroSettingsState,
    nav: GameSettingsNav? = null,
    onPickArtwork: ((LibraryShortcutArtwork.LibraryArtworkSlot) -> Unit)? = null,
    onRemoveArtwork: ((LibraryShortcutArtwork.LibraryArtworkSlot) -> Unit)? = null,
    onImportBios: (() -> Unit)? = null,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val sections = remember(state) { buildRetroSections(state) }
    val selectedIdx = state.currentSection

    if (nav != null) {
        SideEffect {
            nav.sidebarCount = sections.size
            nav.onSelectSection = { state.currentSection = it }
            nav.onSave = onSave
            nav.onCancel = onCancel
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(BgDeep),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            RetroSidebar(
                title = state.name,
                subtitle = state.system?.displayName ?: "",
                sections = sections,
                currentIndex = selectedIdx,
                onSectionSelected = { state.currentSection = it },
                onSave = onSave,
                onCancel = onCancel,
                nav = nav,
                modifier = Modifier.width(220.dp).fillMaxHeight(),
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(DividerColor))
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ContentBg),
            ) {
                RetroSectionContent(
                    sectionIndex = selectedIdx,
                    state = state,
                    nav = nav,
                    onPickArtwork = onPickArtwork,
                    onRemoveArtwork = onRemoveArtwork,
                    onImportBios = onImportBios,
                )
            }
        }
    }
}

@Composable
private fun RetroSectionContent(
    sectionIndex: Int,
    state: RetroSettingsState,
    nav: GameSettingsNav? = null,
    onPickArtwork: ((LibraryShortcutArtwork.LibraryArtworkSlot) -> Unit)? = null,
    onRemoveArtwork: ((LibraryShortcutArtwork.LibraryArtworkSlot) -> Unit)? = null,
    onImportBios: (() -> Unit)? = null,
) {
    // Section switches render instantly — no slide/fade between settings panes.
    run {
        val idx = sectionIndex
        val contentNav = remember(nav) { nav?.let { PaneNavRegistry(initialSignal = it.contentSignal) } }
        val isCurrent = true
        if (nav != null && contentNav != null) {
            contentNav.controllerActive = nav.active && nav.inContent && isCurrent
            contentNav.onEdgeLeft = { nav.exitToSidebar() }
            if (isCurrent) {
                nav.onContentBack = {
                    if (contentNav.overlay != null) {
                        contentNav.overlayClose?.invoke()
                        true
                    } else {
                        false
                    }
                }
            }
            LaunchedEffect(nav.contentSignal) {
                if (isCurrent) contentNav.processNav(nav.contentSignal, nav.contentDir)
            }
            LaunchedEffect(nav.contentResetSignal) {
                if (isCurrent) contentNav.reset()
            }
        }
        val sectionBody: @Composable () -> Unit = {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                if (state.system?.isExternal == true) {
                    when (idx) {
                        0 -> RetroGeneralSection(state, onPickArtwork, onRemoveArtwork, onImportBios)
                        1 -> RetroGraphicsSection(state)
                        2 -> RetroPs2PerformanceSection()
                        3 -> RetroPs2HudSection()
                        4 -> RetroInputSection(state)
                        5 -> RetroAudioSection(state)
                        else -> RetroPs2OnlineSection()
                    }
                } else {
                    when (idx) {
                        0 -> RetroGeneralSection(state, onPickArtwork, onRemoveArtwork, onImportBios)
                        1 -> RetroGraphicsSection(state)
                        2 -> RetroInputSection(state)
                        else -> RetroAudioSection(state)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        if (contentNav != null) {
            CompositionLocalProvider(LocalPaneNav provides contentNav) { sectionBody() }
        } else {
            sectionBody()
        }
    }
}

@Composable
private fun RetroSidebar(
    title: String,
    subtitle: String,
    sections: List<RetroSection>,
    currentIndex: Int,
    onSectionSelected: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    nav: GameSettingsNav? = null,
    modifier: Modifier = Modifier,
) {
    val cancelHighlighted = nav != null && nav.active && !nav.inContent && nav.onActionRow && nav.actionCol == 0
    val saveHighlighted = nav != null && nav.active && !nav.inContent && nav.onActionRow && nav.actionCol == 1
    Column(
        modifier =
            modifier
                .background(SidebarBg)
                .padding(top = 14.dp, bottom = 12.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = LabelSize,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 2.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor),
        )
        Spacer(Modifier.height(8.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            sections.forEachIndexed { index, section ->
                RetroSidebarItem(
                    icon = section.icon,
                    label = stringResource(section.labelRes),
                    isSelected = currentIndex == index,
                    navHighlighted =
                        nav != null && nav.active && !nav.inContent && !nav.onActionRow &&
                            nav.sidebarIndex == index,
                    onClick = {
                        if (nav != null) nav.tapSection(index) else onSectionSelected(index)
                    },
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(DividerColor),
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        .background(CardSurface)
                        .paneHighlight(cancelHighlighted, cornerRadius = 8.dp, highlightColor = NavHighlight)
                        .clickable {
                            nav?.tapAction(0)
                            onCancel()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.retro_gs_cancel), color = TextSecondary, fontSize = LabelSize, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .background(AccentBlue.copy(alpha = 0.1f))
                        .paneHighlight(saveHighlighted, cornerRadius = 8.dp, highlightColor = NavHighlight)
                        .clickable {
                            nav?.tapAction(1)
                            onSave()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.retro_gs_save), color = AccentBlue, fontSize = LabelSize, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RetroSidebarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    navHighlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .chasingBorder(isFocused = isSelected, cornerRadius = 8.dp, borderWidth = 2.dp)
                .paneHighlight(navHighlighted, cornerRadius = 8.dp, highlightColor = NavHighlight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) AccentBlue else TextDim,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                color = if (isSelected) TextPrimary else TextSecondary,
                fontSize = ValueSize,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun RetroSettingGroup(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupCorner))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(GroupCorner))
                .padding(GroupPadding),
    ) {
        content()
    }
}

@Composable
internal fun RetroGroupTitle(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        fontSize = LabelSize,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(bottom = ItemGap),
    )
}

@Composable
internal fun RetroInfoRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = ItemGap)) {
        Text(
            label,
            color = TextSecondary,
            fontSize = LabelSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(bottom = TightGap),
        )
        Text(
            value.ifBlank { "-" },
            color = TextPrimary,
            fontSize = ValueSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun RetroSettingDropdown(
    label: String,
    entries: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = entries.getOrElse(selectedIndex) { "" }
    val parentNav = LocalPaneNav.current
    val optionRegistry = remember { PaneNavRegistry() }
    LaunchedEffect(expanded) {
        if (expanded) {
            optionRegistry.reset()
            optionRegistry.controllerActive = true
            parentNav?.overlay = optionRegistry
            parentNav?.overlayClose = { expanded = false }
        } else if (parentNav?.overlay === optionRegistry) {
            parentNav.overlay = null
            parentNav.overlayClose = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = ItemGap)) {
        Text(
            label,
            color = TextSecondary,
            fontSize = LabelSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            modifier = Modifier.padding(bottom = TightGap),
        )
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FieldCorner))
                        .background(InputSurface)
                        .border(1.dp, InputBorder, RoundedCornerShape(FieldCorner))
                        .paneNavItem(
                            cornerRadius = FieldCorner,
                            onActivate = { expanded = true },
                            highlightColor = NavHighlight,
                        )
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedText,
                    color = TextPrimary,
                    fontSize = ValueSize,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(8.dp),
                containerColor = CardSurface,
                properties = PopupProperties(focusable = false),
            ) {
                CompositionLocalProvider(LocalPaneNav provides optionRegistry) {
                    entries.forEachIndexed { index, entry ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    entry,
                                    color = if (index == selectedIndex) AccentBlue else TextPrimary,
                                    fontSize = ValueSize,
                                    fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelected(index)
                                expanded = false
                            },
                            modifier =
                                (
                                    if (index == selectedIndex) {
                                        Modifier.background(AccentBlue.copy(alpha = 0.06f))
                                    } else {
                                        Modifier
                                    }
                                ).paneNavItem(
                                    cornerRadius = 6.dp,
                                    onActivate = {
                                        onSelected(index)
                                        expanded = false
                                    },
                                    isEntry = index == selectedIndex,
                                    highlightColor = NavHighlight,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RetroSettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onCheckedChange(!checked) }
                .paneNavItem(
                    cornerRadius = 8.dp,
                    onActivate = { onCheckedChange(!checked) },
                    highlightColor = NavHighlight,
                    tapToSelect = true,
                )
                .padding(vertical = TightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = TextPrimary,
            fontSize = ValueSize,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = outlinedSwitchColors(accentColor = AccentBlue, textSecondaryColor = TextSecondary),
        )
    }
}

@Composable
internal fun RetroSettingTextField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    if (editing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onChange(draft.trim())
                    editing = false
                }) { Text(stringResource(R.string.retro_gs_save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editing = false }) { Text(stringResource(R.string.retro_gs_cancel)) }
            },
        )
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { draft = value; editing = true }
                .paneNavItem(
                    cornerRadius = 8.dp,
                    onActivate = { draft = value; editing = true },
                    highlightColor = NavHighlight,
                    tapToSelect = true,
                )
                .padding(vertical = TightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextPrimary, fontSize = ValueSize, modifier = Modifier.weight(1f))
        Text(
            value.ifBlank { placeholder },
            color = if (value.isBlank()) TextSecondary else AccentBlue,
            fontSize = ValueSize,
        )
    }
}

@Composable
private fun RetroGeneralSection(
    state: RetroSettingsState,
    onPickArtwork: ((LibraryShortcutArtwork.LibraryArtworkSlot) -> Unit)? = null,
    onRemoveArtwork: ((LibraryShortcutArtwork.LibraryArtworkSlot) -> Unit)? = null,
    onImportBios: (() -> Unit)? = null,
) {
    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_game))
        RetroInfoRow(stringResource(R.string.retro_gs_label_name), state.name)
        RetroInfoRow(stringResource(R.string.retro_gs_label_system), state.system?.displayName ?: "")
        RetroInfoRow(stringResource(R.string.retro_gs_label_emulator_core), state.system?.coreFileName ?: "")
        RetroInfoRow(stringResource(R.string.retro_gs_label_rom_path), state.romPath)
        if (state.system?.isExternal == true) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val ps2Prefs = remember(context) { context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE) }
            var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            @Suppress("UNUSED_EXPRESSION") version
            RetroSettingSwitch(stringResource(R.string.retro_gs_fast_boot), ps2Prefs.getBoolean("wn.ps2.fastboot", true)) {
                ps2Prefs.edit().putBoolean("wn.ps2.fastboot", it).apply(); version++
            }
        }
    }
    if (state.system?.needsBios == true && onImportBios != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Spacer(Modifier.height(ItemGap))
        RetroSettingGroup {
            RetroGroupTitle(stringResource(R.string.retro_gs_group_bios, state.system.shortName.uppercase()))
            state.biosRefresh
            val dir = RetroCoreManager.systemDir(context)
            val installed = state.system.biosFiles.filter { java.io.File(dir, it).isFile }
            RetroInfoRow(
                stringResource(R.string.retro_gs_label_installed),
                if (installed.isEmpty()) stringResource(R.string.retro_gs_bios_none, state.system.shortName) else installed.joinToString(", "),
            )
            androidx.compose.material3.Button(
                onClick = { onImportBios() },
                modifier = Modifier.fillMaxWidth().padding(top = ItemGap),
            ) {
                androidx.compose.material3.Text(stringResource(R.string.retro_gs_import_bios))
            }
            if (installed.isNotEmpty()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        installed.forEach { java.io.File(dir, it).delete() }
                        state.biosRefresh++
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = ItemGap),
                ) {
                    androidx.compose.material3.Text(stringResource(R.string.retro_gs_remove_bios))
                }
            }
        }
    }
    if (onPickArtwork != null && onRemoveArtwork != null) {
        Spacer(Modifier.height(ItemGap))
        RetroSettingGroup {
            RetroGroupTitle(stringResource(R.string.retro_gs_group_library_artwork))
            Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
                Box(Modifier.weight(1f)) {
                    RetroArtworkRow(
                        title = stringResource(R.string.retro_gs_artwork_game_card),
                        selected = state.artworkSelected[LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD] == true,
                        onPick = { onPickArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD) },
                        onRemove = { onRemoveArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD) },
                    )
                }
                Box(Modifier.weight(1f)) {
                    RetroArtworkRow(
                        title = stringResource(R.string.retro_gs_artwork_grid),
                        selected = state.artworkSelected[LibraryShortcutArtwork.LibraryArtworkSlot.GRID] == true,
                        onPick = { onPickArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.GRID) },
                        onRemove = { onRemoveArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.GRID) },
                    )
                }
            }
            Spacer(Modifier.height(ItemGap))
            Row(horizontalArrangement = Arrangement.spacedBy(ItemGap)) {
                Box(Modifier.weight(1f)) {
                    RetroArtworkRow(
                        title = stringResource(R.string.retro_gs_artwork_carousel),
                        selected = state.artworkSelected[LibraryShortcutArtwork.LibraryArtworkSlot.CAROUSEL] == true,
                        onPick = { onPickArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.CAROUSEL) },
                        onRemove = { onRemoveArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.CAROUSEL) },
                    )
                }
                Box(Modifier.weight(1f)) {
                    RetroArtworkRow(
                        title = stringResource(R.string.retro_gs_artwork_list),
                        selected = state.artworkSelected[LibraryShortcutArtwork.LibraryArtworkSlot.LIST] == true,
                        onPick = { onPickArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.LIST) },
                        onRemove = { onRemoveArtwork(LibraryShortcutArtwork.LibraryArtworkSlot.LIST) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RetroArtworkRow(
    title: String,
    selected: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupCorner))
                .background(InputSurface)
                .border(1.dp, InputBorder, RoundedCornerShape(GroupCorner))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = ValueSize,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.retro_gs_artwork_custom_set),
                        color = TextSecondary,
                        fontSize = LabelSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!selected) {
                    RetroArtworkActionButton(stringResource(R.string.retro_gs_artwork_set_image), AccentBlue, onPick)
                } else {
                    RetroArtworkActionButton(stringResource(R.string.retro_gs_artwork_remove), DangerRed, onRemove)
                }
            }
        }
    }
}

@Composable
private fun RetroArtworkActionButton(
    text: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.08f))
                .border(1.dp, tint.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                .paneNavItem(cornerRadius = 9.dp, onActivate = { onClick() }, highlightColor = NavHighlight)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = LabelSize,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RetroGraphicsSection(state: RetroSettingsState) {
    if (state.system?.isExternal == true) {
        RetroPs2GraphicsSection()
        return
    }
    val shaderLabels = listOf(
        stringResource(R.string.retro_gs_shader_default),
        stringResource(R.string.retro_gs_shader_crt),
        stringResource(R.string.retro_gs_shader_lcd),
        stringResource(R.string.retro_gs_shader_sharp),
    )
    val upscaleLabels = listOf(
        stringResource(R.string.retro_gs_upscale_2x),
        stringResource(R.string.retro_gs_upscale_4x),
        stringResource(R.string.retro_gs_upscale_native),
    )
    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_video))
        RetroSettingDropdown(
            label = stringResource(R.string.retro_gs_video_filter),
            entries = shaderLabels,
            selectedIndex = SHADER_KEYS.indexOf(state.shader).coerceAtLeast(0),
            onSelected = { state.shader = SHADER_KEYS[it] },
        )
        RetroSettingSwitch(
            label = stringResource(R.string.retro_gs_sgsr),
            checked = state.sgsr,
            onCheckedChange = { state.sgsr = it },
        )
        if (state.sgsr) {
            RetroSettingDropdown(
                label = stringResource(R.string.retro_gs_sgsr_upscale),
                entries = upscaleLabels,
                selectedIndex = UPSCALE_KEYS.indexOf(state.upscale).coerceAtLeast(0),
                onSelected = { state.upscale = UPSCALE_KEYS[it] },
            )
        }
        RetroSettingSwitch(
            label = stringResource(R.string.retro_gs_performance_hud),
            checked = state.hud,
            onCheckedChange = { state.hud = it },
        )
    }
    if (state.coreOptions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        RetroSettingGroup {
            RetroGroupTitle((state.system?.shortName ?: stringResource(R.string.retro_gs_group_core)).uppercase())
            state.coreOptions.forEach { option ->
                val current = state.optionValues[option.key] ?: option.defaultValue
                RetroSettingDropdown(
                    label = androidx.compose.ui.res.stringResource(option.label),
                    entries = option.valueLabels.map { androidx.compose.ui.res.stringResource(it) },
                    selectedIndex = option.values.indexOf(current).coerceAtLeast(0),
                    onSelected = { state.optionValues[option.key] = option.values[it] },
                )
            }
        }
    }
}

@Composable
private fun RetroPs2GraphicsSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE) }
    var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    @Suppress("UNUSED_EXPRESSION") version

    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply(); version++ }
    fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); version++ }
    fun putStr(key: String, value: String) { prefs.edit().putString(key, value).apply(); version++ }
    fun putFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply(); version++ }

    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_video))
        val rendererKeys = listOf("vulkan", "opengl", "software")
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_renderer),
            listOf(
                stringResource(R.string.retro_gs_renderer_vulkan),
                stringResource(R.string.retro_gs_renderer_opengl),
                stringResource(R.string.retro_gs_renderer_software),
            ),
            rendererKeys.indexOf(prefs.getString("wn.ps2.renderer", "vulkan")).coerceAtLeast(0),
        ) { putStr("wn.ps2.renderer", rendererKeys[it]) }
        val ps2Drivers = remember { com.armsx2.CustomDriver.listInstalled(context) }
        val driverIds = remember(ps2Drivers) { listOf("") + ps2Drivers.map { it.id } }
        val driverLabels = listOf(stringResource(R.string.retro_gpu_driver_system)) + ps2Drivers.map { it.name }
        val curDriver = (prefs.getString("wn.ps2.driver", "") ?: "").let { if (it.equals("system", true)) "" else it }
        RetroSettingDropdown(
            stringResource(R.string.retro_gpu_driver),
            driverLabels,
            driverIds.indexOf(curDriver).coerceAtLeast(0),
        ) { putStr("wn.ps2.driver", driverIds[it]) }
        if (ps2Drivers.isEmpty()) {
            Text(
                stringResource(R.string.retro_gpu_driver_hint),
                color = TextDim,
                fontSize = LabelSize,
                modifier = Modifier.padding(top = TightGap),
            )
        }
        val scales = listOf(1f, 1.5f, 2f, 3f, 4f)
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_resolution_scale),
            listOf(
                stringResource(R.string.retro_gs_scale_1x_native),
                stringResource(R.string.retro_gs_scale_1_5x),
                stringResource(R.string.retro_gs_scale_2x),
                stringResource(R.string.retro_gs_scale_3x),
                stringResource(R.string.retro_gs_scale_4x),
            ),
            scales.indexOfFirst { kotlin.math.abs(it - prefs.getFloat("wn.ps2.upscale", 1f)) < 0.01f }.coerceAtLeast(0),
        ) { putFloat("wn.ps2.upscale", scales[it]) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_aspect_ratio),
            listOf(
                stringResource(R.string.retro_gs_aspect_stretch),
                stringResource(R.string.retro_gs_aspect_auto_standard),
                stringResource(R.string.retro_gs_aspect_4_3),
                stringResource(R.string.retro_gs_aspect_16_9),
            ),
            prefs.getInt("wn.ps2.aspect", 1).coerceIn(0, 3),
        ) { putInt("wn.ps2.aspect", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_fmv_aspect_ratio),
            listOf(
                stringResource(R.string.retro_gs_off),
                stringResource(R.string.retro_gs_aspect_auto_standard),
                stringResource(R.string.retro_gs_aspect_4_3),
                stringResource(R.string.retro_gs_aspect_16_9),
            ),
            prefs.getInt("wn.ps2.fmvaspect", 0).coerceIn(0, 3),
        ) { putInt("wn.ps2.fmvaspect", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_deinterlace_mode),
            listOf(
                stringResource(R.string.retro_gs_deint_auto),
                stringResource(R.string.retro_gs_deint_off),
                stringResource(R.string.retro_gs_deint_weave_tff),
                stringResource(R.string.retro_gs_deint_weave_bff),
                stringResource(R.string.retro_gs_deint_bob_tff),
                stringResource(R.string.retro_gs_deint_bob_bff),
                stringResource(R.string.retro_gs_deint_blend_tff),
                stringResource(R.string.retro_gs_deint_blend_bff),
                stringResource(R.string.retro_gs_deint_adaptive_tff),
                stringResource(R.string.retro_gs_deint_adaptive_bff),
            ),
            prefs.getInt("wn.ps2.deinterlace", 0).coerceIn(0, 9),
        ) { putInt("wn.ps2.deinterlace", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_display_filter),
            listOf(
                stringResource(R.string.retro_gs_filter_nearest),
                stringResource(R.string.retro_gs_filter_bilinear_smooth),
                stringResource(R.string.retro_gs_filter_bilinear_sharp),
            ),
            prefs.getInt("wn.ps2.displayfilter", 1).coerceIn(0, 2),
        ) { putInt("wn.ps2.displayfilter", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_texture_filter),
            listOf(
                stringResource(R.string.retro_gs_filter_nearest),
                stringResource(R.string.retro_gs_filter_bilinear_forced),
                stringResource(R.string.retro_gs_filter_bilinear_ps2),
                stringResource(R.string.retro_gs_filter_bilinear_sprites),
            ),
            prefs.getInt("wn.ps2.filter", 2).coerceIn(0, 3),
        ) { putInt("wn.ps2.filter", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_mipmapping), prefs.getBoolean("wn.ps2.mipmap", true)) { putBool("wn.ps2.mipmap", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_blending_accuracy),
            listOf(
                stringResource(R.string.retro_gs_blend_minimum),
                stringResource(R.string.retro_gs_blend_basic),
                stringResource(R.string.retro_gs_blend_medium),
                stringResource(R.string.retro_gs_blend_high),
                stringResource(R.string.retro_gs_blend_full),
                stringResource(R.string.retro_gs_blend_maximum),
            ),
            prefs.getInt("wn.ps2.blend", 1).coerceIn(0, 5),
        ) { putInt("wn.ps2.blend", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_anti_blur), prefs.getBoolean("wn.ps2.antiblur", true)) { putBool("wn.ps2.antiblur", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_crt_tv_shader),
            listOf(
                stringResource(R.string.retro_gs_off),
                stringResource(R.string.retro_gs_tvshader_scanline),
                stringResource(R.string.retro_gs_tvshader_diagonal),
                stringResource(R.string.retro_gs_tvshader_triangular),
                stringResource(R.string.retro_gs_tvshader_wave),
                stringResource(R.string.retro_gs_tvshader_lottes),
                stringResource(R.string.retro_gs_tvshader_4xrgss),
                stringResource(R.string.retro_gs_tvshader_nxagss),
            ),
            prefs.getInt("wn.ps2.tvshader", 0).coerceIn(0, 7),
        ) { putInt("wn.ps2.tvshader", it) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_frame_skip),
            listOf(
                stringResource(R.string.retro_gs_off),
                stringResource(R.string.retro_gs_frameskip_1),
                stringResource(R.string.retro_gs_frameskip_2),
                stringResource(R.string.retro_gs_frameskip_3),
            ),
            prefs.getInt("wn.ps2.frameskip", 0).coerceIn(0, 3),
        ) { putInt("wn.ps2.frameskip", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_widescreen_patches), prefs.getBoolean("wn.ps2.widescreen", false)) { putBool("wn.ps2.widescreen", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_no_interlace_patches), prefs.getBoolean("wn.ps2.nointerlace", false)) { putBool("wn.ps2.nointerlace", it) }
    }
}

@Composable
private fun RetroPs2PerformanceSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE) }
    var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    @Suppress("UNUSED_EXPRESSION") version

    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply(); version++ }
    fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); version++ }

    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_performance))
        val rateValues = listOf(-3, -2, -1, 0, 1, 2, 3)
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_ee_cycle_rate),
            listOf(
                stringResource(R.string.retro_gs_rate_50),
                stringResource(R.string.retro_gs_rate_60),
                stringResource(R.string.retro_gs_rate_75),
                stringResource(R.string.retro_gs_rate_100_default),
                stringResource(R.string.retro_gs_rate_130),
                stringResource(R.string.retro_gs_rate_180),
                stringResource(R.string.retro_gs_rate_300),
            ),
            rateValues.indexOf(prefs.getInt("wn.ps2.eeRate", 0).coerceIn(-3, 3)).coerceAtLeast(0),
        ) { putInt("wn.ps2.eeRate", rateValues[it]) }
        RetroSettingDropdown(
            stringResource(R.string.retro_gs_ee_cycle_skip),
            listOf(
                stringResource(R.string.retro_gs_off),
                stringResource(R.string.retro_gs_num_1),
                stringResource(R.string.retro_gs_num_2),
                stringResource(R.string.retro_gs_num_3),
            ),
            prefs.getInt("wn.ps2.eeSkip", 0).coerceIn(0, 3),
        ) { putInt("wn.ps2.eeSkip", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_mtvu), prefs.getBoolean("wn.ps2.mtvu", true)) { putBool("wn.ps2.mtvu", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_instant_vu1), prefs.getBoolean("wn.ps2.instantVu1", true)) { putBool("wn.ps2.instantVu1", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_vu_flag_hack), prefs.getBoolean("wn.ps2.vuFlagHack", true)) { putBool("wn.ps2.vuFlagHack", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_intc_spin), prefs.getBoolean("wn.ps2.intc", true)) { putBool("wn.ps2.intc", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_wait_loop), prefs.getBoolean("wn.ps2.waitloop", true)) { putBool("wn.ps2.waitloop", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_fast_cdvd), prefs.getBoolean("wn.ps2.fastCdvd", false)) { putBool("wn.ps2.fastCdvd", it) }
    }
}

@Composable
private fun RetroPs2HudSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE) }
    var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    @Suppress("UNUSED_EXPRESSION") version

    fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); version++ }

    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_performance_hud))
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_fps), prefs.getBoolean("wn.osd.fps", false)) { putBool("wn.osd.fps", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_emulation_speed), prefs.getBoolean("wn.osd.speed", false)) { putBool("wn.osd.speed", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_internal_resolution), prefs.getBoolean("wn.osd.res", false)) { putBool("wn.osd.res", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_cpu_usage), prefs.getBoolean("wn.osd.cpu", false)) { putBool("wn.osd.cpu", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_gpu_usage), prefs.getBoolean("wn.osd.gpu", false)) { putBool("wn.osd.gpu", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_frame_times), prefs.getBoolean("wn.osd.frametimes", false)) { putBool("wn.osd.frametimes", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_gs_stats), prefs.getBoolean("wn.osd.gsstats", false)) { putBool("wn.osd.gsstats", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_input_display), prefs.getBoolean("wn.osd.inputs", false)) { putBool("wn.osd.inputs", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_hw_info), prefs.getBoolean("wn.osd.hwinfo", false)) { putBool("wn.osd.hwinfo", it) }
        RetroSettingSwitch(stringResource(R.string.retro_gs_hud_version), prefs.getBoolean("wn.osd.version", false)) { putBool("wn.osd.version", it) }
    }
}

@Composable
private fun RetroPs2OnlineSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE) }
    var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    @Suppress("UNUSED_EXPRESSION") version

    fun putBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply(); version++ }
    fun putStr(key: String, value: String) { prefs.edit().putString(key, value).apply(); version++ }

    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_online_dev9))
        val onlineEnabled = prefs.getBoolean("wn.ps2.net.enable", false)
        RetroSettingSwitch(stringResource(R.string.retro_gs_enable_online), onlineEnabled) { putBool("wn.ps2.net.enable", it) }
        RetroSettingSwitch(stringResource(R.string.retro_ps2_hdd), prefs.getBoolean("wn.ps2.hdd", false)) { putBool("wn.ps2.hdd", it) }
        Text(
            stringResource(R.string.retro_ps2_hdd_desc),
            color = TextDim,
            fontSize = LabelSize,
            modifier = Modifier.padding(top = TightGap),
        )
        if (onlineEnabled) {
            val deviceKeys = listOf("Auto", "Wi-Fi")
            val deviceLabels = listOf(
                stringResource(R.string.retro_gs_net_auto),
                stringResource(R.string.retro_gs_net_wifi),
            )
            RetroSettingDropdown(
                stringResource(R.string.retro_gs_ethernet_device), deviceLabels,
                deviceKeys.indexOf(prefs.getString("wn.ps2.net.ethdevice", "Auto")).coerceAtLeast(0),
            ) { putStr("wn.ps2.net.ethdevice", deviceKeys[it]) }
            val dnsModeKeys = listOf("Manual", "Auto", "Internal")
            val dnsModeLabels = listOf(
                stringResource(R.string.retro_gs_dns_manual),
                stringResource(R.string.retro_gs_net_auto),
                stringResource(R.string.retro_gs_dns_internal),
            )
            RetroSettingDropdown(
                stringResource(R.string.retro_gs_dns_mode), dnsModeLabels,
                dnsModeKeys.indexOf(prefs.getString("wn.ps2.net.dnsmode", "Manual")).coerceAtLeast(0),
            ) { putStr("wn.ps2.net.dnsmode", dnsModeKeys[it]) }
            RetroSettingTextField(stringResource(R.string.retro_gs_primary_dns), prefs.getString("wn.ps2.net.dns1", PS2_DEFAULT_DNS).orEmpty(), PS2_DEFAULT_DNS) { putStr("wn.ps2.net.dns1", it) }
            RetroSettingTextField(stringResource(R.string.retro_gs_secondary_dns), prefs.getString("wn.ps2.net.dns2", "").orEmpty(), stringResource(R.string.retro_gs_dns_optional)) { putStr("wn.ps2.net.dns2", it) }
            RetroSettingSwitch(stringResource(R.string.retro_gs_auto_ip_dhcp), prefs.getBoolean("wn.ps2.net.dhcp", true)) { putBool("wn.ps2.net.dhcp", it) }
        }
    }
}

@Composable
private fun RetroInputSection(state: RetroSettingsState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs =
        remember(context) {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        }
    var haptic by remember { mutableStateOf(prefs.getFloat("retro_haptic_strength", 0.4f)) }
    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_input))
        RetroSettingSwitch(
            label = stringResource(R.string.retro_gs_on_screen_controls),
            checked = state.touchControls,
            onCheckedChange = { state.touchControls = it },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = TightGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.retro_gs_haptic_feedback),
                color = TextPrimary,
                fontSize = ValueSize,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(haptic * 100).toInt()}%",
                color = TextSecondary,
                fontSize = ValueSize,
            )
        }
        androidx.compose.material3.Slider(
            value = haptic,
            onValueChange = { value ->
                haptic = value
                prefs.edit().putFloat("retro_haptic_strength", value).apply()
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().height(26.dp),
        )
    }
}

@Composable
private fun RetroAudioSection(state: RetroSettingsState) {
    if (state.system?.isExternal == true) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val prefs = remember(context) { context.getSharedPreferences("ARMSX2", android.content.Context.MODE_PRIVATE) }
        var version by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        @Suppress("UNUSED_EXPRESSION") version
        var vol by remember { mutableStateOf(prefs.getInt("wn.ps2.volume", 100)) }
        RetroSettingGroup {
            RetroGroupTitle(stringResource(R.string.retro_gs_group_audio))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = TightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.retro_gs_volume), color = TextPrimary, fontSize = ValueSize, modifier = Modifier.weight(1f))
                Text("$vol%", color = TextSecondary, fontSize = ValueSize)
            }
            androidx.compose.material3.Slider(
                value = vol.toFloat(),
                onValueChange = { vol = it.toInt() },
                onValueChangeFinished = { prefs.edit().putInt("wn.ps2.volume", vol).apply() },
                valueRange = 0f..200f,
                modifier = Modifier.fillMaxWidth().height(26.dp),
            )
            RetroSettingSwitch(stringResource(R.string.retro_gs_mute), prefs.getBoolean("wn.ps2.muted", false)) {
                prefs.edit().putBoolean("wn.ps2.muted", it).apply(); version++
            }
            RetroSettingSwitch(stringResource(R.string.retro_gs_time_stretch), prefs.getBoolean("wn.ps2.timestretch", true)) {
                prefs.edit().putBoolean("wn.ps2.timestretch", it).apply(); version++
            }
            val bufferValues = listOf(40, 50, 60, 80, 100, 120, 160, 200)
            RetroSettingDropdown(
                stringResource(R.string.retro_gs_audio_buffer),
                bufferValues.map { context.getString(R.string.retro_gs_ms, it) },
                bufferValues.indexOf(prefs.getInt("wn.ps2.audiobuffer", 50)).coerceAtLeast(0),
            ) {
                prefs.edit().putInt("wn.ps2.audiobuffer", bufferValues[it]).apply(); version++
            }
            val latencyValues = listOf(10, 15, 20, 30, 40, 60, 80, 100)
            RetroSettingDropdown(
                stringResource(R.string.retro_gs_audio_latency),
                latencyValues.map { context.getString(R.string.retro_gs_ms, it) },
                latencyValues.indexOf(prefs.getInt("wn.ps2.audiolatency", 20)).coerceAtLeast(0),
            ) {
                prefs.edit().putInt("wn.ps2.audiolatency", latencyValues[it]).apply(); version++
            }
            RetroSettingSwitch(stringResource(R.string.retro_gs_swap_stereo), prefs.getBoolean("wn.ps2.swap", false)) {
                prefs.edit().putBoolean("wn.ps2.swap", it).apply(); version++
            }
        }
        return
    }
    RetroSettingGroup {
        RetroGroupTitle(stringResource(R.string.retro_gs_group_audio))
        RetroSettingSwitch(
            label = stringResource(R.string.retro_gs_sound),
            checked = state.audio,
            onCheckedChange = { state.audio = it },
        )
    }
}
