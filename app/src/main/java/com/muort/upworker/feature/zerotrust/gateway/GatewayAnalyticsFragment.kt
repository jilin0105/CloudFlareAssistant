package com.muort.upworker.feature.zerotrust.gateway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.GatewayDnsAnalytics
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.databinding.FragmentGatewayAnalyticsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Gateway DNS 查询分析
 */
@AndroidEntryPoint
class GatewayAnalyticsFragment : Fragment() {

    private var _binding: FragmentGatewayAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GatewayViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var opsAdapter: DnsAnalyticsAdapter
    private lateinit var countriesAdapter: DnsAnalyticsAdapter
    private lateinit var locationsAdapter: DnsAnalyticsAdapter
    private lateinit var blockedUsersAdapter: DnsAnalyticsAdapter
    private lateinit var allowedUsersAdapter: DnsAnalyticsAdapter

    // 每个区块显示的条目数（Top N），默认 5
    private var topN: Int = 5
    // 缓存当前分析数据，切换 Top N 时无需重新请求
    private var currentData: GatewayDnsAnalytics? = null

    private val topNOptions = listOf(5, 10, 20, 50)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGatewayAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        observeViewModel()
        loadData()
    }

    private fun setupRecyclerViews() {
        opsAdapter = DnsAnalyticsAdapter()
        countriesAdapter = DnsAnalyticsAdapter()
        locationsAdapter = DnsAnalyticsAdapter()
        blockedUsersAdapter = DnsAnalyticsAdapter()
        allowedUsersAdapter = DnsAnalyticsAdapter()

        binding.opsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = opsAdapter
        }
        binding.countriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = countriesAdapter
        }
        binding.locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = locationsAdapter
        }
        binding.blockedUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = blockedUsersAdapter
        }
        binding.allowedUsersRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = allowedUsersAdapter
        }

        binding.retryButton.setOnClickListener { loadData() }

        setupTopNDropdown()
    }

    private fun setupTopNDropdown() {
        val optionLabels = topNOptions.map { getString(R.string.zt_gateway_dns_top_count, it) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, optionLabels)
        binding.itemsDropdown.setAdapter(adapter)
        binding.itemsDropdown.setText(optionLabels[topNOptions.indexOf(topN)], false)
        binding.itemsDropdown.threshold = 0
        binding.itemsDropdown.setOnClickListener { binding.itemsDropdown.showDropDown() }
        binding.itemsDropdown.setOnItemClickListener { _, _, position, _ ->
            topN = topNOptions[position]
            currentData?.let { renderAnalytics(it) }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dnsAnalyticsLoading.collect { loading ->
                        binding.loadingContainer.visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.dnsAnalyticsError.collect { error ->
                        if (error != null) {
                            binding.errorContainer.visibility = View.VISIBLE
                            binding.contentContainer.visibility = View.GONE
                            binding.errorText.text =
                                getString(R.string.zt_gateway_dns_error_format, error)
                        } else {
                            binding.errorContainer.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.dnsAnalytics.collect { analytics ->
                        analytics?.let { renderAnalytics(it) }
                    }
                }
            }
        }
    }

    private fun renderAnalytics(data: GatewayDnsAnalytics) {
        currentData = data

        val isEmpty = data.operations.isEmpty() && data.countries.isEmpty() &&
                data.locations.isEmpty()

        binding.contentContainer.visibility = View.VISIBLE
        binding.emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE

        if (isEmpty) {
            return
        }

        // 阻止次数最多的用户
        val blockedDecisions = setOf("3", "6", "9")
        val totalBlocked = data.operations
            .filter { it.resolverDecision in blockedDecisions }
            .sumOf { it.count }

        // 允许次数最多的用户
        val allowedDecisions = setOf("4", "5", "7", "8", "10")
        val totalAllowed = data.operations
            .filter { it.resolverDecision in allowedDecisions }
            .sumOf { it.count }

        opsAdapter.submitList(
            data.operations.take(topN)
                .map { DnsAnalyticsAdapter.Item(mapResolverDecision(it.resolverDecision), it.count) }
                .toMutableList()
        )
        countriesAdapter.submitList(
            data.countries.take(topN)
                .map { DnsAnalyticsAdapter.Item(it.countryCode, it.count) }
                .toMutableList()
        )
        locationsAdapter.submitList(
            data.locations.take(topN)
                .map { DnsAnalyticsAdapter.Item(it.locationName, it.count) }
                .toMutableList()
        )
        blockedUsersAdapter.submitList(
            if (totalBlocked > 0) {
                mutableListOf(DnsAnalyticsAdapter.Item(getString(R.string.zt_gateway_dns_no_identity), totalBlocked))
            } else {
                mutableListOf()
            }
        )
        allowedUsersAdapter.submitList(
            if (totalAllowed > 0) {
                mutableListOf(DnsAnalyticsAdapter.Item(getString(R.string.zt_gateway_dns_no_identity), totalAllowed))
            } else {
                mutableListOf()
            }
        )
    }

    /**
     * resolverDecision 维度返回数值（如 5、10），映射为中文描述
     * 3=类别阻止, 4=无匹配位置时允许, 5=无策略匹配时允许,
     * 6=始终阻止的类别, 7=安全搜索覆盖, 8=已应用覆盖,
     * 9=规则阻止, 10=规则允许
     */
    private fun mapResolverDecision(decision: String): String {
        val resId = when (decision) {
            "3" -> R.string.zt_gateway_dns_decision_blocked_category
            "4" -> R.string.zt_gateway_dns_decision_allowed_no_location
            "5" -> R.string.zt_gateway_dns_decision_allowed_no_policy
            "6" -> R.string.zt_gateway_dns_decision_blocked_always_category
            "7" -> R.string.zt_gateway_dns_decision_override_safesearch
            "8" -> R.string.zt_gateway_dns_decision_override_applied
            "9" -> R.string.zt_gateway_dns_decision_blocked_rule
            "10" -> R.string.zt_gateway_dns_decision_allowed_rule
            else -> R.string.zt_gateway_dns_decision_unknown
        }
        return getString(resId)
    }

    private fun loadData() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            binding.errorContainer.visibility = View.VISIBLE
            binding.contentContainer.visibility = View.GONE
            binding.errorText.text = getString(R.string.msg_no_account_selected)
            return
        }
        binding.errorContainer.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadDnsAnalytics(account, TimeRange.SEVEN_DAYS)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
