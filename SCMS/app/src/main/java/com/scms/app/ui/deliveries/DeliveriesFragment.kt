package com.scms.app.ui.deliveries

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentDeliveriesBinding
import com.scms.app.databinding.ItemDeliveryBinding
import com.scms.app.models.Delivery
import com.scms.app.models.DeliveryStatusRequest
import com.scms.app.utils.*
import kotlinx.coroutines.launch
import androidx.lifecycle.MutableLiveData

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class DeliveriesViewModel : ViewModel() {
    val deliveries = MutableLiveData<Resource<List<Delivery>>>()
    val updateResult = MutableLiveData<Resource<Unit>>()

    fun load(status: String? = null) {
        viewModelScope.launch {
            deliveries.value = Resource.Loading()
            val result = safeApiCall { RetrofitClient.instance.getDeliveries(status = status) }
            when (result) {
                is Resource.Success -> deliveries.value = Resource.Success(result.data.data)
                is Resource.Error   -> deliveries.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun updateStatus(id: Int, status: String, location: String? = null, note: String? = null) {
        viewModelScope.launch {
            val result = safeApiCall {
                RetrofitClient.instance.updateDeliveryStatus(id, DeliveryStatusRequest(status, location, note))
            }
            updateResult.value = when (result) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Error   -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}

// ─── ADAPTER ─────────────────────────────────────────────────────────────────

class DeliveryAdapter(
    private var items: List<Delivery>,
    private val onUpdateStatus: (Delivery) -> Unit
) : RecyclerView.Adapter<DeliveryAdapter.VH>() {

    inner class VH(val binding: ItemDeliveryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDeliveryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        holder.binding.apply {
            tvDeliveryId.text  = "DEL-${d.id}"
            tvOrderId.text     = "Order #${d.order?.id ?: "—"}"
            tvProduct.text     = d.order?.product?.name ?: "—"
            tvDriver.text      = d.driver?.name ?: "Unassigned"
            tvDestination.text = d.destination
            tvEta.text         = "ETA: ${d.eta ?: "—"}"
            tvStatus.text      = statusLabel(d.status)
            tvStatus.setBackgroundColor(root.context.getColor(statusColor(d.status)))

            val steps = listOf("pending", "in_transit", "out_for_delivery", "delivered")
            val stepIdx = steps.indexOf(d.status)
            progress1.isActivated = stepIdx >= 0
            progress2.isActivated = stepIdx >= 1
            progress3.isActivated = stepIdx >= 2
            progress4.isActivated = stepIdx >= 3

            if (d.status != "delivered") {
                btnUpdateStatus.show()
                btnUpdateStatus.setOnClickListener { onUpdateStatus(d) }
            } else {
                btnUpdateStatus.hide()
            }
        }
    }

    fun update(newItems: List<Delivery>) {
        items = newItems
        notifyDataSetChanged()
    }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class DeliveriesFragment : Fragment() {

    private var _binding: FragmentDeliveriesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DeliveriesViewModel by viewModels()
    private lateinit var adapter: DeliveryAdapter
    private var currentFilter: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeliveriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DeliveryAdapter(emptyList()) { delivery -> showStatusPicker(delivery) }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load(currentFilter) }

        binding.chipAll.setOnClickListener          { currentFilter = null;               viewModel.load(null) }
        binding.chipPending.setOnClickListener      { currentFilter = "pending";          viewModel.load("pending") }
        binding.chipInTransit.setOnClickListener    { currentFilter = "in_transit";       viewModel.load("in_transit") }
        binding.chipOutForDelivery.setOnClickListener { currentFilter = "out_for_delivery"; viewModel.load("out_for_delivery") }
        binding.chipDelivered.setOnClickListener    { currentFilter = "delivered";        viewModel.load("delivered") }

        viewModel.deliveries.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> { binding.progressBar.show(); binding.recyclerView.hide() }
                is Resource.Success -> {
                    binding.progressBar.hide()
                    binding.swipeRefresh.isRefreshing = false
                    binding.recyclerView.show()
                    adapter.update(state.data)
                    binding.tvEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is Resource.Error -> {
                    binding.progressBar.hide()
                    binding.swipeRefresh.isRefreshing = false
                    toast(state.message)
                }
            }
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Status updated"); viewModel.load(currentFilter) }
            else if (state is Resource.Error) toast(state.message)
        }

        viewModel.load()
    }

    private fun showStatusPicker(delivery: Delivery) {
        val statuses = arrayOf("Pending", "In Transit", "Out for Delivery", "Delivered")
        val values   = arrayOf("pending", "in_transit", "out_for_delivery", "delivered")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update Delivery Status")
            .setItems(statuses) { _, which ->
                viewModel.updateStatus(delivery.id, values[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}