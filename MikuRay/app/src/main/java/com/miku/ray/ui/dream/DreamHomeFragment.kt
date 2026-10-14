package com.miku.ray.ui.dream

import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.main.MainViewModel
import com.miku.ray.util.Utils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 梦盒风格首页：侧栏 + 光球 + 流量/节点/问候 + 连接按钮 */
class DreamHomeFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()

    private var tvMm: TextView? = null
    private var tvDd: TextView? = null
    private var batteryFill: View? = null
    private var tvBattery: TextView? = null
    private var tvUpValue: TextView? = null
    private var tvUpUnit: TextView? = null
    private var tvDownValue: TextView? = null
    private var tvDownUnit: TextView? = null
    private var tvFlag: TextView? = null
    private var tvNodeName: TextView? = null
    private var tvDelay: TextView? = null
    private var tvGreeting: TextView? = null
    private var btnConnect: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_dream_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvMm = view.findViewById(R.id.dream_home_date_mm)
        tvDd = view.findViewById(R.id.dream_home_date_dd)
        batteryFill = view.findViewById(R.id.dream_home_battery_fill)
        tvBattery = view.findViewById(R.id.dream_home_battery_text)
        tvUpValue = view.findViewById(R.id.dream_home_up_value)
        tvUpUnit = view.findViewById(R.id.dream_home_up_unit)
        tvDownValue = view.findViewById(R.id.dream_home_down_value)
        tvDownUnit = view.findViewById(R.id.dream_home_down_unit)
        tvFlag = view.findViewById(R.id.dream_home_flag)
        tvNodeName = view.findViewById(R.id.dream_home_node_name)
        tvDelay = view.findViewById(R.id.dream_home_delay)
        tvGreeting = view.findViewById(R.id.dream_home_greeting)
        btnConnect = view.findViewById(R.id.dream_home_connect_btn)

        refreshDate()
        refreshBattery()
        refreshGreeting()
        refreshNodeInfo()

        view.findViewById<ImageButton>(R.id.dream_home_shortcut_proxy)
            .setOnClickListener { (activity as? DreamMainActivity)?.switchTab(1) }
        view.findViewById<ImageButton>(R.id.dream_home_shortcut_config)
            .setOnClickListener { (activity as? DreamMainActivity)?.switchTab(2) }
        view.findViewById<ImageButton>(R.id.dream_home_shortcut_settings)
            .setOnClickListener { (activity as? DreamMainActivity)?.switchTab(3) }

        btnConnect?.setOnClickListener { mainViewModel.onFabClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainViewModel.isRunning.collect { running ->
                        updateConnectButton(running)
                        refreshNodeInfo()
                    }
                }
                launch {
                    mainViewModel.trafficSpeedText.collect { speedText ->
                        updateTraffic(speedText)
                    }
                }
                launch {
                    mainViewModel.updateListItemEvent.collect {
                        refreshNodeInfo()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBattery()
        refreshNodeInfo()
        updateConnectButton(mainViewModel.isRunning.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tvMm = null
        tvDd = null
        batteryFill = null
        tvBattery = null
        tvUpValue = null
        tvUpUnit = null
        tvDownValue = null
        tvDownUnit = null
        tvFlag = null
        tvNodeName = null
        tvDelay = null
        tvGreeting = null
        btnConnect = null
    }

    private fun refreshDate() {
        val now = Calendar.getInstance().time
        tvMm?.text = SimpleDateFormat("MM", Locale.getDefault()).format(now)
        tvDd?.text = SimpleDateFormat("dd", Locale.getDefault()).format(now)
    }

    private fun refreshBattery() {
        val bm = requireContext().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        tvBattery?.text = "$pct%"
        val fill = batteryFill ?: return
        fill.post {
            val trackWidth = (fill.parent as View).width
            if (trackWidth > 0) {
                val lp = fill.layoutParams
                lp.width = (trackWidth * pct / 100f).toInt().coerceAtLeast(0)
                fill.layoutParams = lp
            }
        }
    }

    private fun refreshGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val resId = when (hour) {
            in 5..11 -> R.string.dream_greeting_morning
            in 12..17 -> R.string.dream_greeting_afternoon
            in 18..23 -> R.string.dream_greeting_evening
            else -> R.string.dream_greeting_night
        }
        tvGreeting?.text = getString(resId)
    }

    private fun updateTraffic(speedText: String) {
        // 形如 "↑ 2.2 KB  ↓ 25 KB"
        val parts = speedText.split("↓")
        val upRaw = parts.getOrNull(0)?.replace("↑", "")?.trim().orEmpty()
        val downRaw = parts.getOrNull(1)?.trim().orEmpty()
        val (upValue, upUnit) = splitValueUnit(upRaw)
        val (downValue, downUnit) = splitValueUnit(downRaw)
        tvUpValue?.text = upValue.ifEmpty { "--" }
        tvUpUnit?.text = upUnit
        tvDownValue?.text = downValue.ifEmpty { "--" }
        tvDownUnit?.text = downUnit
    }

    private fun splitValueUnit(raw: String): Pair<String, String> {
        if (raw.isBlank()) return "" to ""
        val idx = raw.lastIndexOf(' ')
        return if (idx > 0) {
            raw.substring(0, idx) to raw.substring(idx + 1)
        } else {
            raw to ""
        }
    }

    private fun refreshNodeInfo() {
        val context = context ?: return
        val guid = MmkvManager.getSelectServer()
        if (guid.isNullOrEmpty()) {
            tvFlag?.text = ""
            tvNodeName?.text = getString(R.string.dream_no_server_selected)
            tvDelay?.text = ""
            return
        }
        val profile = MmkvManager.decodeServerConfig(guid)
        tvNodeName?.text = profile?.remarks?.ifBlank { getString(R.string.dream_no_server_selected) }
            ?: getString(R.string.dream_no_server_selected)

        val aff = MmkvManager.decodeServerAffiliationInfo(guid)
        val code = aff?.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
        tvFlag?.text = Utils.countryCodeToFlag(code)

        val delayMs = aff?.testDelayMillis ?: 0L
        when {
            delayMs > 0L -> {
                tvDelay?.text = "${delayMs}ms"
                val colorRes = if (delayMs >= 500L) R.color.dream_orange else R.color.dream_green
                tvDelay?.setTextColor(ContextCompat.getColor(context, colorRes))
            }
            delayMs < 0L -> {
                tvDelay?.text = getString(R.string.connection_test_fail)
                tvDelay?.setTextColor(ContextCompat.getColor(context, R.color.dream_orange))
            }
            else -> {
                tvDelay?.text = ""
            }
        }
    }

    private fun updateConnectButton(running: Boolean) {
        val context = context ?: return
        if (running) {
            btnConnect?.text = getString(R.string.dream_stop)
            btnConnect?.background = ContextCompat.getDrawable(context, R.drawable.dream_pill_gray)
        } else {
            btnConnect?.text = getString(R.string.dream_connect)
            btnConnect?.background = ContextCompat.getDrawable(context, R.drawable.dream_pill_teal)
        }
    }
}
