package com.scms.app.ui.inventory

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
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
import com.scms.app.databinding.FragmentInventoryBinding
import com.scms.app.databinding.ItemProductBinding
import com.scms.app.databinding.DialogProductFormBinding
import com.scms.app.models.Product
import com.scms.app.models.ProductRequest
import com.scms.app.models.Supplier
import com.scms.app.utils.*
import kotlinx.coroutines.launch
import androidx.lifecycle.MutableLiveData

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class InventoryViewModel : ViewModel() {
    val products = MutableLiveData<Resource<List<Product>>>()
    val deleteResult = MutableLiveData<Resource<Unit>>()

    private var allProducts = listOf<Product>()

    fun load(search: String? = null, lowStock: Boolean? = null) {
        viewModelScope.launch {
            products.value = Resource.Loading()
            val result = safeApiCall {
                RetrofitClient.instance.getProducts(search = search, lowStock = lowStock)
            }
            when (result) {
                is Resource.Success -> {
                    allProducts = result.data.data
                    products.value = Resource.Success(allProducts)
                }
                is Resource.Error -> products.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun delete(id: Int) {
        viewModelScope.launch {
            val result = safeApiCall { RetrofitClient.instance.deleteProduct(id) }
            deleteResult.value = when (result) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Error   -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}

// ─── ADAPTER ─────────────────────────────────────────────────────────────────

class ProductAdapter(
    private var items: List<Product>,
    private val onEdit: (Product) -> Unit,
    private val onDelete: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.VH>() {

    inner class VH(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.binding.apply {
            tvProductName.text = p.name
            tvSku.text = p.sku
            tvCategory.text = p.category
            tvStock.text = "${p.stockQty} ${p.unit ?: "pcs"}"
            tvPrice.text = formatCurrency(p.unitPrice)
            tvSupplier.text = p.supplier?.name ?: "—"

            if (p.isLowStock) {
                tvStockStatus.text = "Low Stock"
                tvStockStatus.setBackgroundResource(com.scms.app.R.drawable.bg_badge_red)
                tvStock.setTextColor(root.context.getColor(android.R.color.holo_red_dark))
            } else {
                tvStockStatus.text = "In Stock"
                tvStockStatus.setBackgroundResource(com.scms.app.R.drawable.bg_badge_green)
                tvStock.setTextColor(root.context.getColor(android.R.color.holo_green_dark))
            }

            btnEdit.setOnClickListener { onEdit(p) }
            btnDelete.setOnClickListener { onDelete(p) }
        }
    }

    fun update(newItems: List<Product>) {
        items = newItems
        notifyDataSetChanged()
    }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: InventoryViewModel by viewModels()
    private lateinit var adapter: ProductAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductAdapter(
            emptyList(),
            onEdit   = { product -> ProductFormDialog(product) { viewModel.load() }.show(childFragmentManager, "edit") },
            onDelete = { product -> confirmDelete(product) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        binding.fabAdd.setOnClickListener {
            ProductFormDialog(null) { viewModel.load() }.show(childFragmentManager, "add")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.load(search = s?.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.chipAll.setOnClickListener { viewModel.load() }
        binding.chipLowStock.setOnClickListener { viewModel.load(lowStock = true) }

        viewModel.products.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progressBar.show()
                    binding.recyclerView.hide()
                }
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
            if (state is Resource.Success) {
                toast("Product deleted")
                viewModel.load()
            } else if (state is Resource.Error) {
                toast(state.message)
            }
        }

        viewModel.load()
    }

    private fun confirmDelete(product: Product) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Product")
            .setMessage("Delete \"${product.name}\" from inventory?")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(product.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── FORM DIALOG ──────────────────────────────────────────────────────────────

class ProductFormDialog(
    private val product: Product?,
    private val onSuccess: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogProductFormBinding? = null
    private val binding get() = _binding!!
    private var suppliers = listOf<Supplier>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogProductFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = if (product != null) "Edit Product" else "Add Product"

        product?.let {
            binding.etName.setText(it.name)
            binding.etSku.setText(it.sku)
            binding.etCategory.setText(it.category)
            binding.etStock.setText(it.stockQty.toString())
            binding.etReorder.setText(it.reorderPoint.toString())
            binding.etUnitPrice.setText(it.unitPrice.toString())
            binding.etUnit.setText(it.unit ?: "pcs")
            binding.etDescription.setText(it.description ?: "")
        }

        loadSuppliers()

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun loadSuppliers() {
        lifecycleScope.launch {
            val result = safeApiCall { RetrofitClient.instance.getSuppliers() }
            if (result is Resource.Success) {
                suppliers = result.data.data
                val names = suppliers.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                binding.spinnerSupplier.adapter = adapter

                product?.supplier?.let { sup ->
                    val idx = suppliers.indexOfFirst { it.id == sup.id }
                    if (idx >= 0) binding.spinnerSupplier.setSelection(idx)
                }
            }
        }
    }

    private fun save() {
        val name     = binding.etName.text.toString().trim()
        val sku      = binding.etSku.text.toString().trim()
        val category = binding.etCategory.text.toString().trim()
        val stock    = binding.etStock.text.toString().toIntOrNull() ?: 0
        val reorder  = binding.etReorder.text.toString().toIntOrNull() ?: 0
        val price    = binding.etUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
        val unit     = binding.etUnit.text.toString().trim().ifEmpty { "pcs" }
        val desc     = binding.etDescription.text.toString().trim()

        if (name.isEmpty() || sku.isEmpty() || category.isEmpty()) {
            toast("Please fill in all required fields")
            return
        }

        if (suppliers.isEmpty()) { toast("Please select a supplier"); return }
        val supplierId = suppliers[binding.spinnerSupplier.selectedItemPosition].id

        val request = ProductRequest(
            name          = name,
            sku           = sku,
            category      = category,
            supplierId    = supplierId,
            stockQty      = stock,
            reorderPoint  = reorder,
            unitPrice     = price,
            unit          = unit,
            description   = desc.ifEmpty { null }
        )

        lifecycleScope.launch {
            binding.btnSave.isEnabled = false
            val result = if (product != null) {
                safeApiCall { RetrofitClient.instance.updateProduct(product.id, request) }
            } else {
                safeApiCall { RetrofitClient.instance.createProduct(request) }
            }

            binding.btnSave.isEnabled = true

            when (result) {
                is Resource.Success -> {
                    toast(if (product != null) "Product updated" else "Product added")
                    onSuccess()
                    dismiss()
                }
                is Resource.Error -> toast(result.message)
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}