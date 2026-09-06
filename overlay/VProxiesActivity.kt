package io.nekohasekai.sfa.vproxies

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.bg.ServiceConnection
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.Alert
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.net.URL

private const val API_BASE_URL = "https://api.vproxies.app/api/v1/"
private const val CLIENT_NAME = "VProxies Android 0.3.2"

/**
 * VProxies clean UI layered on the official Android libbox/VpnService implementation.
 * Account passwords and source proxy credentials are intentionally never persisted.
 */
class VProxiesActivity : AppCompatActivity(), ServiceConnection.Callback {
    companion object {
        private const val PREFS = "vproxies"
        private const val PROFILE_ID = "managed_profile_id"
        private val SUPPORTED_PROTOCOLS = setOf("http", "https", "socks4", "socks5")
    }

    private lateinit var api: ApiClient
    private val gateways = mutableListOf<Gateway>()
    private val proxies = mutableListOf<ProxyItem>()
    private var pendingConfig: String? = null
    private var credentialFile: File? = null
    private lateinit var coreConnection: ServiceConnection
    private var coreStatus = Status.Stopped

    private lateinit var identityInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var gatewaySpinner: Spinner
    private lateinit var proxySpinner: Spinner
    private lateinit var protocolSpinner: Spinner
    private lateinit var routingSpinner: Spinner
    private lateinit var selectAppsButton: Button
    private lateinit var dnsThroughProxyBox: CheckBox
    private lateinit var preventDnsLeaksBox: CheckBox
    private lateinit var connectButton: Button
    private lateinit var stopButton: Button
    private lateinit var accountLabel: TextView
    private lateinit var proxyDetailLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var manualProtocolSpinner: Spinner
    private lateinit var manualHostInput: EditText
    private lateinit var manualPortInput: EditText
    private lateinit var manualUsernameInput: EditText
    private lateinit var manualPasswordInput: EditText
    private lateinit var manualSniInput: EditText

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startCore() else setStatus("Bạn chưa cấp quyền VPN.", true)
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpnPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = ApiClient(getSystemService(ConnectivityManager::class.java))
        coreConnection = ServiceConnection(this, this)
        coreConnection.connect()
        title = "VProxies"
        buildInterface()
        identityInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("identity", ""))
    }

    override fun onDestroy() {
        coreConnection.disconnect()
        super.onDestroy()
    }

    override fun onServiceStatusChanged(status: Status) {
        val previous = coreStatus
        coreStatus = status
        runOnUiThread {
            when (status) {
                Status.Starting -> setStatus("Đang khởi động VPN…")
                Status.Started -> {
                    setStatus("VPN đã kết nối. Đang kiểm tra Internet…")
                    verifyTunnelInternet()
                }
                Status.Stopping -> setStatus("Đang ngắt VPN…")
                Status.Stopped -> if (previous != Status.Stopped) setStatus("VPN đã ngắt.")
            }
        }
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        runOnUiThread {
            credentialFile?.delete()
            credentialFile = null
            setStatus("Lỗi VPN: ${message?.takeIf(String::isNotBlank) ?: type.name}", true)
        }
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(8, 14, 30)) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(36))
        }
        scroll.addView(page)

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(ImageView(this).apply {
            setImageResource(io.nekohasekai.sfa.R.drawable.ic_vproxies_logo)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) }
            contentDescription = "VProxies"
        })
        brand.addView(text("vproxies", 28f, Color.rgb(25, 216, 232), Typeface.BOLD))
        page.addView(brand)
        page.addView(text("Proxy riêng của bạn, kết nối trực tiếp", 15f, Color.rgb(184, 196, 220)))
        page.addView(space(22))

        page.addView(section("Tài khoản"))
        identityInput = input("Tên đăng nhập hoặc email", false)
        passwordInput = input("Mật khẩu", true)
        loginButton = button("Đăng nhập & đồng bộ") { login() }
        accountLabel = text("Chưa đăng nhập", 13f, Color.rgb(151, 163, 184))
        page.addView(identityInput)
        page.addView(passwordInput)
        page.addView(loginButton)
        page.addView(accountLabel)
        page.addView(space(22))

        page.addView(section("ĐỊNH TUYẾN"))
        page.addView(label("Chế độ lưu lượng"))
        routingSpinner = spinner().apply {
            adapter = adapter(listOf("Toàn hệ thống", "Chỉ web theo quy tắc", "Các ứng dụng đã chọn"))
        }
        selectAppsButton = button("Chọn ứng dụng") {
            startActivity(Intent(this, VProxiesAppPickerActivity::class.java))
        }.apply { visibility = View.GONE }
        dnsThroughProxyBox = CheckBox(this).apply {
            text = "DNS qua proxy"
            setTextColor(Color.rgb(184, 196, 220))
            isChecked = false
        }
        preventDnsLeaksBox = CheckBox(this).apply {
            text = "Chống rò rỉ DNS"
            setTextColor(Color.rgb(184, 196, 220))
            isChecked = true
        }
        page.addView(routingSpinner)
        page.addView(selectAppsButton)
        page.addView(dnsThroughProxyBox)
        page.addView(preventDnsLeaksBox)
        page.addView(text("Chế độ quy tắc chỉ đưa lưu lượng web TCP 80/443 qua proxy; lưu lượng khác đi trực tiếp.", 12f, Color.rgb(126, 139, 165)))
        page.addView(space(22))

        page.addView(section("Proxy được cấp"))
        gatewaySpinner = spinner()
        proxySpinner = spinner()
        protocolSpinner = spinner()
        proxyDetailLabel = text("Đăng nhập để tải danh sách proxy.", 13f, Color.rgb(151, 163, 184))
        page.addView(label("Máy chủ lưu proxy"))
        page.addView(gatewaySpinner)
        page.addView(label("Proxy"))
        page.addView(proxySpinner)
        page.addView(label("Giao thức kết nối"))
        page.addView(protocolSpinner)
        page.addView(proxyDetailLabel)
        page.addView(space(18))

        connectButton = button("Kết nối VPN") { connect() }.apply { isEnabled = false }
        stopButton = button("Ngắt kết nối") { stopCore() }
        page.addView(connectButton)
        page.addView(stopButton)

        page.addView(space(24))
        page.addView(section("Kết nối proxy riêng"))
        page.addView(text("Chỉ nhập proxy bạn quản lý hoặc được phép sử dụng. Thông tin xác thực không được lưu.", 12f, Color.rgb(126, 139, 165)))
        manualProtocolSpinner = spinner().apply {
            adapter = adapter(listOf("HTTP", "HTTPS", "SOCKS4", "SOCKS5"))
            setSelection(3)
        }
        manualHostInput = input("Host hoặc IP", false)
        manualPortInput = input("Port", false).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        manualUsernameInput = input("Username proxy (nếu có)", false)
        manualPasswordInput = input("Password proxy (nếu có)", true)
        manualSniInput = input("HTTPS SNI (tùy chọn)", false)
        page.addView(label("Giao thức"))
        page.addView(manualProtocolSpinner)
        page.addView(manualHostInput)
        page.addView(manualPortInput)
        page.addView(manualUsernameInput)
        page.addView(manualPasswordInput)
        page.addView(manualSniInput)
        page.addView(button("Kiểm tra proxy") { checkManualProxy() })
        page.addView(button("Kết nối proxy riêng") { connectManual() })

        statusLabel = text("Sẵn sàng", 14f, Color.rgb(126, 231, 166), Typeface.BOLD)
        statusLabel.gravity = Gravity.CENTER_HORIZONTAL
        statusLabel.setPadding(0, dp(12), 0, dp(12))
        page.addView(statusLabel)

        page.addView(button("Cài đặt nâng cao / chọn ứng dụng") {
            startActivity(Intent(this, MainActivity::class.java))
        })
        page.addView(text("DNS qua proxy mặc định tắt để tăng tốc và tránh lỗi bootstrap DNS.", 12f, Color.rgb(126, 139, 165)))

        gatewaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (api.signedIn && position in gateways.indices) loadProxies(gateways[position].id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        proxySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateProxySelection(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        routingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectAppsButton.visibility = if (position == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        setContentView(scroll)
    }

    private fun login() {
        val identity = identityInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (identity.isBlank() || password.isBlank()) {
            setStatus("Hãy nhập tên đăng nhập/email và mật khẩu.", true)
            return
        }
        busy(true, "Đang đăng nhập…")
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.login(identity, password) } }
                .onSuccess { login ->
                    passwordInput.text.clear()
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("identity", identity).apply()
                    accountLabel.text = "${login.userName} · ${login.packageName.ifBlank { login.status }} · còn ${login.remainingDays} ngày"
                    if (!login.active) {
                        connectButton.isEnabled = false
                        setStatus("Tài khoản chưa có gói sử dụng đang hoạt động.", true)
                    } else {
                        setStatus("Đăng nhập thành công. Đang đồng bộ proxy…")
                        loadGateways()
                    }
                }
                .onFailure { setStatus(it.message ?: "Đăng nhập thất bại.", true) }
            busy(false)
        }
    }

    private fun loadGateways() {
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.gateways() } }
                .onSuccess { items ->
                    gateways.clear()
                    gateways.addAll(items)
                    gatewaySpinner.adapter = adapter(items.map { it.display })
                    if (items.isEmpty()) setStatus("Tài khoản chưa được cấp máy chủ proxy.", true)
                }
                .onFailure { setStatus(it.message ?: "Không tải được máy chủ.", true) }
        }
    }

    private fun loadProxies(gatewayId: String) {
        lifecycleScope.launch {
            setStatus("Đang tải proxy…")
            runCatching { withContext(Dispatchers.IO) { api.proxies(gatewayId) } }
                .onSuccess { items ->
                    proxies.clear()
                    proxies.addAll(items)
                    proxySpinner.adapter = adapter(items.map { it.display })
                    connectButton.isEnabled = false
                    if (items.isEmpty()) setStatus("Máy chủ này chưa có proxy khả dụng.", true)
                    else setStatus("Đã đồng bộ ${items.size} proxy.")
                }
                .onFailure { setStatus(it.message ?: "Không tải được proxy.", true) }
        }
    }

    private fun updateProxySelection(position: Int) {
        if (position !in proxies.indices) return
        val proxy = proxies[position]
        val supported = proxy.protocols.ifEmpty { listOf(proxy.protocol) }
            .map(String::lowercase).distinct().filter { it in SUPPORTED_PROTOCOLS }
        protocolSpinner.adapter = adapter(supported.map { it.uppercase() })
        val primaryIndex = supported.indexOf(proxy.protocol.lowercase())
        if (primaryIndex >= 0) protocolSpinner.setSelection(primaryIndex)
        val endpoint = if (proxy.showHostPort && proxy.host.isNotBlank()) "${proxy.host}:${proxy.port}" else "IP/port được ẩn theo chính sách"
        proxyDetailLabel.text = "${proxy.location} · ${proxy.status.ifBlank { "Chưa xác định" }} · ${proxy.latency ?: "—"} ms\n$endpoint"
        connectButton.isEnabled = supported.isNotEmpty()
    }

    private fun connect() {
        val gatewayPosition = gatewaySpinner.selectedItemPosition
        val proxyPosition = proxySpinner.selectedItemPosition
        if (gatewayPosition !in gateways.indices || proxyPosition !in proxies.indices) {
            setStatus("Hãy chọn proxy trước khi kết nối.", true)
            return
        }
        val gateway = gateways[gatewayPosition]
        val proxy = proxies[proxyPosition]
        val protocol = protocolSpinner.selectedItem?.toString()?.lowercase() ?: proxy.protocol.lowercase()
        val routingMode = prepareRouting() ?: return
        busy(true, "Đang xin thông tin kết nối…")
        lifecycleScope.launch {
            runCatching {
                val entitlement = withContext(Dispatchers.IO) { api.entitlement() }
                if (!entitlement.active) error("Tài khoản không có quyền sử dụng đang hiệu lực (${entitlement.status}).")
                val connection = withContext(Dispatchers.IO) { api.connection(gateway.id, proxy.id) }
                if (connection.protocols.isNotEmpty() && protocol !in connection.protocols.map { it.lowercase() }) {
                    error("Proxy không còn hỗ trợ giao thức ${protocol.uppercase()}.")
                }
                val config = buildConfig(
                    connection,
                    protocol,
                    routingMode,
                    dnsThroughProxyBox.isChecked,
                    preventDnsLeaksBox.isChecked,
                    upstreamTls = false,
                )
                installProfile(proxy.display, config)
                pendingConfig = config
            }.onSuccess {
                requestNotificationThenVpn()
            }.onFailure {
                setStatus(it.message ?: "Không thể chuẩn bị kết nối.", true)
            }
            busy(false)
        }
    }

    private fun prepareRouting(): Int? {
        val routingMode = routingSpinner.selectedItemPosition.coerceIn(0, 2)
        if (routingMode == 2 && Settings.perAppProxyList.isEmpty()) {
            setStatus("Hãy chọn ít nhất một ứng dụng cho chế độ này.", true)
            return null
        }
        Settings.perAppProxyEnabled = routingMode == 2
        Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
        return routingMode
    }

    private fun manualConnection(): ConnectionInfo {
        val host = manualHostInput.text.toString().trim()
        val port = manualPortInput.text.toString().toIntOrNull() ?: 0
        if (host.isBlank() || port !in 1..65535) error("Hãy nhập host/IP và port proxy hợp lệ.")
        return ConnectionInfo(
            host = host,
            port = port,
            username = manualUsernameInput.text.toString(),
            password = manualPasswordInput.text.toString(),
            protocol = manualProtocolSpinner.selectedItem.toString().lowercase(),
            protocols = listOf(manualProtocolSpinner.selectedItem.toString().lowercase()),
            expiresAt = null,
        )
    }

    private fun checkManualProxy() {
        val connection = runCatching { manualConnection() }.getOrElse {
            setStatus(it.message ?: "Thông tin proxy không hợp lệ.", true)
            return
        }
        busy(true, "Đang kiểm tra cổng proxy…")
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    Socket().use { it.connect(InetSocketAddress(connection.host, connection.port), 8_000) }
                }
            }.onSuccess {
                setStatus("Đã kết nối được tới cổng proxy. Username/password sẽ được kiểm tra khi bật VPN.")
            }.onFailure {
                setStatus("Không kết nối được tới proxy: ${it.message ?: "hết thời gian chờ"}", true)
            }
            busy(false)
        }
    }

    private fun connectManual() {
        val connection = runCatching { manualConnection() }.getOrElse {
            setStatus(it.message ?: "Thông tin proxy không hợp lệ.", true)
            return
        }
        val routingMode = prepareRouting() ?: return
        busy(true, "Đang chuẩn bị proxy riêng…")
        lifecycleScope.launch {
            runCatching {
                val config = buildConfig(
                    connection,
                    connection.protocol,
                    routingMode,
                    dnsThroughProxyBox.isChecked,
                    preventDnsLeaksBox.isChecked,
                    upstreamTls = connection.protocol == "https",
                    tlsServerName = manualSniInput.text.toString().trim(),
                )
                installProfile("Proxy riêng · ${connection.protocol.uppercase()}", config)
                pendingConfig = config
            }.onSuccess {
                manualPasswordInput.text.clear()
                requestNotificationThenVpn()
            }
                .onFailure { setStatus(it.message ?: "Không thể chuẩn bị proxy riêng.", true) }
            busy(false)
        }
    }

    private suspend fun installProfile(name: String, config: String) = withContext(Dispatchers.IO) {
        val file = File(filesDir, "vproxies-managed.json")
        file.writeText(config)
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        credentialFile = file
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val oldId = prefs.getLong(PROFILE_ID, -1L)
        val old = if (oldId > 0) ProfileManager.get(oldId) else null
        val profile = if (old == null) {
            Profile(
                userOrder = ProfileManager.nextOrder(),
                name = "VProxies · $name",
                typed = TypedProfile().apply { path = file.absolutePath; type = TypedProfile.Type.Local },
            ).let { ProfileManager.create(it, andSelect = true) }
        } else {
            old.name = "VProxies · $name"
            old.typed.path = file.absolutePath
            old.typed.type = TypedProfile.Type.Local
            ProfileManager.update(old)
            Settings.selectedProfile = old.id
            old
        }
        prefs.edit().putLong(PROFILE_ID, profile.id).apply()
        Settings.serviceMode = ServiceMode.VPN
    }

    private fun requestNotificationThenVpn() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpnPermission()
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent == null) startCore() else vpnPermission.launch(intent)
    }

    private fun startCore() {
        if (pendingConfig == null) return
        BoxService.start()
        pendingConfig = null
        setStatus("Đang khởi động VPN…")
    }

    private fun stopCore() {
        BoxService.stop()
        pendingConfig = null
        credentialFile?.delete()
        credentialFile = null
        setStatus("Đã yêu cầu ngắt kết nối.")
    }

    private fun verifyTunnelInternet() {
        lifecycleScope.launch {
            delay(1_500)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val test = URL("https://www.cloudflare.com/cdn-cgi/trace").openConnection() as HttpURLConnection
                    try {
                        test.instanceFollowRedirects = true
                        test.connectTimeout = 10_000
                        test.readTimeout = 10_000
                        test.setRequestProperty("Cache-Control", "no-cache")
                        test.responseCode
                    } finally {
                        test.disconnect()
                    }
                }
            }
            if (coreStatus != Status.Started) return@launch
            result.onSuccess { status ->
                if (status in 200..399) setStatus("VPN và Internet đang hoạt động.")
                else setStatus("VPN đã bật nhưng kiểm tra Internet trả HTTP $status.", true)
            }.onFailure { error ->
                val reason = if (error is UnknownHostException) {
                    "DNS trong VPN chưa phân giải được tên miền."
                } else {
                    error.message ?: "không nhận được phản hồi"
                }
                setStatus("VPN đã bật nhưng chưa truy cập được Internet: $reason", true)
            }
        }
    }

    private fun buildConfig(
        connection: ConnectionInfo,
        protocol: String,
        routingMode: Int,
        dnsThroughProxy: Boolean,
        preventDnsLeaks: Boolean,
        upstreamTls: Boolean,
        tlsServerName: String = "",
    ): String {
        val proxy = JSONObject().put("tag", "proxy")
            .put("server", connection.host)
            .put("server_port", connection.port)
        when (protocol) {
            "socks4" -> proxy.put("type", "socks").put("version", "4")
            "socks5" -> proxy.put("type", "socks").put("version", "5")
            // The API's `https` label currently means HTTP CONNECT. It does not
            // declare TLS transport to the upstream proxy, so never infer TLS here.
            "https" -> {
                proxy.put("type", "http")
                if (upstreamTls) {
                    proxy.put(
                        "tls",
                        JSONObject().put("enabled", true)
                            .put("server_name", tlsServerName.ifBlank { connection.host }),
                    )
                }
            }
            else -> proxy.put("type", "http")
        }
        if (connection.username.isNotBlank()) proxy.put("username", connection.username)
        if (connection.password.isNotBlank() && protocol != "socks4") proxy.put("password", connection.password)
        if (!connection.host.matches(Regex("^[0-9a-fA-F:.]+$"))) proxy.put("domain_resolver", "dns-direct")

        val direct = JSONObject().put("type", "direct").put("tag", "direct")
        val block = JSONObject().put("type", "block").put("tag", "block")
        // Do not use Android's local resolver from inside the TUN. Once the VPN owns
        // the default route that resolver can call back into the TUN and leave Chrome
        // at DNS_PROBE_STARTED forever. An IP-literal DoH endpoint needs no bootstrap
        // lookup and its traffic is explicitly routed outside the proxy below.
        val dnsServers = JSONArray().put(
            JSONObject().put("type", "https").put("tag", "dns-direct")
                .put("server", "1.1.1.1").put("server_port", 443).put("path", "/dns-query")
                .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com")),
        )
        if (dnsThroughProxy) {
            dnsServers.put(
                JSONObject().put("type", "https").put("tag", "dns-proxy")
                    .put("server", "1.1.1.1").put("server_port", 443).put("path", "/dns-query")
                    .put("tls", JSONObject().put("enabled", true).put("server_name", "cloudflare-dns.com"))
                    .put("detour", "proxy"),
            )
        }
        val routeRules = JSONArray()
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
            // Android Private DNS uses encrypted DNS-over-TLS on TCP/853. Many
            // HTTP/SOCKS proxies block that port, so preserve the user's system
            // resolver by routing DoT directly instead of trapping it in the proxy.
            .put(
                JSONObject().put("network", "tcp").put("port", 853)
                    .put("action", "route").put("outbound", "direct"),
            )
            .put(
                JSONObject().put("ip_cidr", JSONArray().put("1.1.1.1/32"))
                    .put("action", "route").put("outbound", "direct"),
            )
            .put(JSONObject().put("ip_is_private", true).put("action", "route").put("outbound", "direct"))
        if (routingMode == 1) {
            routeRules.put(
                JSONObject().put("network", "tcp").put("port", JSONArray().put(80).put(443))
                    .put("action", "route").put("outbound", "proxy"),
            )
        }
        val finalOutbound = if (routingMode == 1) "direct" else "proxy"
        return JSONObject()
            .put("log", JSONObject().put("level", "info").put("timestamp", true))
            .put(
                "dns",
                JSONObject()
                    .put("servers", dnsServers)
                    .put("final", if (dnsThroughProxy) "dns-proxy" else "dns-direct"),
            )
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject().put("type", "tun").put("tag", "tun-in")
                        .put("address", JSONArray().put("172.19.0.1/30"))
                        .put("mtu", 1500).put("auto_route", true)
                        .put("strict_route", preventDnsLeaks).put("stack", "mixed"),
                ),
            )
            .put("outbounds", JSONArray().put(proxy).put(direct).put(block))
            .put(
                "route",
                JSONObject().put("rules", routeRules).put("final", finalOutbound)
                    .put("auto_detect_interface", true).put("default_domain_resolver", "dns-direct"),
            ).toString(2)
    }

    private fun busy(value: Boolean, message: String? = null) {
        loginButton.isEnabled = !value
        connectButton.isEnabled = if (value) false else {
            val selected = proxies.getOrNull(proxySpinner.selectedItemPosition)
            selected != null && selected.protocols.ifEmpty { listOf(selected.protocol) }
                .any { it.lowercase() in SUPPORTED_PROTOCOLS }
        }
        if (message != null) setStatus(message)
    }

    private fun setStatus(message: String, error: Boolean = false) {
        statusLabel.text = message
        statusLabel.setTextColor(if (error) Color.rgb(255, 128, 142) else Color.rgb(126, 231, 166))
    }

    private fun adapter(values: List<String>) =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)

    private fun section(value: String) = text(value, 18f, Color.WHITE, Typeface.BOLD).apply { setPadding(0, 0, 0, dp(8)) }
    private fun label(value: String) = text(value, 12f, Color.rgb(150, 164, 191)).apply { setPadding(0, dp(10), 0, dp(4)) }
    private fun space(height: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun text(value: String, size: Float, color: Int, style: Int = Typeface.NORMAL) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setTypeface(typeface, style)
    }
    private fun input(hintValue: String, secret: Boolean) = EditText(this).apply {
        hint = hintValue
        setHintTextColor(Color.rgb(120, 133, 160))
        setTextColor(Color.WHITE)
        setSingleLine(true)
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }
    private fun spinner() = Spinner(this).apply { setBackgroundColor(Color.rgb(25, 37, 65)) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class Gateway(val id: String, val name: String, val region: String) {
    val display: String get() = if (region.isBlank()) name else "$name · $region"
}

private data class ProxyItem(
    val id: Long,
    val name: String,
    val gatewayId: String,
    val protocol: String,
    val protocols: List<String>,
    val status: String,
    val country: String,
    val city: String,
    val latency: Long?,
    val showHostPort: Boolean,
    val host: String,
    val port: Int,
) {
    val location: String get() = listOf(city, country).filter(String::isNotBlank).joinToString(", ").ifBlank { "Chưa xác định" }
    val display: String get() = "${name.ifBlank { "Proxy #$id" }} · $location"
}

private data class LoginInfo(
    val userName: String,
    val active: Boolean,
    val status: String,
    val packageName: String,
    val remainingDays: Long,
)

private data class ConnectionInfo(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val protocol: String,
    val protocols: List<String>,
    val expiresAt: Long?,
)

private data class EntitlementInfo(
    val active: Boolean,
    val status: String,
    val packageName: String,
    val remainingDays: Long,
)

private class ApiClient(private val connectivity: ConnectivityManager) {
    private var token = ""
    val signedIn: Boolean get() = token.isNotBlank()

    fun login(identity: String, password: String): LoginInfo {
        val root = request(
            "auth/login",
            "POST",
            JSONObject().put("login", identity).put("password", password)
                .put("platform", "android").put("client_name", CLIENT_NAME),
            authorize = false,
        )
        val loginData = root.optJSONObject("data") ?: root
        token = root.string("access_token", "token").ifBlank { loginData.string("access_token", "token") }
        if (token.isBlank()) error("Phản hồi đăng nhập không có access token.")
        val user = root.optJSONObject("user") ?: loginData.optJSONObject("user")
        val entitlement = entitlement()
        return LoginInfo(
            userName = user?.string("username", "name", "email").orEmpty().ifBlank { identity },
            active = entitlement.active,
            status = entitlement.status,
            packageName = entitlement.packageName,
            remainingDays = entitlement.remainingDays,
        )
    }

    fun entitlement(): EntitlementInfo {
        val root = request("entitlement")
        val data = root.optJSONObject("data") ?: root.optJSONObject("entitlement") ?: root
        return EntitlementInfo(
            active = data.bool("active"),
            status = data.string("status"),
            packageName = data.string("package_name", "plan_name", "package"),
            remainingDays = data.long("remaining_days"),
        )
    }

    fun gateways(): List<Gateway> {
        val root = request("gateways")
        val array = root.optJSONArray("gateways")
            ?: root.optJSONObject("data")?.optJSONArray("gateways") ?: JSONArray()
        return array.objects().map {
            Gateway(it.scalar("id"), it.string("name").ifBlank { "Gateway" }, it.string("region"))
        }.filter { it.id.isNotBlank() }
    }

    fun proxies(gatewayId: String): List<ProxyItem> {
        val response = request("proxies?gateway_id=${java.net.URLEncoder.encode(gatewayId, "UTF-8")}")
        val root = response.optJSONObject("data") ?: response
        val deliveryVisible = root.optJSONObject("delivery")?.bool("show_host_port") == true
        return (root.optJSONArray("proxies") ?: JSONArray()).objects().map { item ->
            val visible = item.optJSONObject("visibility")?.bool("show_host_port") ?: deliveryVisible
            ProxyItem(
                id = item.long("id"), gatewayId = item.scalar("gateway_id").ifBlank { gatewayId },
                name = item.string("name"), protocol = item.string("protocol"), protocols = item.strings("protocols"),
                status = item.string("status"), country = item.string("country"), city = item.string("city"),
                latency = if (item.has("latency_ms") && !item.isNull("latency_ms")) item.long("latency_ms") else null,
                showHostPort = visible, host = if (visible) item.string("host") else "",
                port = if (visible) item.long("port").toInt() else 0,
            )
        }.filter { it.id > 0 }
    }

    fun connection(gatewayId: String, proxyId: Long): ConnectionInfo {
        val response = request(
            "connections",
            "POST",
            JSONObject().put("gateway_id", gatewayId).put("proxy_id", proxyId),
        )
        val root = response.optJSONObject("data") ?: response
        val envelope = root.optJSONObject("connection") ?: error("Phản hồi thiếu connection envelope.")
        if (!envelope.string("mode").equals("direct", true)) error("API không trả về chế độ kết nối direct.")
        val returnedGateway = envelope.scalar("gateway_id").ifBlank { root.scalar("gateway_id") }
        if (returnedGateway.isNotBlank() && returnedGateway != gatewayId) error("API trả về sai gateway_id.")
        val returnedProxy = envelope.long("proxy_id")
        if (returnedProxy > 0 && returnedProxy != proxyId) error("API trả về sai proxy_id.")
        val source = envelope.optJSONObject("connection") ?: error("Phản hồi thiếu thông tin proxy nguồn.")
        val sourceProtocols = source.strings("protocols").map(String::lowercase).distinct()
        return ConnectionInfo(
            host = source.string("host"), port = source.long("port").toInt(),
            username = source.string("username"), password = source.string("password"),
            protocol = source.string("protocol").lowercase(), protocols = sourceProtocols,
            expiresAt = envelope.opt("expires_at").let {
                when (it) { is Number -> it.toLong(); is String -> it.toLongOrNull(); else -> null }
            },
        ).also {
            if (it.host.isBlank() || it.port !in 1..65535) error("Proxy nguồn không có host/port hợp lệ.")
            if (it.expiresAt != null && it.expiresAt <= System.currentTimeMillis() / 1000L) {
                error("Cấu hình proxy đã hết hạn; hãy yêu cầu lại.")
            }
        }
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, authorize: Boolean = true): JSONObject {
        if (authorize && token.isBlank()) error("Bạn cần đăng nhập trước.")
        val url = URL("$API_BASE_URL${path.trimStart('/')}")
        val physicalNetwork = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        }
        // Account/config API traffic must not depend on a currently active proxy tunnel.
        // Binding it to Wi-Fi/cellular also avoids the DNS loop shown when reconnecting.
        val connection = (physicalNetwork?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            if (authorize) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val errorRoot = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                val code = errorRoot.string("code", "error")
                val message = errorRoot.string("message")
                val friendly = when {
                    status == 401 -> "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."
                    status == 403 && code == "entitlement_required" -> "Tài khoản không có quyền sử dụng đang hiệu lực."
                    status == 422 && code in setOf("invalid_gateway", "gateway_selection_required") -> "Máy chủ proxy không hợp lệ; hãy tải lại danh sách máy chủ."
                    status == 422 && code == "invalid_proxy" -> "Proxy được chọn không hợp lệ."
                    status == 502 && code == "proxy_server_unavailable" -> "Máy chủ lưu proxy đang tạm thời không khả dụng."
                    status == 503 && code == "gateway_registry_unavailable" -> "Danh sách máy chủ chưa sẵn sàng; hãy thử lại sau."
                    else -> message.ifBlank { code.ifBlank { "Yêu cầu API thất bại." } }
                }
                error("API $status: $friendly")
            }
            return JSONObject(text)
        } catch (_: UnknownHostException) {
            error("Không phân giải được api.vproxies.app. Hãy kiểm tra Wi-Fi/4G hoặc DNS riêng trên điện thoại rồi thử lại.")
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.string(vararg names: String): String = names.firstNotNullOfOrNull { name ->
    if (has(name) && !isNull(name)) optString(name, "").takeIf { it.isNotBlank() } else null
}.orEmpty()
private fun JSONObject.scalar(name: String): String = if (!has(name) || isNull(name)) "" else opt(name).toString()
private fun JSONObject.long(name: String): Long = when (val value = opt(name)) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull() ?: 0
    else -> 0
}
private fun JSONObject.bool(name: String): Boolean = when (val value = opt(name)) {
    is Boolean -> value
    is String -> value.equals("true", true) || value == "1"
    is Number -> value.toInt() != 0
    else -> false
}
private fun JSONObject.strings(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}
