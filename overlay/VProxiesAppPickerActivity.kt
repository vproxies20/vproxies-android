package io.nekohasekai.sfa.vproxies

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.nekohasekai.sfa.database.Settings

class VProxiesAppPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Chọn ứng dụng"
        val selected = Settings.perAppProxyList.toMutableSet()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
            setBackgroundColor(Color.rgb(7, 16, 29))
        }
        page.addView(TextView(this).apply {
            text = "ỨNG DỤNG DÙNG PROXY"
            textSize = 20f
            setTextColor(Color.rgb(25, 216, 232))
        })
        page.addView(TextView(this).apply {
            text = "Chỉ các ứng dụng được đánh dấu mới đi qua VPN VProxies."
            setTextColor(Color.rgb(145, 162, 185))
            setPadding(0, dp(6), 0, dp(14))
        })

        val applications = packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != packageName }
            .map { it.packageName to packageManager.getApplicationLabel(it).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .toList()
        for ((packageName, label) in applications) {
            page.addView(CheckBox(this).apply {
                text = "$label\n$packageName"
                setTextColor(Color.WHITE)
                isChecked = packageName in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(packageName) else selected.remove(packageName)
                }
            })
        }
        page.addView(Button(this).apply {
            text = "Lưu ${selected.size} ứng dụng"
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener {
                Settings.perAppProxyList = selected
                Settings.perAppProxyEnabled = true
                Settings.perAppProxyMode = Settings.PER_APP_PROXY_INCLUDE
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
