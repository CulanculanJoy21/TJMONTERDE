package com.scms.app.ui.suppliers

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
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
    val deleteResult = MutableLiveData<Resource<Unit>>()

    fun load(search: String? = null) {
        viewModelScope.launch {
            suppliers.value = Resource.Loading()
            val result = safeApiCall { RetrofitClient.instance.getSuppliers(search = search) }
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
            deleteResult.value = when (result) {
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
    private val onEdit: (Supplier) -> Unit,
    private val onDelete: (Supplier) -> Unit
) : RecyclerView.Adapter<SupplierAdapter.VH>() {

    inner class VH(val binding: ItemSupplierBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSupplierBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.binding.apply {
            tvName.text         = s.name
            tvCountry.text      = s.country ?: "—"
            tvEmail.text        = s.email
            tvPhone.text        = s.phone ?: "—"
            tvActiveOrders.text = "${s.activeOrders ?: 0} active orders"
            tvRating.text       = "★ ${s.rating}"

            btnEdit.setOnClickListener   { onEdit(s) }
            btnDelete.setOnClickListener { onDelete(s) }
        }
    }

    fun update(newItems: List<Supplier>) {
        items = newItems
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

        adapter = SupplierAdapter(
            emptyList(),
            onEdit   = { supplier -> SupplierFormDialog(supplier) { viewModel.load() }.show(childFragmentManager, "edit_supplier") },
            onDelete = { supplier -> confirmDelete(supplier) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        binding.fabAdd.setOnClickListener {
            SupplierFormDialog(null) { viewModel.load() }.show(childFragmentManager, "add_supplier")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.load(search = s?.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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
                is Resource.Error -> {
                    binding.progressBar.hide()
                    binding.swipeRefresh.isRefreshing = false
                    toast(state.message)
                }
            }
        }

        viewModel.deleteResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Supplier removed"); viewModel.load() }
            else if (state is Resource.Error) toast(state.message)
        }

        viewModel.load()
    }

    private fun confirmDelete(supplier: Supplier) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Supplier")
            .setMessage("Remove \"${supplier.name}\" from the system?")
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

        binding.tvTitle.text = if (supplier != null) "Edit Supplier" else "Add Supplier"

        supplier?.let {
            binding.etName.setText(it.name)
            binding.etEmail.setText(it.email)
            binding.etPhone.setText(it.phone ?: "")
            binding.etCountry.setText(it.country ?: "")
            binding.etAddress.setText(it.address ?: "")
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun save() {
        val name    = binding.etName.text.toString().trim()
        val email   = binding.etEmail.text.toString().trim()
        val phone   = binding.etPhone.text.toString().trim()
        val country = binding.etCountry.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()

        if (name.isEmpty() || email.isEmpty()) {
            toast("Name and email are required")
            return
        }

        val request = SupplierRequest(
            name    = name,
            email   = email,
            phone   = phone.ifEmpty { null },
            country = country.ifEmpty { null },
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
            when (result) {
                is Resource.Success -> { toast(if (supplier != null) "Supplier updated" else "Supplier added"); onSuccess(); dismiss() }
                is Resource.Error   -> toast(result.message)
                else -> {}
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}