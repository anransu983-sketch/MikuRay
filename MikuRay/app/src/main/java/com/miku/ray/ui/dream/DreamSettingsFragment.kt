package com.miku.ray.ui.dream

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.miku.ray.R
import com.miku.ray.ui.about.AboutActivity
import com.miku.ray.ui.backup.BackupActivity
import com.miku.ray.ui.logcat.LogcatActivity
import com.miku.ray.ui.preference.activity.AdvancedSettingsActivity
import com.miku.ray.ui.preference.activity.CoreSettingsActivity
import com.miku.ray.ui.preference.activity.UiSettingsActivity
import com.miku.ray.ui.preference.activity.VpnSettingsActivity
import com.miku.ray.ui.routing.RoutingSettingActivity

/** 梦盒风格设置页：分组白卡 + 36dp teal 圆图标行 */
class DreamSettingsFragment : Fragment() {

    private data class SettingEntry(
        val iconRes: Int,
        val titleRes: Int,
        val subtitleRes: Int,
        val target: Class<*>,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_dream_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<LinearLayout>(R.id.dream_settings_container)
        val inflater = LayoutInflater.from(requireContext())

        addGroup(
            container, inflater,
            R.string.dream_settings_group_ui,
            listOf(
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_design_palette_line,
                    R.string.dream_set_app, R.string.dream_set_app_sub, UiSettingsActivity::class.java
                ),
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_device_rss_line,
                    R.string.dream_set_network, R.string.dream_set_network_sub, VpnSettingsActivity::class.java
                ),
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_design_brush_line,
                    R.string.dream_set_override, R.string.dream_set_override_sub, AdvancedSettingsActivity::class.java
                ),
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_system_function_line,
                    R.string.dream_set_meta, R.string.dream_set_meta_sub, CoreSettingsActivity::class.java
                ),
            )
        )

        addGroup(
            container, inflater,
            R.string.dream_settings_group_more,
            listOf(
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_map_route_line,
                    R.string.dream_set_routing, R.string.dream_set_routing_sub, RoutingSettingActivity::class.java
                ),
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_development_terminal_line,
                    R.string.dream_set_logcat, R.string.dream_set_logcat_sub, LogcatActivity::class.java
                ),
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_system_download_line,
                    R.string.dream_set_backup, R.string.dream_set_backup_sub, BackupActivity::class.java
                ),
                SettingEntry(
                    com.miku.ray.remixicon.R.drawable.rmx_system_information_line,
                    R.string.dream_set_about, R.string.dream_set_about_sub, AboutActivity::class.java
                ),
            )
        )
    }

    private fun addGroup(
        container: LinearLayout,
        inflater: LayoutInflater,
        groupTitleRes: Int,
        entries: List<SettingEntry>,
    ) {
        val context = requireContext()

        val label = TextView(context).apply {
            text = getString(groupTitleRes)
            setTextColor(ContextCompat.getColor(context, R.color.dream_teal))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(20)
            lp.bottomMargin = dp(8)
            lp.marginStart = dp(4)
            layoutParams = lp
        }
        container.addView(label)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.dream_card_bg)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }

        entries.forEach { entry ->
            val row = inflater.inflate(R.layout.item_dream_setting_row, card, false)
            val icon = row.findViewById<ImageView>(R.id.dream_setting_icon)
            icon.setImageResource(entry.iconRes)
            icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            row.findViewById<TextView>(R.id.dream_setting_title).text = getString(entry.titleRes)
            row.findViewById<TextView>(R.id.dream_setting_subtitle).text = getString(entry.subtitleRes)
            row.setOnClickListener {
                startActivity(Intent(context, entry.target))
            }
            card.addView(row)
        }
        container.addView(card)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
