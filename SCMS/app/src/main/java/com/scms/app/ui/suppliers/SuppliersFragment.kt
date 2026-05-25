package com.scms.app.ui.suppliers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentSuppliersBinding
import com.scms.app.databinding.ItemSupplierBinding
import com.scms.app.databinding.DialogSupplierFormBinding
import com.scms.app.models.Supplier
import com.scms.app.models.SupplierRequest
import com.scms.app.utils.*
import kotlinx.coroutines.launch

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class SuppliersViewModel : ViewModel() {
    val suppliers = MutableLiveData<Resource<List<Supplier>>>()
    val actionResult = MutableLiveData<Resource<Unit>>()

    fun load() {
        viewModelScope.launch {
            suppliers.value = Resource.Loading()
            val result = safeApiCall { RetrofitClient.instance.getSuppliers() }
            when (result) {
                is Resource.Success -> suppliers.value = Resource.Success(result.data.data)
                is Resource.Error   -> suppliers.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            val result = safeApiCall { RetrofitClient.instance.deleteSupplier(id) }
            actionResult.value = when (result) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Error   -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}

// ─── ADAPTER ─────────────────────────────────────────────────────────────────

class SupplierAdapter(
    private var items: List<Supplier>,
    private val isAdmin: Boolean,
    private val onEdit: (Supplier) -> Unit,
    private val onDelete: (Supplier) -> Unit
) : RecyclerView.Adapter<SupplierAdapter.VH>() {

    // 🛠️ ADD THIS: Keeps a master backup copy of your supplier directory data
    private var unfilteredItems: List<Supplier> = items

    inner class VH(val binding: ItemSupplierBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSupplierBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.binding.apply {
            tvName.text = s.name
            tvCountry.text = s.country ?: "Local Vendor"
            tvEmail.text = "Email: ${s.email ?: "—"}"
            tvPhone.text = "Phone: ${s.phone ?: "—"}"
            tvActiveOrders.text = "Manual Fulfillment Mode"
            tvRating.text = "★ ${s.rating ?: "0.0"}"

            root.setOnClickListener {
                if (!s.phone.isNullOrBlank()) {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}"))
                    root.context.startActivity(intent)
                } else {
                    Toast.makeText(root.context, "No phone details listed for this vendor", Toast.LENGTH_SHORT).show()
                }
            }

            if (isAdmin) {
                btnEdit.show()
                btnDelete.show()
                btnEdit.setOnClickListener { onEdit(s) }
                btnDelete.setOnClickListener { onDelete(s) }
            } else {
                btnEdit.hide()
                btnDelete.hide()
            }
        }
    }

    // 🛠️ UPDATED: Updates both lists when new network response calls drop in
    fun update(newItems: List<Supplier>) {
        items = newItems
        unfilteredItems = newItems
        notifyDataSetChanged()
    }

    // 🛠️ ADD THIS FUNCTION: Local character loop logic for real-time string sorting
    fun filter(query: String) {
        val cleanQuery = query.lowercase().trim()
        items = if (cleanQuery.isEmpty()) {
            unfilteredItems
        } else {
            unfilteredItems.filter { supplier ->
                supplier.name.lowercase().contains(cleanQuery) ||
                        (supplier.country?.lowercase()?.contains(cleanQuery) ?: false)
            }
        }
        notifyDataSetChanged()
    }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class SuppliersFragment : Fragment() {

    private var _binding: FragmentSuppliersBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SuppliersViewModel by viewModels()
    private lateinit var adapter: SupplierAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSuppliersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager(requireContext())
        val role = session.user?.role?.lowercase() ?: "field_personnel"
        val isAdminUser = role == "admin"

        adapter = SupplierAdapter(
            emptyList(),
            isAdmin = isAdminUser,
            onEdit  = { supplier -> SupplierFormDialog(supplier) { viewModel.load() }.show(childFragmentManager, "edit_supplier") },
            onDelete = { supplier -> confirmDelete(supplier) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        // 🛠️ FIXED: Real-time text watcher listener hooked up to filter layout datasets on-the-fly
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        if (isAdminUser) {
            binding.fabAdd.show()
            binding.fabAdd.setOnClickListener {
                SupplierFormDialog(null) { viewModel.load() }.show(childFragmentManager, "add_supplier")
            }
        } else {
            binding.fabAdd.hide()
        }

        viewModel.suppliers.observe(viewLifecycleOwner) { state ->
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

        viewModel.actionResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Supplier data synced"); viewModel.load() }
            else if (state is Resource.Error) toast(state.message)
        }

        viewModel.load()
    }

    private fun confirmDelete(supplier: Supplier) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Supplier")
            .setMessage("Are you sure you want to remove \"${supplier.name}\" from the directory?")
            .setPositiveButton("Remove") { _, _ -> viewModel.delete(supplier.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ─── FORM DIALOG ──────────────────────────────────────────────────────────────

class SupplierFormDialog(
    private val supplier: Supplier?,
    private val onSuccess: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogSupplierFormBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogSupplierFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = if (supplier != null) "Edit Supplier Info" else "Register New Supplier"

        supplier?.let {
            binding.etName.setText(it.name)
            binding.etCountry.setText(it.country ?: "")
            binding.etPhone.setText(it.phone ?: "")
            binding.etEmail.setText(it.email ?: "")
            binding.etAddress.setText(it.address ?: "")
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun save() {
        val name    = binding.etName.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()
        val phone   = binding.etPhone.text.toString().trim()
        val email   = binding.etEmail.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()

        if (name.isEmpty()) { toast("Supplier Name is required"); return }

        // 🛠️ FIXED: Only email requires a strict non-null fallback string
        val request = SupplierRequest(
            name = name,
            country = if (country.isEmpty()) "Local" else country,
            phone = phone.ifEmpty { null },
            email = email.ifEmpty { "" }, // 💎 Passes empty string instead of null to prevent the mismatch error!
            address = address.ifEmpty { null }
        )

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            val result = if (supplier != null) {
                safeApiCall { RetrofitClient.instance.updateSupplier(supplier.id, request) }
            } else {
                safeApiCall { RetrofitClient.instance.createSupplier(request) }
            }
            binding.btnSave.isEnabled = true

            if (result is Resource.Success) {
                toast("Supplier catalog synchronized")
                onSuccess(); dismiss()
            } else if (result is Resource.Error) {
                toast(result.message)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}