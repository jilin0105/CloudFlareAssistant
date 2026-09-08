package com.muort.upworker.feature.zerotrust.gateway

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.databinding.ItemDnsAnalyticsRowBinding
import java.util.Locale

/**
 * 通用 DNS 分析行适配器
 * 渲染名称 + 数值 + 水平进度条（按当前列表最大值计算百分比）
 */
class DnsAnalyticsAdapter : ListAdapter<DnsAnalyticsAdapter.Item, DnsAnalyticsAdapter.ViewHolder>(DiffCallback()) {

    data class Item(
        val name: String,
        val count: Long
    )

    private var maxCount: Long = 1L

    override fun submitList(list: MutableList<Item>?) {
        maxCount = list?.maxOfOrNull { it.count }?.coerceAtLeast(1L) ?: 1L
        super.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDnsAnalyticsRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), maxCount)
    }

    class ViewHolder(private val binding: ItemDnsAnalyticsRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item, maxCount: Long) {
            binding.dnsItemName.text = item.name
            binding.dnsItemCount.text = formatCount(item.count)
            val percent = ((item.count.toDouble() / maxCount) * 100).toInt().coerceIn(0, 100)
            binding.dnsItemProgress.progress = percent
        }

        private fun formatCount(count: Long): String {
            return if (count >= 10000) {
                String.format(Locale.ROOT, "%.2f万", count / 10000.0)
            } else {
                count.toString()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem.name == newItem.name

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem == newItem
    }
}
