package com.miku.ray.ui.dream

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.core.LauncherManager
import com.miku.ray.dto.EConfigType
import com.miku.ray.enums.PermissionType
import com.miku.ray.extension.snackbarError
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.AngConfigManager
import com.miku.ray.handler.MmkvManager
import com.miku.ray.handler.SettingsManager
import com.miku.ray.handler.SubscriptionUpdater
import com.miku.ray.ui.base.HelperBaseActivity
import com.miku.ray.ui.bottomsheet.AddConfigBottomSheet
import com.miku.ray.ui.main.MainViewModel
import com.miku.ray.ui.server.ServerGroupActivity
import com.miku.ray.ui.server.ServerHysteria2Activity
import com.miku.ray.ui.server.ServerProxyChainActivity
import com.miku.ray.ui.server.ServerShadowsocksActivity
import com.miku.ray.ui.server.ServerSocksActivity
import com.miku.ray.ui.server.ServerTrojanActivity
import com.miku.ray.ui.server.ServerVlessActivity
import com.miku.ray.ui.server.ServerVmessActivity
import com.miku.ray.ui.server.ServerWireguardActivity
import com.miku.ray.util.LogUtil
import com.miku.ray.util.Utils
import com.miku.ray.util.requestSubscriptionImportName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 梦盒「轮回空间」风格主界面（替换 MikuRay 原生主屏）。
 * 复用 MainViewModel 的全部服务接线；MainActivity 保留不动作为备份。
 */
class DreamMainActivity : HelperBaseActivity(),
    AddConfigBottomSheet.OnAddConfigClickListener {

    val mainViewModel: MainViewModel by viewModels()

    private val fragments = listOf(
        DreamHomeFragment(),
        DreamProxyFragment(),
        DreamConfigFragment(),
        DreamSettingsFragment(),
    )
    private var currentTab = 0

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) startV2Ray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dream_main)

        hideLoading()
        window.statusBarColor = Color.TRANSPARENT

        setupTabs()
        setupViewModel()

        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)

        mainViewModel.reloadServerList()
        SubscriptionUpdater.sync()
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
    }

    private fun setupTabs() {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        fragments.forEachIndexed { index, fragment ->
            tx.add(R.id.dream_fragment_container, fragment, "dream_tab_$index")
            if (index != 0) tx.hide(fragment)
        }
        tx.commitNowAllowingStateLoss()

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.dream_bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val index = when (item.itemId) {
                R.id.dream_nav_home -> 0
                R.id.dream_nav_proxy -> 1
                R.id.dream_nav_config -> 2
                R.id.dream_nav_settings -> 3
                else -> return@setOnItemSelectedListener false
            }
            switchTab(index)
            true
        }
    }

    fun switchTab(index: Int) {
        if (index == currentTab) return
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        tx.hide(fragments[currentTab])
        tx.show(fragments[index])
        tx.commitAllowingStateLoss()
        currentTab = index
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.dream_bottom_nav)
        val itemId = when (index) {
            0 -> R.id.dream_nav_home
            1 -> R.id.dream_nav_proxy
            2 -> R.id.dream_nav_config
            else -> R.id.dream_nav_settings
        }
        if (bottomNav.selectedItemId != itemId) bottomNav.selectedItemId = itemId
    }

    private fun setupViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainViewModel.requestServiceStartEvent.collect { requestServiceStart() }
                }
            }
        }
    }

    private fun requestServiceStart() {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) startV2Ray() else requestVpnPermission.launch(intent)
    }

    private fun startV2Ray() {
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            snackbarError(getString(R.string.title_file_chooser), title = getString(R.string.title_alerter_error))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && MmkvManager.decodeSettingsBool(AppConfig.PREF_PROXY_SHARING)) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }

        LauncherManager.startService(this)
    }

    // ---- AddConfigBottomSheet.OnAddConfigClickListener ----

    override fun onAddConfigOptionClicked(viewId: Int) {
        when (viewId) {
            R.id.import_qrcode -> launchQRCodeScanner { text -> importBatchConfig(text) }
            R.id.import_clipboard -> {
                try {
                    importBatchConfig(Utils.getClipboard(this))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to import config from clipboard", e)
                }
            }
            R.id.import_local -> launchFileChooser { uri ->
                if (uri == null) return@launchFileChooser
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val text = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?.toString(Charsets.UTF_8)
                        withContext(Dispatchers.Main) { importBatchConfig(text) }
                    } catch (e: Exception) {
                        LogUtil.e(AppConfig.TAG, "Failed to read config file", e)
                    }
                }
            }
            R.id.import_manually_policy_group -> startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerGroupActivity::class.java)
            )
            R.id.import_manually_proxy_chain -> startActivity(
                Intent()
                    .putExtra("subscriptionId", mainViewModel.subscriptionId)
                    .setClass(this, ServerProxyChainActivity::class.java)
            )
            R.id.import_manually_vmess -> importManually(EConfigType.VMESS.value)
            R.id.import_manually_vless -> importManually(EConfigType.VLESS.value)
            R.id.import_manually_ss -> importManually(EConfigType.SHADOWSOCKS.value)
            R.id.import_manually_socks -> importManually(EConfigType.SOCKS.value)
            R.id.import_manually_http -> importManually(EConfigType.HTTP.value)
            R.id.import_manually_trojan -> importManually(EConfigType.TROJAN.value)
            R.id.import_manually_wireguard -> importManually(EConfigType.WIREGUARD.value)
            R.id.import_manually_hysteria2 -> importManually(EConfigType.HYSTERIA2.value)
        }
    }

    private fun importManually(createConfigType: Int) {
        val targetActivity = when (EConfigType.fromInt(createConfigType)) {
            EConfigType.VLESS -> ServerVlessActivity::class.java
            EConfigType.TROJAN -> ServerTrojanActivity::class.java
            EConfigType.SHADOWSOCKS -> ServerShadowsocksActivity::class.java
            EConfigType.SOCKS, EConfigType.HTTP -> ServerSocksActivity::class.java
            EConfigType.WIREGUARD -> ServerWireguardActivity::class.java
            EConfigType.HYSTERIA2 -> ServerHysteria2Activity::class.java
            else -> ServerVmessActivity::class.java
        }
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, targetActivity)
        )
    }

    private fun importBatchConfig(server: String?) {
        if (server.isNullOrEmpty()) return
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val (count, countSub) = AngConfigManager.importBatchConfig(
                    server,
                    mainViewModel.subscriptionId,
                    true,
                ) { suggested, existing ->
                    requestSubscriptionImportName(suggested, existing)
                }
                delay(500L)
                withContext(Dispatchers.Main) {
                    when {
                        count > 0 -> {
                            snackbarSuccess(
                                getString(R.string.title_import_config_count, count),
                                title = getString(R.string.title_alerter_success)
                            )
                            mainViewModel.reloadServerList()
                        }
                        countSub > 0 -> mainViewModel.reloadServerList()
                        else -> snackbarError(
                            getString(R.string.import_configuration),
                            title = getString(R.string.title_alerter_error)
                        )
                    }
                    hideLoading()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarError(
                        getString(R.string.import_configuration),
                        title = getString(R.string.title_alerter_error)
                    )
                    hideLoading()
                }
                LogUtil.e(AppConfig.TAG, "Failed to import batch config", e)
            }
        }
    }

}
