package com.scms.app.ui.deliveries

import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
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
    val deleteResult = MutableLiveData<Resource<Unit>>() // 🛠️ Tracks removal completions

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

    // 🛠️ Dispatches permanent deletion downstream to the backend API route
    fun deleteDelivery(id: Int) {
        viewModelScope.launch {
            val result = safeApiCall { RetrofitClient.instance.deleteDelivery(id) }
            deleteResult.value = when (result) {
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
    private val userRole: String?, // 🛠️ Explicit access rule context tracking
    private val onUpdateStatus: (Delivery) -> Unit,
    private val onDeleteDelivery: (Delivery) -> Unit // 🛠️ Triggers deletion logic dialogs
) : RecyclerView.Adapter<DeliveryAdapter.VH>() {

    inner class VH(val binding: ItemDeliveryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemDeliveryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = items[position]
        holder.binding.apply {
            tvDeliveryId.text  = "TRK-${d.id}"
            tvOrderId.text     = "Order ID: #${d.order?.id ?: "—"}"
            tvProduct.text     = d.order?.product?.name ?: "—"
            tvDriver.text      = d.driver?.name ?: "Unassigned"
            tvDestination.text = d.destination
            val rawEta = d.eta
            if (!rawEta.isNullOrEmpty() && rawEta.contains("T")) {
                try {
                    val parts = rawEta.split("T")
                    val dateParts = parts[0].split("-") // [2026, 05, 19]
                    val timeParts = parts[1].split(":") // [16, 00, 00...]

                    val year = dateParts[0].substring(2) // "26"
                    val month = dateParts[1].toInt().toString() // "5"
                    val day = dateParts[2].toInt().toString() // "19"

                    var hour = timeParts[0].toInt()
                    val ampm = if (hour >= 12) "PM" else "AM"
                    if (hour > 12) hour -= 12
                    if (hour == 0) hour = 12

                    tvEta.text = "ETA: $month/$day/$year, $hour:00 $ampm"
                } catch (e: Exception) {
                    tvEta.text = "ETA: $rawEta" // Fallback if parsing fails
                }
            } else {
                tvEta.text = "ETA: —"
            }
            tvStatus.text      = statusLabel(d.status)
            tvStatus.setBackgroundColor(root.context.getColor(statusColor(d.status)))

            val steps = listOf("pending", "in_transit", "out_for_delivery", "delivered")
            val stepIdx = steps.indexOf(d.status)
            progress1.isActivated = stepIdx >= 0
            progress2.isActivated = stepIdx >= 1
            progress3.isActivated = stepIdx >= 2
            progress4.isActivated = stepIdx >= 3

            // Control tracking modification capabilities based on current completion state
            if (d.status != "delivered" && d.status != "cancelled") {
                btnUpdateStatus.show()
                btnUpdateStatus.setOnClickListener { onUpdateStatus(d) }

                // 🛠️ INLINE CANCELLATION: Pressing and holding the Delivery ID label acts as a shortcut cancel route
                tvDeliveryId.setOnLongClickListener {
                    AlertDialog.Builder(root.context)
                        .setTitle("Cancel Delivery Operations")
                        .setMessage("Permanently remove delivery tracking trace record TRK-${d.id} from local logs?")
                        .setPositiveButton("Cancel Delivery") { _, _ ->
                            onUpdateStatus(d.copy(status = "cancelled"))
                        }
                        .setNegativeButton("Keep Active", null)
                        .show()
                    true
                }
            } else {
                btnUpdateStatus.hide()
                tvDeliveryId.setOnLongClickListener(null) // Strip long click listeners for resolved cards
            }

            // 🛠️ CONDITIONAL PRIVILEGE FILTERING: Reveal the layout's trash bin shortcut only to admins
            val role = userRole?.lowercase() ?: "field_personnel"
            if (role == "admin" && (d.status == "delivered" || d.status == "cancelled")) {
                btnDeleteDelivery.show()
                btnDeleteDelivery.setOnClickListener { onDeleteDelivery(d) }
            } else {
                btnDeleteDelivery.hide()
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

        val session = SessionManager(requireContext())

        adapter = DeliveryAdapter(
            emptyList(),
            userRole = session.user?.role,
            onUpdateStatus = { delivery ->
                // Checks if this invocation was fired from our custom inline cancellation trigger shortcut
                if (delivery.status == "cancelled") {
                    viewModel.updateStatus(delivery.id, "cancelled")
                } else {
                    showStatusPicker(delivery)
                }
            },
            onDeleteDelivery = { delivery ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Remove Tracking Record")
                    .setMessage("Permanently remove delivery tracking trace record DEL-${delivery.id} from local logs?")
                    .setPositiveButton("Remove") { _, _ -> viewModel.deleteDelivery(delivery.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.load(currentFilter) }

        binding.chipAll.setOnClickListener            { currentFilter = null;               viewModel.load(null) }
        binding.chipPending.setOnClickListener        { currentFilter = "pending";          viewModel.load("pending") }
        binding.chipInTransit.setOnClickListener      { currentFilter = "in_transit";       viewModel.load("in_transit") }
        binding.chipOutForDelivery.setOnClickListener   { currentFilter = "out_for_delivery"; viewModel.load("out_for_delivery") }
        binding.chipDelivered.setOnClickListener      { currentFilter = "delivered";        viewModel.load("delivered") }

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
                is Resource.Error -> { binding.progressBar.hide(); binding.swipeRefresh.isRefreshing = false; toast(state.message) }
            }
        }

        viewModel.updateResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Status updated"); viewModel.load(currentFilter) }
            else if (state is Resource.Error) toast(state.message)
        }

        // 🛠️ Observe database clearance operations and sync UI automatically
        viewModel.deleteResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Tracking record removed"); viewModel.load(currentFilter) }
            else if (state is Resource.Error) toast(state.message)
        }

        viewModel.load()
    }

    private fun showStatusPicker(delivery: Delivery) {
        val statuses = arrayOf("Pending", "In Transit", "Out for Delivery", "Delivered")
        val values   = arrayOf("pending", "in_transit", "out_for_delivery", "delivered")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update Delivery Status")
            .setItems(statuses) { _, which -> viewModel.updateStatus(delivery.id, values[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}