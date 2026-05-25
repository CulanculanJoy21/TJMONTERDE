package com.scms.app.ui.orders

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentOrdersBinding
import com.scms.app.databinding.ItemOrderBinding
import com.scms.app.databinding.DialogOrderFormBinding
import com.scms.app.models.*
import com.scms.app.utils.*
import kotlinx.coroutines.launch
import androidx.lifecycle.MutableLiveData

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class OrdersViewModel : ViewModel() {
    val orders = MutableLiveData<Resource<List<Order>>>()
    val actionResult = MutableLiveData<Resource<Unit>>()

    fun load(status: String? = null) {
        viewModelScope.launch {
            orders.value = Resource.Loading()
            val result = safeApiCall { RetrofitClient.instance.getOrders(status = status) }
            when (result) {
                is Resource.Success -> orders.value = Resource.Success(result.data.data)
                is Resource.Error   -> orders.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun updateStatus(id: Int, status: String, note: String? = null) {
        viewModelScope.launch {
            val result = safeApiCall {
                RetrofitClient.instance.updateOrderStatus(id, OrderStatusRequest(status, note))
            }
            actionResult.value = when (result) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Error   -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}

// ─── ADAPTER ─────────────────────────────────────────────────────────────────

class OrderAdapter(
    private var items: List<Order>,
    private val userRole: String?,
    private val onApprove: (Order) -> Unit,
    private val onReject: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.VH>() {

    inner class VH(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val o = items[position]
        holder.binding.apply {
            tvOrderId.text    = "#${o.id}"
            tvProduct.text    = o.product?.name ?: "—"
            tvSupplier.text   = o.supplier?.name ?: "—"
            tvQty.text        = "Qty: ${o.qty}"
            tvTotal.text      = formatCurrency(o.totalAmount)
            tvDate.text       = o.createdAt.take(10)
            tvStatus.text     = statusLabel(o.status)
            tvStatus.setBackgroundColor(root.context.getColor(statusColor(o.status)))

            // 🔏 ROLE ENFORCEMENT: Only Admin accounts can reveal approval controls
            val role = userRole?.lowercase() ?: "field_personnel"
            if (role == "admin" && o.status == "pending") {
                btnApprove.show()
                btnReject.show()
                btnApprove.setOnClickListener { onApprove(o) }
                btnReject.setOnClickListener { onReject(o) }
            } else {
                btnApprove.hide()
                btnReject.hide()
            }
        }
    }

    fun update(newItems: List<Order>) {
        items = newItems
        notifyDataSetChanged()
    }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class OrdersFragment : Fragment() {

    private var _binding: FragmentOrdersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OrdersViewModel by viewModels()
    private lateinit var adapter: OrderAdapter
    private var currentFilter: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager(requireContext())
        val role = session.user?.role?.lowercase() ?: "field_personnel"

        adapter = OrderAdapter(
            emptyList(),
            userRole = role,
            onApprove = { order -> confirmAction(order, "approved") },
            onReject  = { order -> confirmAction(order, "rejected") }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load(currentFilter) }

        // Filter chips bindings
        binding.chipAll.setOnClickListener      { currentFilter = null;        viewModel.load(null) }
        binding.chipPending.setOnClickListener  { currentFilter = "pending";   viewModel.load("pending") }
        binding.chipApproved.setOnClickListener { currentFilter = "approved";  viewModel.load("approved") }
        binding.chipShipped.setOnClickListener  { currentFilter = "shipped";   viewModel.load("shipped") }
        binding.chipDelivered.setOnClickListener{ currentFilter = "delivered"; viewModel.load("delivered") }
        binding.chipRejected.setOnClickListener { currentFilter = "rejected";  viewModel.load("rejected") }

        // Contextual FAB visibility check
        // 🛠️ FORCED VISIBILITY: Showing the button unconditionally for testing
        binding.fabAdd.show()
        binding.fabAdd.setOnClickListener {
            OrderFormDialog { viewModel.load(currentFilter) }.show(childFragmentManager, "add_order")
        }

        viewModel.orders.observe(viewLifecycleOwner) { state ->
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

        viewModel.actionResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Order status updated"); viewModel.load(currentFilter) }
            else if (state is Resource.Error) toast(state.message)
        }

        viewModel.load()
    }

    private fun confirmAction(order: Order, action: String) {
        val label = if (action == "approved") "Approve" else "Reject"
        AlertDialog.Builder(requireContext())
            .setTitle("$label Order")
            .setMessage("$label order #${order.id}?")
            .setPositiveButton(label) { _, _ -> viewModel.updateStatus(order.id, action) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── FORM DIALOG ──────────────────────────────────────────────────────────────

class OrderFormDialog(private val onSuccess: () -> Unit) : BottomSheetDialogFragment() {

    private var _binding: DialogOrderFormBinding? = null
    private val binding get() = _binding!!
    private var products  = listOf<Product>()
    private var suppliers = listOf<Supplier>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogOrderFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadData()
        setupTextCalculationListeners()
        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val pResult = safeApiCall { RetrofitClient.instance.getProducts() }
            val sResult = safeApiCall { RetrofitClient.instance.getSuppliers() }

            if (pResult is Resource.Success) {
                products = pResult.data.data
                val names = products.map { it.name }
                binding.spinnerProduct.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                binding.spinnerProduct.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        if (products.isNotEmpty()) {
                            binding.etUnitPrice.setText(products[position].unitPrice.toString())
                            calculateLiveTotal()
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            if (sResult is Resource.Success) {
                suppliers = sResult.data.data
                val names = suppliers.map { it.name }
                binding.spinnerSupplier.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
        }
    }

    private fun setupTextCalculationListeners() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { calculateLiveTotal() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.etQty.addTextChangedListener(watcher)
        binding.etUnitPrice.addTextChangedListener(watcher)
    }

    private fun calculateLiveTotal() {
        val qty = binding.etQty.text.toString().toIntOrNull() ?: 0
        val price = binding.etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
        binding.tvTotal.text = formatCurrency(qty * price)
    }

    private fun save() {
        if (products.isEmpty() || suppliers.isEmpty()) { toast("Loading data, please wait"); return }

        val qty   = binding.etQty.text.toString().toIntOrNull() ?: 0
        val price = binding.etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
        val notes = binding.etNotes.text.toString().trim()

        if (qty <= 0 || price <= 0.0) { toast("Enter valid quantity and price"); return }

        val request = OrderRequest(
            productId  = products[binding.spinnerProduct.selectedItemPosition].id,
            supplierId = suppliers[binding.spinnerSupplier.selectedItemPosition].id,
            qty        = qty,
            unitPrice  = price,
            notes      = notes.ifEmpty { null }
        )

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            val result = safeApiCall { RetrofitClient.instance.createOrder(request) }
            binding.btnSave.isEnabled = true

            when (result) {
                is Resource.Success -> {
                    toast("Order created successfully!")
                    onSuccess(); dismiss()
                }
                is Resource.Error   -> {
                    if (result.message.contains("202") || result.message.contains("review", ignoreCase = true)) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Order Submitted")
                            .setMessage("Your purchase request has been safely sent to the Admin Approvals Staging Queue.")
                            .setPositiveButton("OK") { _, _ -> onSuccess(); dismiss() }
                            .show()
                    } else {
                        toast(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}