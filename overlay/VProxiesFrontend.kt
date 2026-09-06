package io.nekohasekai.sfa.vproxies

import android.net.TrafficStats
import android.os.Process
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.constant.Status
import kotlinx.coroutines.delay
import java.util.Locale

private val Navy = Color(0xFF071A3A)
private val Obsidian = Color(0xFF080F19)
private val Surface = Color(0xFF121B2E)
private val SurfaceHigh = Color(0xFF1A2540)
private val Cyan = Color(0xFF13E5FF)
private val Blue = Color(0xFF087BFF)
private val Violet = Color(0xFF8B35FF)
private val White = Color(0xFFF8FAFC)
private val Muted = Color(0xFF9AA9C4)
private val Good = Color(0xFF21E6A2)
private val Danger = Color(0xFFFF667A)
private val Warning = Color(0xFFFFC44D)
private val CardShape = RoundedCornerShape(20.dp)

internal data class UiLog(val level: String, val message: String, val time: Long = System.currentTimeMillis())

internal class VProxiesUiState {
    var tab by mutableStateOf(0)
    var identity by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberAccount by mutableStateOf(false)
    var accountSummary by mutableStateOf("Not signed in")
    var busy by mutableStateOf(false)
    var statusMessage by mutableStateOf("Ready")
    var statusError by mutableStateOf(false)
    var coreStatus by mutableStateOf(Status.Stopped)
    var connectedAt by mutableLongStateOf(0L)

    var gateways by mutableStateOf<List<String>>(emptyList())
    var selectedGateway by mutableStateOf(0)
    var proxies by mutableStateOf<List<String>>(emptyList())
    var selectedProxy by mutableStateOf(0)
    var protocols by mutableStateOf<List<String>>(emptyList())
    var selectedProtocol by mutableStateOf(0)
    var proxyDetail by mutableStateOf("Sign in to load your proxy list")

    var routingMode by mutableStateOf(0)
    var dnsThroughProxy by mutableStateOf(false)
    var preventDnsLeaks by mutableStateOf(true)

    var manualProtocol by mutableStateOf(3)
    var manualHost by mutableStateOf("")
    var manualPort by mutableStateOf("")
    var manualUsername by mutableStateOf("")
    var manualPassword by mutableStateOf("")
    var manualSni by mutableStateOf("")
    var rememberManual by mutableStateOf(false)
    val logs = mutableStateListOf<UiLog>()

    fun addLog(level: String, message: String) {
        logs.add(0, UiLog(level, message))
        while (logs.size > 300) logs.removeAt(logs.lastIndex)
    }
}

internal data class VProxiesActions(
    val power: () -> Unit,
    val login: () -> Unit,
    val gateway: (Int) -> Unit,
    val proxy: (Int) -> Unit,
    val protocol: (Int) -> Unit,
    val selectApps: () -> Unit,
    val advanced: () -> Unit,
    val checkManual: () -> Unit,
    val connectManual: () -> Unit,
)

@Composable
internal fun VProxiesFrontend(state: VProxiesUiState, actions: VProxiesActions) {
    val colors = darkColorScheme(
        primary = Cyan, secondary = Blue, tertiary = Violet,
        background = Obsidian, surface = Surface, surfaceVariant = SurfaceHigh,
        onPrimary = Obsidian, onBackground = White, onSurface = White,
        error = Danger,
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = { VProxiesBottomBar(state) },
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(Obsidian, Navy.copy(alpha = .68f), Obsidian)),
            ),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state.tab) {
                    0 -> DashboardScreen(state, actions)
                    1 -> LogsScreen(state)
                    else -> SettingsScreen(state, actions)
                }
            }
        }
    }
}

@Composable
private fun VProxiesBottomBar(state: VProxiesUiState) {
    val tabs = listOf(
        Triple("Dashboard", Icons.Default.Home, 0),
        Triple("Logs", Icons.Default.ListAlt, 1),
        Triple("Settings", Icons.Default.Settings, 2),
    )
    NavigationBar(
        containerColor = Color(0xF2182134),
        modifier = Modifier.navigationBarsPadding(),
    ) {
        tabs.forEach { (name, icon, index) ->
            NavigationBarItem(
                selected = state.tab == index,
                onClick = { state.tab = index },
                icon = { Icon(icon, name) },
                label = { Text(name, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cyan, selectedTextColor = Cyan,
                    indicatorColor = Cyan.copy(alpha = .13f),
                    unselectedIconColor = Muted, unselectedTextColor = Muted,
                ),
            )
        }
    }
}

@Composable
private fun BrandHeader(subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_vproxies_logo),
                contentDescription = "VProxies",
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text("VProxies", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        if (subtitle != null) {
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DashboardScreen(state: VProxiesUiState, actions: VProxiesActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var upRate by remember { mutableLongStateOf(0L) }
    var downRate by remember { mutableLongStateOf(0L) }
    var totalUp by remember { mutableLongStateOf(0L) }
    var totalDown by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.coreStatus) {
        var lastTx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)
        var lastRx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)
        val baseTx = lastTx
        val baseRx = lastRx
        while (state.coreStatus == Status.Started) {
            delay(1_000)
            now = System.currentTimeMillis()
            val tx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(lastTx)
            val rx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(lastRx)
            upRate = tx - lastTx
            downRate = rx - lastRx
            totalUp = tx - baseTx
            totalDown = rx - baseRx
            lastTx = tx
            lastRx = rx
        }
        if (state.coreStatus == Status.Stopped) {
            upRate = 0; downRate = 0
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { BrandHeader("Your proxies. Direct, private connections.") }
        item { PowerDial(state, actions.power, now) }
        item {
            ElevatedPanel(
                modifier = Modifier.clickable { state.tab = 2 },
                borderColor = if (state.coreStatus == Status.Started) Cyan.copy(alpha = .55f) else Color.Transparent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Router, null, tint = Cyan, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.proxies.getOrNull(state.selectedProxy) ?: "No proxy selected", color = White, fontWeight = FontWeight.SemiBold)
                        Text(state.proxyDetail, color = Muted, fontSize = 12.sp, maxLines = 2)
                    }
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TrafficCard("Upload", upRate, totalUp, Cyan, Modifier.weight(1f))
                TrafficCard("Download", downRate, totalDown, Blue, Modifier.weight(1f))
            }
        }
        item { StatusPanel(state) }
    }
}

@Composable
private fun PowerDial(state: VProxiesUiState, onClick: () -> Unit, now: Long) {
    val active = state.coreStatus == Status.Started
    val working = state.coreStatus == Status.Starting || state.coreStatus == Status.Stopping || state.busy
    val dialColor by animateColorAsState(
        when {
            state.statusError -> Danger
            active -> Cyan
            working -> Blue
            else -> Muted
        }, label = "power-color",
    )
    val elapsed = if (active && state.connectedAt > 0) now - state.connectedAt else 0L
    val label = when {
        state.busy -> "Preparing"
        state.coreStatus == Status.Starting -> "Connecting"
        state.coreStatus == Status.Started -> "Connected"
        state.coreStatus == Status.Stopping -> "Disconnecting"
        state.statusError -> "Connection failed"
        else -> "Disconnected"
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(238.dp)
                .shadow(if (active) 28.dp else 8.dp, CircleShape, ambientColor = dialColor, spotColor = dialColor)
                .background(Brush.radialGradient(listOf(dialColor.copy(alpha = .22f), Surface, Obsidian)), CircleShape)
                .border(4.dp, dialColor, CircleShape)
                .clickable(enabled = !working) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize().padding(13.dp)) {
                drawCircle(dialColor.copy(alpha = .25f), style = Stroke(2.dp.toPx()))
                drawArc(dialColor.copy(alpha = .8f), -90f, if (active) 300f else 85f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PowerSettingsNew, null, tint = dialColor, modifier = Modifier.size(55.dp))
                Spacer(Modifier.height(10.dp))
                AnimatedContent(label, label = "status-label") { Text(it, color = dialColor, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                Text(if (active) formatDuration(elapsed) else "Tap to connect", color = White.copy(alpha = .86f), fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun TrafficCard(title: String, rate: Long, total: Long, color: Color, modifier: Modifier) {
    ElevatedPanel(modifier) {
        Text(title, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        Text("${formatBytes(rate)}/s", color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(formatBytes(total), color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val step = size.width / 7f
            val values = listOf(.18f, .32f, .22f, .66f, .38f, .72f, .46f, .58f)
            for (i in 0 until values.lastIndex) {
                drawLine(
                    color.copy(alpha = .85f),
                    Offset(step * i, size.height * (1f - values[i])),
                    Offset(step * (i + 1), size.height * (1f - values[i + 1])),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(state: VProxiesUiState) {
    val color = if (state.statusError) Danger else if (state.coreStatus == Status.Started) Good else Cyan
    ElevatedPanel(borderColor = color.copy(alpha = .35f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (state.statusError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(state.statusMessage, color = color, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LogsScreen(state: VProxiesUiState) {
    var filter by remember { mutableStateOf("ALL") }
    val filters = listOf("ALL", "INFO", "WARN", "ERROR")
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp)) {
        BrandHeader()
        Spacer(Modifier.height(18.dp))
        Text("Logs", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { value ->
                val selected = filter == value
                OutlinedButton(
                    onClick = { filter = value },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Blue.copy(alpha = .22f) else Color.Transparent),
                    border = BorderStroke(1.dp, if (selected) Cyan else SurfaceHigh),
                ) { Text(value, color = if (selected) Cyan else Muted, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            val shown = state.logs.filter { filter == "ALL" || it.level == filter }
            if (shown.isEmpty()) item { Text("No events in this session yet.", color = Muted, modifier = Modifier.padding(12.dp)) }
            items(shown) { log -> LogCard(log) }
        }
    }
}

@Composable
private fun LogCard(log: UiLog) {
    val color = when (log.level) { "ERROR" -> Danger; "WARN" -> Warning; else -> Cyan }
    ElevatedPanel(padding = 12) {
        Row(verticalAlignment = Alignment.Top) {
            Text(log.level, color = Obsidian, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(color, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 3.dp))
            Spacer(Modifier.width(9.dp))
            Text(log.message, color = White.copy(alpha = .9f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen(state: VProxiesUiState, actions: VProxiesActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        BrandHeader()
        Spacer(Modifier.height(18.dp))
        Text("Settings", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        SectionTitle("Account")
        ElevatedPanel {
            VTextField(state.identity, { state.identity = it }, "Username or email", Icons.Default.Person)
            Spacer(Modifier.height(10.dp))
            VPasswordField(state.password, { state.password = it }, "Password")
            ToggleRow("Remember password", "Encrypted with Android Keystore", state.rememberAccount) { state.rememberAccount = it }
            PrimaryButton("Sign in & sync", state.busy, actions.login)
            Text(state.accountSummary, color = Muted, fontSize = 12.sp)
        }

        SectionTitle("Routing")
        ElevatedPanel {
            VDropdown("Traffic mode", listOf("Full system", "Web rules only", "Selected applications"), state.routingMode) {
                state.routingMode = it
            }
            if (state.routingMode == 2) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Choose applications", Icons.Default.Apps, actions.selectApps)
            }
            ToggleRow("DNS through proxy", "Off by default for faster startup", state.dnsThroughProxy) { state.dnsThroughProxy = it }
            ToggleRow("Prevent DNS leaks", "Capture port 53 inside the VPN", state.preventDnsLeaks) { state.preventDnsLeaks = it }
        }

        SectionTitle("Assigned proxy")
        ElevatedPanel {
            VDropdown("Gateway", state.gateways, state.selectedGateway, actions.gateway)
            Spacer(Modifier.height(10.dp))
            VDropdown("Proxy", state.proxies, state.selectedProxy, actions.proxy)
            Spacer(Modifier.height(10.dp))
            VDropdown("Protocol", state.protocols, state.selectedProtocol, actions.protocol)
            Spacer(Modifier.height(10.dp))
            Text(state.proxyDetail, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Connect assigned proxy", state.busy) {
                state.tab = 0
                actions.power()
            }
        }

        SectionTitle("Manual proxy")
        ElevatedPanel {
            VDropdown("Protocol", listOf("HTTP", "HTTPS", "SOCKS4", "SOCKS5"), state.manualProtocol) { state.manualProtocol = it }
            Spacer(Modifier.height(10.dp))
            VTextField(state.manualHost, { state.manualHost = it }, "Host or IP", Icons.Default.Router)
            Spacer(Modifier.height(10.dp))
            VTextField(state.manualPort, { state.manualPort = it.filter(Char::isDigit) }, "Port", Icons.Default.Speed, KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            VTextField(state.manualUsername, { state.manualUsername = it }, "Username (optional)", Icons.Default.Person)
            Spacer(Modifier.height(10.dp))
            VPasswordField(state.manualPassword, { state.manualPassword = it }, "Password (optional)")
            if (state.manualProtocol == 1) {
                Spacer(Modifier.height(10.dp))
                VTextField(state.manualSni, { state.manualSni = it }, "HTTPS SNI (optional)", Icons.Default.Dns)
            }
            ToggleRow("Remember manual proxy", "Encrypt credentials on this device", state.rememberManual) { state.rememberManual = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = actions.checkManual, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Cyan)) {
                    Text("Check", color = Cyan)
                }
                Button(onClick = actions.connectManual, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                    Text("Connect", color = White)
                }
            }
        }

        SectionTitle("Advanced")
        ElevatedPanel {
            SecondaryButton("Core & service settings", Icons.Default.Settings, actions.advanced)
            Text("VProxies 0.4.0 · sing-box 1.13.20", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 17.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
private fun ElevatedPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(), shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = .94f)),
        border = if (borderColor != Color.Transparent) BorderStroke(1.dp, borderColor) else null,
        elevation = CardDefaults.cardElevation(7.dp),
    ) { Column(Modifier.padding(padding.dp), content = content) }
}

@Composable
private fun VTextField(
    value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value, onValueChange, label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(icon, null, tint = Cyan) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
    )
}

@Composable
private fun VPasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onValueChange, label = { Text(label) }, singleLine = true,
        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Cyan) },
        trailingIcon = { IconButton({ visible = !visible }) { Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
    )
}

@Composable
private fun VDropdown(label: String, values: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = values.getOrNull(selected) ?: "No data", onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
            modifier = Modifier.fillMaxWidth().clickable { if (values.isNotEmpty()) expanded = true },
            enabled = false, colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = White, disabledBorderColor = Muted.copy(alpha = .55f),
                disabledLabelColor = Muted, disabledTrailingIconColor = Muted,
            ), shape = RoundedCornerShape(13.dp),
        )
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(SurfaceHigh)) {
            values.forEachIndexed { index, value ->
                DropdownMenuItem(text = { Text(value, color = White) }, onClick = { onSelected(index); expanded = false })
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked, onChecked, colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = Blue))
    }
}

@Composable
private fun PrimaryButton(title: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue, disabledContainerColor = SurfaceHigh),
    ) { Text(if (busy) "Please wait…" else title, color = White, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Cyan)
        Spacer(Modifier.width(12.dp))
        Text(title, color = White, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted)
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

private fun formatBytes(value: Long): String = when {
    value >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", value / 1_073_741_824.0)
    value >= 1_048_576 -> String.format(Locale.US, "%.1f MB", value / 1_048_576.0)
    value >= 1024 -> String.format(Locale.US, "%.1f kB", value / 1024.0)
    else -> "$value B"
}
