package com.miku.ray.ui.dream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miku.ray.R
import com.miku.ray.core.LauncherManager
import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.entities.ServersCache
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.main.MainViewModel
import com.miku.ray.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 梦盒风格代理页：按订阅分组的节点卡片 */
class DreamProxyFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private var adapter: GroupAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_dream_proxy, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val groupAdapter = GroupAdapter(
            onSelectServer = { guid -> selectServer(guid) },
            onTestGroup = { mainViewModel.testAllRealPing() },
        )
        adapter = groupAdapter
        view.findViewById<RecyclerView>(R.id.dream_proxy_groups).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = groupAdapter
        }
        view.findViewById<ImageButton>(R.id.dream_proxy_test_all)
            .setOnClickListener { mainViewModel.testAllRealPing() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeGroups(groupAdapter) }
                launch {
                    mainViewModel.updateListItemEvent.collect {
                        groupAdapter.refreshDelays()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter?.refreshSelection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }

    private suspend fun observeGroups(groupAdapter: GroupAdapter) {
        val groups = withContext(Dispatchers.IO) {
            mainViewModel.getSubscriptions(requireContext())
        }
        if (groups.isEmpty()) {
            groupAdapter.submit(emptyList())
            return
        }
        val flows = withContext(Dispatchers.IO) {
            groups.map { mainViewModel.serversForGroup(it.id) }
        }
        combine(flows) { arr -> arr.toList() }.collect { lists ->
            val data = groups.mapIndexed { index, group -> group to lists[index] }
            groupAdapter.submit(data)
        }
    }

    private fun selectServer(guid: String) {
        if (!mainViewModel.beginServerRestart(guid)) return
        val activity = activity ?: return
        LauncherManager.restartService(activity) { handled ->
            mainViewModel.onServerRestartRequestResult(guid, handled)
        }
        adapter?.refreshSelection()
    }

    // ---------- 分组 adapter ----------

    private class GroupAdapter(
        private val onSelectServer: (String) -> Unit,
        private val onTestGroup: () -> Unit,
    ) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

        private var data: List<Pair<GroupMapItem, List<ServersCache>>> = emptyList()

        fun submit(newData: List<Pair<GroupMapItem, List<ServersCache>>>) {
            data = newData
            notifyDataSetChanged()
        }

        fun refreshDelays() = notifyDataSetChanged()
        fun refreshSelection() = notifyDataSetChanged()

        override fun getItemCount(): Int = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dream_group, parent, false)
            return GroupViewHolder(v, onSelectServer, onTestGroup)
        }

        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
            holder.bind(data[position].first, data[position].second)
        }

        class GroupViewHolder(
            itemView: View,
            private val onSelectServer: (String) -> Unit,
            private val onTestGroup: () -> Unit,
        ) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.dream_group_name)
            private val tvBadge: TextView = itemView.findViewById(R.id.dream_group_badge)
            private val btnTest: ImageButton = itemView.findViewById(R.id.dream_group_test)
            private val rvServers: RecyclerView = itemView.findViewById(R.id.dream_group_servers)
            private val serverAdapter = ServerAdapter(onSelectServer)

            init {
                rvServers.layoutManager = LinearLayoutManager(itemView.context)
                rvServers.adapter = serverAdapter
                btnTest.setOnClickListener { onTestGroup() }
            }

            fun bind(group: GroupMapItem, servers: List<ServersCache>) {
                tvName.text = group.remarks.ifBlank { group.id }
                tvBadge.text = itemView.context.getString(R.string.dream_server_count, servers.size)
                serverAdapter.submit(servers)
            }
        }
    }

    // ---------- 节点行 adapter ----------

    private class ServerAdapter(
        private val onSelectServer: (String) -> Unit,
    ) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

        private var data: List<ServersCache> = emptyList()

        fun submit(newData: List<ServersCache>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dream_server, parent, false)
            return ServerViewHolder(v)
        }

        override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
            val item = data[position]
            holder.bind(item, item.guid == MmkvManager.getSelectServer())
            holder.itemView.setOnClickListener { onSelectServer(item.guid) }
        }

        class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val row: View = itemView.findViewById(R.id.dream_server_row)
            private val tvFlag: TextView = itemView.findViewById(R.id.dream_server_flag)
            private val tvRemarks: TextView = itemView.findViewById(R.id.dream_server_remarks)
            private val tvDelay: TextView = itemView.findViewById(R.id.dream_server_delay)
            private val selectableBg: android.graphics.drawable.Drawable? by lazy {
                val tv = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true
                )
                ContextCompat.getDrawable(itemView.context, tv.resourceId)
            }

            fun bind(item: ServersCache, selected: Boolean) {
                val context = itemView.context
                tvRemarks.text = item.profile.remarks.ifBlank { item.profile.server }

                val aff = MmkvManager.decodeServerAffiliationInfo(item.guid)
                val code = aff?.countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
                tvFlag.text = Utils.countryCodeToFlag(code)

                val delayMs = aff?.testDelayMillis ?: 0L
                when {
                    delayMs > 0L -> {
                        tvDelay.text = "${delayMs}ms"
                        val colorRes = if (delayMs >= 500L) R.color.dream_orange else R.color.dream_green
                        tvDelay.setTextColor(ContextCompat.getColor(context, colorRes))
                    }
                    delayMs < 0L -> {
                        tvDelay.text = context.getString(R.string.connection_test_fail)
                        tvDelay.setTextColor(ContextCompat.getColor(context, R.color.dream_orange))
                    }
                    else -> {
                        tvDelay.text = ""
                    }
                }

                row.background = if (selected) {
                    ContextCompat.getDrawable(context, R.drawable.dream_server_selected)
                } else {
                    selectableBg?.constantState?.newDrawable(context.resources)
                }
            }
        }
    }
}
