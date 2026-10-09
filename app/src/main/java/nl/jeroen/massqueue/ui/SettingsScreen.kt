package nl.jeroen.massqueue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.jeroen.massqueue.AppLanguage
import nl.jeroen.massqueue.BuildConfig
import nl.jeroen.massqueue.DEFAULT_SENDSPIN_LOCAL_URL
import nl.jeroen.massqueue.MassLocation
import nl.jeroen.massqueue.MassPlayer
import nl.jeroen.massqueue.PhonePlayerStatus
import nl.jeroen.massqueue.SendspinAudioQuality
import nl.jeroen.massqueue.tr
import nl.jeroen.massqueue.ui.theme.AppTheme
import nl.jeroen.massqueue.ui.theme.colorSchemeFor

/**
 * Modern retro-futuristic Settings Screen for SpinFlow
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialUrl: String,
    initialToken: String,
    locations: List<MassLocation>,
    activeLocationId: String?,
    players: List<MassPlayer>,
    volumeControlPlayerIds: Set<String>,
    localPlayerIds: Set<String>,
    hiddenPlayerIds: Set<String>,
    playerAliases: Map<String, String>,
    onSave: (url: String, token: String) -> Unit,
    onAddLocation: (String) -> Unit,
    onDeleteLocation: (String) -> Unit,
    onSelectLocation: (String) -> Unit,
    onPinLocation: () -> Unit,
    onToggleVolumePlayer: (String) -> Unit,
    onToggleLocalPlayer: (String) -> Unit,
    onToggleHiddenPlayer: (String) -> Unit,
    onSetPlayerAlias: (String, String) -> Unit,
    selectedTheme: AppTheme = AppTheme.CASSETTE,
    onSelectTheme: ((AppTheme) -> Unit)? = null,
    selectedLanguage: AppLanguage = AppLanguage.SYSTEM,
    onSelectLanguage: ((AppLanguage) -> Unit)? = null,
    showCompactHeader: Boolean = false,
    onToggleCompactHeader: ((Boolean) -> Unit)? = null,
    discoPlayerId: String? = null,
    onSelectDiscoPlayer: ((String) -> Unit)? = null,
    pinnedPlayerIds: Set<String> = emptySet(),
    onTogglePinnedPlayer: ((String) -> Unit)? = null,
    showMaHiddenPlayers: Boolean = false,
    onToggleShowMaHiddenPlayers: ((Boolean) -> Unit)? = null,
    phonePlayerEnabled: Boolean = false,
    phonePlayerName: String = "",
    phonePlayerLocalUrl: String = DEFAULT_SENDSPIN_LOCAL_URL,
    phonePlayerStatus: PhonePlayerStatus = PhonePlayerStatus(),
    onTogglePhonePlayer: ((Boolean) -> Unit)? = null,
    onSavePhonePlayer: ((name: String, localUrl: String) -> Unit)? = null,
    phonePlayerAudioQuality: SendspinAudioQuality = SendspinAudioQuality.ORIGINAL,
    onSelectPhonePlayerAudioQuality: ((SendspinAudioQuality) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var token by remember(initialToken) { mutableStateOf(initialToken) }

    var showVolumePlayers by remember { mutableStateOf(false) }
    var showLocalPlayers by remember { mutableStateOf(false) }
    var showHiddenPlayers by remember { mutableStateOf(false) }
    var showAliases by remember { mutableStateOf(false) }
    var showDiscoPlayer by remember { mutableStateOf(false) }
    var showPinnedPlayers by remember { mutableStateOf(false) }
    var showAddLocation by remember { mutableStateOf(false) }
    var newLocationName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            tr("Instellingen", "Settings"),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "SpinFlow & Music Assistant",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Terug", "Back"))
                        }
                    } else {
                        IconButton(onClick = { if (url.isNotBlank()) onSave(url.trim(), token.trim()) }) {
                            Icon(Icons.Default.Tune, contentDescription = tr("Instellingen", "Settings"))
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = { onSave(url.trim(), token.trim()) },
                        enabled = url.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Opslaan", "Save"), fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Server Configuration Card
            item {
                SettingsSectionCard {
                    // Uitklapbaar zoals Kleurthema; bij de eerste start (nog geen adres) meteen open
                    var serverExpanded by remember { mutableStateOf(initialUrl.isBlank()) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent, // geen binnenvlak: icoon op één lijn met de andere kaarten
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { serverExpanded = !serverExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsRoundIcon(Icons.Outlined.Dns)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Music Assistant Server",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    url.trim().ifBlank { tr("Nog niet ingesteld", "Not set yet") },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (serverExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (serverExpanded) tr("Server-instellingen inklappen", "Collapse server settings") else tr("Server-instellingen uitklappen", "Expand server settings")
                            )
                        }
                    }

                    if (serverExpanded) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(tr("Server Adres (URL)", "Server address (URL)")) },
                            placeholder = { Text("https://homeassistant.<tailnet>.ts.net") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        var showToken by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text(tr("API Access Token (Optioneel)", "API access token (optional)")) },
                            placeholder = { Text(tr("Plak hier je long-lived access token", "Paste your long-lived access token here")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showToken) tr("Token verbergen", "Hide token") else tr("Token tonen", "Show token")
                                    )
                                }
                            },
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Telefoon als speler (Sendspin)
            item {
                // Geen kop: de rij met de schakelaar heeft de titel al
                SettingsSectionCard {
                    // Zelfde uitklap-patroon als Kleurthema: naam en adres pas na uitklappen
                    var phoneExpanded by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent, // geen binnenvlak: icoon op één lijn met de andere kaarten
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { phoneExpanded = !phoneExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsRoundIcon(Icons.Outlined.PhoneAndroid)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tr("Telefoon als speler", "Phone as player"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (phonePlayerEnabled) phonePlayerStatus.text
                                    else tr("Uit: deze telefoon verschijnt niet als speler in MA", "Off: this phone does not appear as a player in MA"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (phonePlayerEnabled && phonePlayerStatus.isError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = phonePlayerEnabled,
                                onCheckedChange = { onTogglePhonePlayer?.invoke(it) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (phoneExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (phoneExpanded) tr("Speler-instellingen inklappen", "Collapse player settings") else tr("Speler-instellingen uitklappen", "Expand player settings")
                            )
                        }
                    }

                    var phoneName by remember(phonePlayerName) { mutableStateOf(phonePlayerName) }
                    var phoneUrl by remember(phonePlayerLocalUrl) { mutableStateOf(phonePlayerLocalUrl) }
                    if (phoneExpanded) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phoneName,
                            onValueChange = { phoneName = it },
                            label = { Text(tr("Naam in Music Assistant", "Name in Music Assistant")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = phoneUrl,
                            onValueChange = { phoneUrl = it },
                            label = { Text(tr("Sendspin-adres thuis (terugval)", "Sendspin address at home (fallback)")) },
                            placeholder = { Text(DEFAULT_SENDSPIN_LOCAL_URL) },
                            supportingText = { Text(tr("Eerst via het server-adres hierboven, met je API-token; dit adres alleen als dat niet lukt", "First via the server address above, using your API token; this address only if that fails")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(12.dp)
                        )
                        val changed = phoneName.trim() != phonePlayerName || phoneUrl.trim() != phonePlayerLocalUrl
                        if (changed) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onSavePhonePlayer?.invoke(phoneName, phoneUrl) },
                                enabled = phoneName.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Toepassen", "Apply"), fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Audiokwaliteit: geldt meteen, ook tijdens het afspelen
                        Spacer(Modifier.height(12.dp))
                        var qualityMenuOpen by remember { mutableStateOf(false) }
                        Box {
                            OutlinedTextField(
                                value = phonePlayerAudioQuality.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(tr("Audiokwaliteit", "Audio quality")) },
                                supportingText = { Text(phonePlayerAudioQuality.description) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.GraphicEq, contentDescription = null) },
                                trailingIcon = {
                                    Icon(
                                        if (qualityMenuOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            // Leesveld vangt geen klikken: doorzichtige laag eroverheen (zonder de hulptekst)
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(bottom = 22.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { qualityMenuOpen = true }
                            )
                            DropdownMenu(
                                expanded = qualityMenuOpen,
                                onDismissRequest = { qualityMenuOpen = false }
                            ) {
                                SendspinAudioQuality.entries.forEach { q ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(q.label, fontWeight = if (q == phonePlayerAudioQuality) FontWeight.Bold else FontWeight.Normal)
                                                Text(
                                                    q.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            if (q == phonePlayerAudioQuality) Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            qualityMenuOpen = false
                                            if (q != phonePlayerAudioQuality) onSelectPhonePlayerAudioQuality?.invoke(q)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Color Theme Selection Card
            item {
                SettingsSectionCard(
                    title = tr("Kleurthema & Stijl", "Color theme & style"),
                    subtitle = tr("Kies jouw favoriete kleurenpalet voor de app", "Choose your favorite color palette for the app"),
                    icon = Icons.Outlined.Palette
                ) {
                    var themesExpanded by remember { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { themesExpanded = !themesExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThemeSwatches(selectedTheme)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    selectedTheme.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    selectedTheme.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                if (themesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (themesExpanded) tr("Thema's inklappen", "Collapse themes") else tr("Thema's uitklappen", "Expand themes")
                            )
                        }
                    }
                    if (themesExpanded) AppTheme.entries.forEach { theme ->
                        val isSelected = theme == selectedTheme
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectTheme?.invoke(theme) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectTheme?.invoke(theme) }
                                )
                                Spacer(Modifier.width(8.dp))
                                ThemeSwatches(theme)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        theme.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        theme.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Header Layout Card
            item {
                // Geen kop: de rij met de schakelaar zegt genoeg
                SettingsSectionCard {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent, // geen binnenvlak: icoon op één lijn met de andere kaarten
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleCompactHeader?.invoke(!showCompactHeader) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsRoundIcon(Icons.Outlined.ViewStream)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tr("Compacte Minimalist Header", "Compact minimalist header"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (showCompactHeader) tr("Actief: verbergt de cassette-banner voor maximale schermruimte", "On: hides the cassette banner for maximum screen space")
                                    else tr("Uitgeschakeld: toont de grote cassette-banner bovenin", "Off: shows the large cassette banner at the top"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = showCompactHeader,
                                onCheckedChange = { onToggleCompactHeader?.invoke(it) }
                            )
                        }
                    }
                }
            }

            // Taal: titel in beide talen, zodat je hem ook in een onbekende taal terugvindt
            item {
                SettingsSectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsRoundIcon(Icons.Outlined.Language)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Taal · Language",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                tr("Taal van de app", "App language"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val languages = listOf(
                        AppLanguage.SYSTEM to tr("Systeem", "System"),
                        AppLanguage.NL to "Nederlands",
                        AppLanguage.EN to "English"
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        languages.forEachIndexed { index, (language, label) ->
                            SegmentedButton(
                                selected = language == selectedLanguage,
                                onClick = { onSelectLanguage?.invoke(language) },
                                shape = SegmentedButtonDefaults.itemShape(index, languages.size)
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 2. Hardware Volume Control Card
            item {
                val selectedVolumeCount = players.count { p ->
                    volumeControlPlayerIds.contains(p.id) ||
                            volumeControlPlayerIds.contains(p.name.lowercase().trim())
                }

                SettingsActionCard(
                    title = tr("Hardware Volumeknoppen", "Hardware volume buttons"),
                    subtitle = tr("Bedien de fysieke volumeknoppen van je telefoon ($selectedVolumeCount actief)", "Use your phone's physical volume buttons ($selectedVolumeCount active)"),
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    badgeText = "$selectedVolumeCount / ${players.size}",
                    onClick = { showVolumePlayers = true }
                )
            }

            // 3. Geofencing & Locations Card
            item {
                SettingsSectionCard {
                    // Zelfde uitklap-patroon als Music Assistant Server
                    var locationsExpanded by remember { mutableStateOf(false) }
                    val activeLocationName = locations.firstOrNull { it.id == activeLocationId }?.name
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent, // geen binnenvlak: icoon op één lijn met de andere kaarten
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { locationsExpanded = !locationsExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsRoundIcon(Icons.Outlined.PinDrop)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tr("Locaties & Geofencing", "Locations & geofencing"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    activeLocationName?.let { tr("Actief: $it", "Active: $it") }
                                        ?: tr("Thuislocatie bepalen om slimme spelerfiltering in te schakelen", "Set a home location to enable smart player filtering"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (locationsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (locationsExpanded) tr("Locaties inklappen", "Collapse locations") else tr("Locaties uitklappen", "Expand locations")
                            )
                        }
                    }

                    if (locationsExpanded) {
                        Spacer(Modifier.height(8.dp))
                        locations.forEach { loc ->
                            val isSelected = loc.id == activeLocationId
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectLocation(loc.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectLocation(loc.id) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            loc.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        val lat = loc.lat
                                        val lon = loc.lon
                                        val coordsText = if (lat != null && lon != null)
                                            "GPS: ${"%.4f".format(lat)}, ${"%.4f".format(lon)}"
                                        else
                                            tr("Geen GPS coördinaten ingesteld", "No GPS coordinates set")
                                        Text(
                                            coordsText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (locations.size > 1) {
                                        IconButton(onClick = { onDeleteLocation(loc.id) }) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = tr("Verwijderen", "Delete"),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddLocation = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Locatie", "Location"))
                            }

                            Button(
                                onClick = onPinLocation,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("GPS Pin")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        val localCount = players.count { p ->
                            localPlayerIds.contains(p.id) || localPlayerIds.contains(p.name.lowercase().trim())
                        }
                        SettingsActionRow(
                            title = tr("Thuislocatie Filter (150m)", "Home location filter (150m)"),
                            subtitle = tr("Lokale speakers verbergen wanneer je buitenshuis bent", "Hide local speakers when you're away from home"),
                            icon = Icons.Outlined.HomeWork,
                            badgeText = tr("$localCount lokaal", "$localCount local"),
                            onClick = { showLocalPlayers = true }
                        )
                    }
                }
            }

            // Spelers: keuzelijst, verborgen spelers, roepnamen, vaste spelers en disco-speler
            item {
                SettingsSectionCard {
                    // Zelfde uitklap-patroon als Music Assistant Server
                    var playersExpanded by remember { mutableStateOf(false) }
                    val visibleCount = players.count { p ->
                        !(hiddenPlayerIds.contains(p.id) || hiddenPlayerIds.contains(p.name.lowercase().trim()))
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent, // geen binnenvlak: icoon op één lijn met de andere kaarten
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { playersExpanded = !playersExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsRoundIcon(Icons.Outlined.Speaker)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tr("Spelers", "Players"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    tr("Keuzelijst, roepnamen, vaste spelers en disco ($visibleCount / ${players.size} zichtbaar)", "Player list, nicknames, pinned players and disco ($visibleCount / ${players.size} visible)"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                if (playersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (playersExpanded) tr("Speler-opties inklappen", "Collapse player options") else tr("Speler-opties uitklappen", "Expand player options")
                            )
                        }
                    }

                    if (playersExpanded) {
                        Spacer(Modifier.height(8.dp))

                        SettingsActionRow(
                            title = tr("Spelers in Keuzelijst", "Players in list"),
                            subtitle = tr("Verberg of toon specifieke spelers in het hoofdmenu", "Hide or show specific players in the main menu"),
                            icon = Icons.Outlined.Visibility,
                            badgeText = "$visibleCount / ${players.size}",
                            onClick = { showHiddenPlayers = true }
                        )

                        // Spelers die in MA op "Hide player in UI: Always" staan
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleShowMaHiddenPlayers?.invoke(!showMaHiddenPlayers) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsRoundIcon(Icons.Outlined.VisibilityOff)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tr("Toon verborgen spelers", "Show hidden players"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (showMaHiddenPlayers) tr("Aan: ook spelers die in Music Assistant verborgen zijn", "On: also players that are hidden in Music Assistant")
                                    else tr("Uit: spelers die in Music Assistant verborgen zijn, staan niet in de keuzelijst", "Off: players that are hidden in Music Assistant are not in the player list"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = showMaHiddenPlayers,
                                onCheckedChange = { onToggleShowMaHiddenPlayers?.invoke(it) }
                            )
                        }

                        val aliasedCount = players.count { p -> !playerAliases[p.id].isNullOrBlank() }
                        SettingsActionRow(
                            title = tr("Speler Roepnamen (Aliassen)", "Player nicknames (aliases)"),
                            subtitle = tr("Geef speakers een aangepaste weergavenaam", "Give speakers a custom display name"),
                            icon = Icons.Outlined.Edit,
                            badgeText = if (aliasedCount > 0) tr("$aliasedCount actief", "$aliasedCount active") else tr("Instellen", "Set up"),
                            onClick = { showAliases = true }
                        )

                        // Vaste spelers (nooit onder "Overige spelers")
                        val pinnedCount = players.count { it.id in pinnedPlayerIds }
                        SettingsActionRow(
                            title = tr("Vaste Spelers", "Pinned players"),
                            subtitle = tr("Altijd bovenin de keuzelijst, nooit onder \"Overige spelers\"", "Always at the top of the player list, never under \"Other players\""),
                            icon = Icons.Outlined.PushPin,
                            badgeText = if (pinnedCount > 0) tr("$pinnedCount vast", "$pinnedCount pinned") else tr("Instellen", "Set up"),
                            onClick = { showPinnedPlayers = true }
                        )

                        val discoName = players.firstOrNull { it.id == discoPlayerId }?.name
                        SettingsActionRow(
                            title = tr("Disco-speler", "Disco player"),
                            subtitle = discoName ?: tr("Speler die de disco-schakelaar aan de groep toevoegt", "Player that the disco switch adds to the group"),
                            icon = Icons.Outlined.Lightbulb,
                            badgeText = if (discoName != null) tr("Gekozen", "Selected") else tr("Instellen", "Set up"),
                            onClick = { showDiscoPlayer = true }
                        )
                    }
                }
            }

            // App Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "SpinFlow v${BuildConfig.VERSION_NAME} — Music Assistant Client",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${BuildConfig.APPLICATION_ID} · build ${BuildConfig.BUILD_TIME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Ontwikkeld door Jeroen van Sonsbeek", "Developed by Jeroen van Sonsbeek"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    // Modal Dialog for Adding Location
    if (showAddLocation) {
        AlertDialog(
            onDismissRequest = { showAddLocation = false },
            title = { Text(tr("Nieuwe Locatie Toevoegen", "Add new location")) },
            text = {
                Column {
                    Text(
                        tr("Geef een naam op voor deze locatie (bijv. 'Thuis', 'Kantoor' of 'Vakantiehuis'):", "Enter a name for this location (e.g. 'Home', 'Office' or 'Holiday home'):"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newLocationName,
                        onValueChange = { newLocationName = it },
                        placeholder = { Text(tr("Locatienaam", "Location name")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newLocationName.isNotBlank()) {
                            onAddLocation(newLocationName.trim())
                            newLocationName = ""
                            showAddLocation = false
                        }
                    }
                ) {
                    Text(tr("Toevoegen", "Add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocation = false }) {
                    Text(tr("Annuleren", "Cancel"))
                }
            }
        )
    }

    // Modal Bottom Sheet helper functions
    if (showVolumePlayers) {
        PlayerChecklistSheet(
            title = tr("Hardware Volume Bediening", "Hardware volume control"),
            subtitle = tr("Kies voor welke spelers de volumeknoppen van je telefoon actief zijn:", "Choose which players your phone's volume buttons control:"),
            players = players,
            isChecked = { p ->
                volumeControlPlayerIds.contains(p.id) ||
                        volumeControlPlayerIds.contains(p.name.lowercase().trim())
            },
            onToggle = { onToggleVolumePlayer(it.id) },
            onDismiss = { showVolumePlayers = false }
        )
    }

    if (showLocalPlayers) {
        PlayerChecklistSheet(
            title = tr("Lokale Thuis-Spelers", "Local home players"),
            subtitle = tr("Kies welke spelers verborgen moeten worden als je meer dan 150m van huis bent:", "Choose which players to hide when you're more than 150m from home:"),
            players = players,
            isChecked = { p ->
                localPlayerIds.contains(p.id) ||
                        localPlayerIds.contains(p.name.lowercase().trim())
            },
            onToggle = { onToggleLocalPlayer(it.id) },
            onDismiss = { showLocalPlayers = false }
        )
    }

    if (showHiddenPlayers) {
        PlayerChecklistSheet(
            title = tr("Spelers in Keuzelijst", "Players in list"),
            subtitle = tr("Vink aan welke spelers zichtbaar zijn in het speler-keuzemenu:", "Check which players are visible in the player menu:"),
            players = players,
            isChecked = { p ->
                !(hiddenPlayerIds.contains(p.id) ||
                        hiddenPlayerIds.contains(p.name.lowercase().trim()))
            },
            onToggle = { onToggleHiddenPlayer(it.id) },
            onDismiss = { showHiddenPlayers = false }
        )
    }

    if (showPinnedPlayers) {
        PlayerChecklistSheet(
            title = tr("Vaste Spelers", "Pinned players"),
            subtitle = tr("Groepsleden en lichtspelers staan in de keuzelijst onder \"Overige spelers\". Vink aan welke toch altijd bovenin moeten staan:", "Group members and light players are listed under \"Other players\". Check which ones should always be at the top:"),
            players = players,
            isChecked = { p -> p.id in pinnedPlayerIds },
            onToggle = { onTogglePinnedPlayer?.invoke(it.id) },
            onDismiss = { showPinnedPlayers = false }
        )
    }

    if (showDiscoPlayer) {
        PlayerChecklistSheet(
            title = tr("Disco-speler", "Disco player"),
            subtitle = tr("Kies welke speler de disco-schakelaar aan de huidige groep toevoegt:", "Choose which player the disco switch adds to the current group:"),
            players = players,
            isChecked = { p -> p.id == discoPlayerId },
            onToggle = {
                onSelectDiscoPlayer?.invoke(it.id)
                showDiscoPlayer = false
            },
            onDismiss = { showDiscoPlayer = false }
        )
    }

    if (showAliases) {
        ModalBottomSheet(
            onDismissRequest = { showAliases = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tr("Speler Roepnamen", "Player nicknames"),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showAliases = false }) {
                        Icon(Icons.Default.Close, contentDescription = tr("Sluiten", "Close"))
                    }
                }
                Text(
                    tr("Stel een vriendelijke roepnaam in per speaker voor in de app:", "Set a friendly nickname per speaker for use in the app:"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(players) { player ->
                        Column {
                            Text(
                                tr("Originele naam: ${player.name}", "Original name: ${player.name}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedTextField(
                                value = playerAliases[player.id] ?: "",
                                onValueChange = { onSetPlayerAlias(player.id, it) },
                                placeholder = { Text(player.name) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Rond icoontje in de themakleur, vóór de titel van een instelling. */
@Composable
private fun SettingsRoundIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Kop (icoon, titel, uitleg) is optioneel
            if (title != null && subtitle != null && icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsRoundIcon(icon)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        SettingsActionRowContent(title, subtitle, icon, badgeText, Modifier.padding(16.dp))
    }
}

/** Zelfde rij als [SettingsActionCard], maar zonder eigen kaart: voor binnen een uitklapbare kaart. */
@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    onClick: () -> Unit
) {
    SettingsActionRowContent(
        title, subtitle, icon, badgeText,
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsActionRowContent(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeText: String,
    modifier: Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRoundIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Text(
                badgeText,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerChecklistSheet(
    title: String,
    subtitle: String,
    players: List<MassPlayer>,
    isChecked: (MassPlayer) -> Boolean,
    onToggle: (MassPlayer) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = tr("Sluiten", "Close"))
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(players) { player ->
                    val checked = isChecked(player)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggle(player) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(player) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                player.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small overlapping color dots previewing a theme's background, primary and secondary colors. */
@Composable
private fun ThemeSwatches(theme: AppTheme) {
    val scheme = colorSchemeFor(theme)
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        listOf(scheme.background, scheme.primary, scheme.secondary).forEach { color ->
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    }
}
