package io.nekohasekai.sfa.vproxies

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.nekohasekai.sfa.bg.BoxService
import io.nekohasekai.sfa.compose.MainActivity
import io.nekohasekai.sfa.constant.ServiceMode
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * VProxies clean UI layered on the official Android libbox/VpnService implementation.
 * Account passwords and source proxy credentials are intentionally never persisted.
 */
class VProxiesActivity : AppCompatActivity() {
    companion object {
        private const val API_BASE = "https://api.vproxies.app/api/v1/"
        private const val CLIENT_NAME = "VProxies Android 0.1.0"
        private const val PREFS = "vproxies"
        private const val PROFILE_ID = "managed_profile_id"
    }

    private val api = ApiClient()
    private val gateways = mutableListOf<Gateway>()
    private val proxies = mutableListOf<ProxyItem>()
    private var pendingConfig: String? = null

    private lateinit var identityInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var gatewaySpinner: Spinner
    private lateinit var proxySpinner: Spinner
    private lateinit var protocolSpinner: Spinner
    private lateinit var connectButton: Button
    private lateinit var stopButton: Button
    private lateinit var accountLabel: TextView
    private lateinit var proxyDetailLabel: TextView
    private lateinit var statusLabel: TextView

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) startCore() else setStatus("Bạn chưa cấp quyền VPN.", true)
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpnPermission() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "VProxies"
        buildInterface()
        identityInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("identity", ""))
    }

    private fun buildInterface() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(8, 14, 30)) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(36))
        }
        scroll.addView(page)

        page.addView(text("VPROXIES", 30f, Color.rgb(78, 220, 255), Typeface.BOLD))
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
                    connectButton.isEnabled = items.isNotEmpty()
                    if (items.isEmpty()) setStatus("Máy chủ này chưa có proxy khả dụng.", true)
                    else setStatus("Đã đồng bộ ${items.size} proxy.")
                }
                .onFailure { setStatus(it.message ?: "Không tải được proxy.", true) }
        }
    }

    private fun updateProxySelection(position: Int) {
        if (position !in proxies.indices) return
        val proxy = proxies[position]
        val supported = proxy.protocols.ifEmpty { listOf(proxy.protocol) }.filter { it.isNotBlank() }
        protocolSpinner.adapter = adapter(supported.map { it.uppercase() })
        val endpoint = if (proxy.showHostPort && proxy.host.isNotBlank()) "${proxy.host}:${proxy.port}" else "Đã ẩn IP/port"
        proxyDetailLabel.text = "${proxy.city}, ${proxy.country} · ${proxy.status} · ${proxy.latency ?: "—"} ms\n$endpoint"
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
        busy(true, "Đang xin thông tin kết nối…")
        lifecycleScope.launch {
            runCatching {
                val connection = withContext(Dispatchers.IO) { api.connection(gateway.id, proxy.id) }
                if (connection.protocols.isNotEmpty() && protocol !in connection.protocols.map { it.lowercase() }) {
                    error("Proxy không còn hỗ trợ giao thức ${protocol.uppercase()}.")
                }
                val config = buildConfig(connection, protocol)
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

    private suspend fun installProfile(name: String, config: String) = withContext(Dispatchers.IO) {
        val file = File(filesDir, "vproxies-managed.json")
        file.writeText(config)
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
        setStatus("Đã yêu cầu kết nối. Kiểm tra biểu tượng VPN trên thanh trạng thái.")
    }

    private fun stopCore() {
        BoxService.stop()
        pendingConfig = null
        setStatus("Đã yêu cầu ngắt kết nối.")
    }

    private fun buildConfig(connection: ConnectionInfo, protocol: String): String {
        val proxy = JSONObject().put("tag", "proxy")
            .put("server", connection.host)
            .put("server_port", connection.port)
        when (protocol) {
            "socks4", "socks4a" -> proxy.put("type", "socks").put("version", "4a")
            "socks5" -> proxy.put("type", "socks").put("version", "5")
            "https" -> proxy.put("type", "http").put(
                "tls", JSONObject().put("enabled", true).put("server_name", connection.host),
            )
            else -> proxy.put("type", "http")
        }
        if (connection.username.isNotBlank()) proxy.put("username", connection.username)
        if (connection.password.isNotBlank() && protocol !in listOf("socks4", "socks4a")) proxy.put("password", connection.password)
        if (!connection.host.matches(Regex("^[0-9a-fA-F:.]+$"))) proxy.put("domain_resolver", "dns-local")

        val direct = JSONObject().put("type", "direct").put("tag", "direct")
        val block = JSONObject().put("type", "block").put("tag", "block")
        val privateRule = JSONObject().put("ip_is_private", true).put("action", "route").put("outbound", "direct")
        return JSONObject()
            .put("log", JSONObject().put("level", "info").put("timestamp", true))
            .put(
                "dns",
                JSONObject()
                    .put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "dns-local")))
                    .put("final", "dns-local"),
            )
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject().put("type", "tun").put("tag", "tun-in")
                        .put("address", JSONArray().put("172.19.0.1/30"))
                        .put("mtu", 1500).put("auto_route", true).put("stack", "mixed"),
                ),
            )
            .put("outbounds", JSONArray().put(proxy).put(direct).put(block))
            .put(
                "route",
                JSONObject().put("rules", JSONArray().put(privateRule)).put("final", "proxy")
                    .put("auto_detect_interface", true).put("default_domain_resolver", "dns-local"),
            ).toString(2)
    }

    private fun busy(value: Boolean, message: String? = null) {
        loginButton.isEnabled = !value
        connectButton.isEnabled = !value && proxies.isNotEmpty()
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
    val display: String get() = "${name.ifBlank { "Proxy #$id" }} · ${city.ifBlank { country }}"
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
)

private class ApiClient {
    private var token = ""
    val signedIn: Boolean get() = token.isNotBlank()

    fun login(identity: String, password: String): LoginInfo {
        val root = request(
            "auth/login",
            "POST",
            JSONObject().put("login", identity).put("password", password)
                .put("platform", "android").put("client_name", "VProxies Android 0.1.0"),
            authorize = false,
        )
        token = root.string("access_token", "token")
        if (token.isBlank()) error("Phản hồi đăng nhập không có access token.")
        val user = root.optJSONObject("user")
        val entitlement = root.optJSONObject("entitlement") ?: JSONObject()
        return LoginInfo(
            userName = user?.string("username", "name", "email").orEmpty().ifBlank { identity },
            active = entitlement.bool("active"),
            status = entitlement.string("status"),
            packageName = entitlement.string("package_name"),
            remainingDays = entitlement.long("remaining_days"),
        )
    }

    fun gateways(): List<Gateway> {
        val array = request("gateways").optJSONArray("gateways") ?: JSONArray()
        return array.objects().map {
            Gateway(it.scalar("id"), it.string("name").ifBlank { "Gateway" }, it.string("region"))
        }.filter { it.id.isNotBlank() }
    }

    fun proxies(gatewayId: String): List<ProxyItem> {
        val root = request("proxies?gateway_id=${java.net.URLEncoder.encode(gatewayId, "UTF-8")}")
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
        val root = request(
            "connections",
            "POST",
            JSONObject().put("gateway_id", gatewayId).put("proxy_id", proxyId),
        )
        val envelope = root.optJSONObject("connection") ?: error("Phản hồi thiếu connection envelope.")
        val source = envelope.optJSONObject("connection") ?: error("Phản hồi thiếu thông tin proxy nguồn.")
        return ConnectionInfo(
            host = source.string("host"), port = source.long("port").toInt(),
            username = source.string("username"), password = source.string("password"),
            protocol = source.string("protocol"), protocols = source.strings("protocols"),
        ).also { if (it.host.isBlank() || it.port !in 1..65535) error("Proxy nguồn không có host/port hợp lệ.") }
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, authorize: Boolean = true): JSONObject {
        if (authorize && token.isBlank()) error("Bạn cần đăng nhập trước.")
        val connection = URL("https://api.vproxies.app/api/v1/$path").openConnection() as HttpURLConnection
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
                val message = runCatching { JSONObject(text).string("message", "error") }.getOrDefault("")
                error("API $status: ${message.ifBlank { text.take(180) }}")
            }
            return JSONObject(text)
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
