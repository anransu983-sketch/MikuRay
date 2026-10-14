package com.miku.ray.ui.dream

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.miku.ray.R
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import com.miku.ray.extension.snackbarSuccess
import com.miku.ray.handler.MmkvManager
import com.miku.ray.ui.bottomsheet.AddConfigBottomSheet
import com.miku.ray.ui.bottomsheet.ShareSubBottomSheet
import com.miku.ray.ui.subscription.SubEditActivity
import com.miku.ray.ui.subscription.SubscriptionsViewModel
import com.miku.ray.util.showDeleteConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 梦盒风格配置页：订阅卡片列表 */
class DreamConfigFragment : Fragment() {

    private val subViewModel: SubscriptionsViewModel by viewModels()
    private var adapter: SubAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_dream_config, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val subAdapter = SubAdapter(
            onToggle = { guid, item, enabled ->
                item.enabled = enabled
                subViewModel.update(guid, item)
            },
            onShare = { item ->
                if (item.url.isNotEmpty()) {
                    ShareSubBottomSheet.newInstance(item.url)
                        .show(requireActivity().supportFragmentManager, ShareSubBottomSheet.TAG)
                }
            },
            onDelete = { guid ->
                showDeleteConfirmDialog(
                    requireContext(),
                    R.string.dream_delete_sub_message,
                ) {
                    subViewModel.remove(guid)
                }
            },
            onUpdate = { guid ->
                updateSubscription(guid)
            },
            onEdit = { guid ->
                startActivity(
                    Intent(requireContext(), SubEditActivity::class.java)
                        .putExtra("subId", guid)
                )
            },
        )
        adapter = subAdapter
        view.findViewById<RecyclerView>(R.id.dream_config_list).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = subAdapter
        }
        view.findViewById<Button>(R.id.dream_config_add).setOnClickListener {
            AddConfigBottomSheet().show(
                requireActivity().supportFragmentManager,
                AddConfigBottomSheet.TAG
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                subViewModel.subscriptionsState.collect { list ->
                    subAdapter.submit(list)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        subViewModel.reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }

    private fun updateSubscription(guid: String) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val result = subViewModel.updateSubscriptionsOnly()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                subViewModel.reload()
                requireContext().snackbarSuccess(
                    getString(R.string.title_import_config_count, result.configCount),
                    title = getString(R.string.title_alerter_success)
                )
            }
        }
    }

    // ---------- 订阅卡片 adapter ----------

    private class SubAdapter(
        private val onToggle: (String, SubscriptionItem, Boolean) -> Unit,
        private val onShare: (SubscriptionItem) -> Unit,
        private val onDelete: (String) -> Unit,
        private val onUpdate: (String) -> Unit,
        private val onEdit: (String) -> Unit,
    ) : RecyclerView.Adapter<SubAdapter.SubViewHolder>() {

        private var data: List<SubscriptionCache> = emptyList()

        fun submit(newData: List<SubscriptionCache>) {
            data = newData
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dream_subscription, parent, false)
            return SubViewHolder(v)
        }

        override fun onBindViewHolder(holder: SubViewHolder, position: Int) {
            val cache = data[position]
            holder.bind(cache.guid, cache.subscription, onToggle, onShare, onDelete, onUpdate, onEdit)
        }

        class SubViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvRemarks: TextView = itemView.findViewById(R.id.dream_sub_remarks)
            private val swEnabled: MaterialSwitch = itemView.findViewById(R.id.dream_sub_enabled)
            private val tvTraffic: TextView = itemView.findViewById(R.id.dream_sub_traffic)
            private val progress: ProgressBar = itemView.findViewById(R.id.dream_sub_progress)
            private val tvExpire: TextView = itemView.findViewById(R.id.dream_sub_expire)
            private val tvUpdated: TextView = itemView.findViewById(R.id.dream_sub_updated)
            private val btnShare: ImageButton = itemView.findViewById(R.id.dream_sub_share)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.dream_sub_delete)
            private val btnUpdate: Button = itemView.findViewById(R.id.dream_sub_update)
            private val btnEdit: Button = itemView.findViewById(R.id.dream_sub_edit)

            fun bind(
                guid: String,
                item: SubscriptionItem,
                onToggle: (String, SubscriptionItem, Boolean) -> Unit,
                onShare: (SubscriptionItem) -> Unit,
                onDelete: (String) -> Unit,
                onUpdate: (String) -> Unit,
                onEdit: (String) -> Unit,
            ) {
                val context = itemView.context
                tvRemarks.text = item.remarks.ifBlank { guid }

                swEnabled.setOnCheckedChangeListener(null)
                swEnabled.isChecked = item.enabled
                swEnabled.setOnCheckedChangeListener { button, isChecked ->
                    if (!button.isPressed) return@setOnCheckedChangeListener
                    onToggle(guid, item, isChecked)
                }

                val used = item.bytesUsed
                val remaining = item.bytesRemaining
                if (used >= 0 && remaining >= 0) {
                    val total = used + remaining
                    val pct = if (total > 0) (used * 100 / total).toInt() else 0
                    tvTraffic.text = context.getString(
                        R.string.dream_traffic_format,
                        MmkvManager.formatTrafficBytesPublic(used),
                        MmkvManager.formatTrafficBytesPublic(total),
                        pct
                    )
                    tvTraffic.visibility = View.VISIBLE
                    progress.visibility = View.VISIBLE
                    progress.progress = pct
                } else if (used >= 0) {
                    tvTraffic.text = context.getString(
                        R.string.dream_traffic_used_format,
                        MmkvManager.formatTrafficBytesPublic(used)
                    )
                    tvTraffic.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                } else {
                    tvTraffic.visibility = View.GONE
                    progress.visibility = View.GONE
                }

                if (item.expiresAt > 0) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(Date(item.expiresAt))
                    val daysLeft = ((item.expiresAt - System.currentTimeMillis()) / 86400000L)
                        .coerceAtLeast(0L)
                    tvExpire.text = context.getString(
                        R.string.dream_expire_format, dateStr, daysLeft.toInt()
                    )
                } else {
                    tvExpire.text = context.getString(R.string.dream_no_expire)
                }

                tvUpdated.text = relativeTime(context, item.lastUpdated)

                btnShare.setOnClickListener { onShare(item) }
                btnDelete.setOnClickListener { onDelete(guid) }
                btnUpdate.setOnClickListener { onUpdate(guid) }
                btnEdit.setOnClickListener { onEdit(guid) }
            }

            private fun relativeTime(context: android.content.Context, ts: Long): String {
                if (ts <= 0) return ""
                val diffMin = (System.currentTimeMillis() - ts) / 60000L
                return when {
                    diffMin < 1 -> context.getString(R.string.dream_updated_just)
                    diffMin < 60 -> context.getString(R.string.dream_updated_minutes, diffMin.toInt())
                    diffMin < 1440 -> context.getString(R.string.dream_updated_hours, (diffMin / 60).toInt())
                    else -> context.getString(R.string.dream_updated_days, (diffMin / 1440).toInt())
                }
            }
        }
    }
}
