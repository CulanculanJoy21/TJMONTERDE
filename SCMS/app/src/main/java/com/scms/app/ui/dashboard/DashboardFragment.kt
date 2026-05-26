package com.scms.app.ui.dashboard

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentDashboardBinding
import com.scms.app.models.DashboardStats
import com.scms.app.utils.*
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class DashboardViewModel : ViewModel() {
    val stats = MutableLiveData<Resource<DashboardStats>>()
    fun load() {
        viewModelScope.launch {
            stats.value = Resource.Loading()
            stats.value = safeApiCall { RetrofitClient.instance.getDashboard() }
        }
    }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.stats.observe(viewLifecycleOwner) { state ->
            viewLifecycleOwner.lifecycleScope.launch {
                when (state) {
                    is Resource.Loading -> {
                        if (!binding.swipeRefresh.isRefreshing) {
                            // 🛠️ FIXED: Replaced custom extension helpers with clean native view visibility parameters
                            binding.progressBar.visibility = View.VISIBLE
                            binding.contentGroup.visibility = View.GONE
                        }
                    }
                    is Resource.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        binding.contentGroup.visibility = View.VISIBLE
                        bindStats(state.data)
                    }
                    is Resource.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        toast(state.message)
                    }
                }
            }
        }

        viewModel.load()
    }

    private fun bindStats(data: DashboardStats) {
        binding.tvTotalProducts.text    = data.totalProducts.toString()
        binding.tvPendingOrders.text    = data.pendingOrders.toString()
        binding.tvActiveDeliveries.text = data.activeDeliveries.toString()
        binding.tvLowStock.text         = data.lowStockItems.toString()
        binding.tvOrderValue.text       = formatCurrency(data.totalOrderValue)
        binding.tvOrdersMonth.text      = "${data.ordersThisMonth} orders this month"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}