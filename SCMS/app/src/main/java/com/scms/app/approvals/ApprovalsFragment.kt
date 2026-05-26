package com.scms.app.ui.approvals

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentApprovalsBinding
import com.scms.app.databinding.ItemApprovalBinding
import com.scms.app.models.ApprovalRequestMobile
import com.scms.app.utils.*
import kotlinx.coroutines.launch
import com.google.gson.GsonBuilder

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class ApprovalsViewModel : ViewModel() {
    val requests = MutableLiveData<Resource<List<ApprovalRequestMobile>>>()
    val reviewResult = MutableLiveData<Resource<String>>()

    fun load() {
        viewModelScope.launch {
            requests.value = Resource.Loading()
            val result = safeApiCall { RetrofitClient.instance.getPendingApprovals() }
            requests.value = result
        }
    }

    fun submitReview(id: Int, status: String) {
        viewModelScope.launch {
            val result = safeApiCall {
                RetrofitClient.instance.reviewApproval(id, mapOf("status" to status, "review_note" to "Processed via Mobile App"))
            }
            when (result) {
                is Resource.Success -> reviewResult.value = Resource.Success(status)
                is Resource.Error -> reviewResult.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }
}

// ─── ADAPTER ─────────────────────────────────────────────────────────────────

class ApprovalsAdapter(
    private var items: List<ApprovalRequestMobile>,
    private val onAction: (ApprovalRequestMobile, String) -> Unit
) : RecyclerView.Adapter<ApprovalsAdapter.VH>() {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    inner class VH(val binding: ItemApprovalBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemApprovalBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.binding.apply {
            tvAction.text = r.actionType.uppercase()
            tvModule.text = r.modelType.replace("_", " ").replaceFirstChar { it.uppercase() }
            tvPayload.text = gson.toJson(r.payload)
            tvMeta.text = "By: ${r.user?.name ?: "Manager"} • ${r.createdAt.take(16).replace("T", " ")}"

            btnApprove.setOnClickListener { onAction(r, "approved") }
            btnReject.setOnClickListener { onAction(r, "rejected") }
        }
    }

    fun update(newItems: List<ApprovalRequestMobile>) { items = newItems; notifyDataSetChanged() }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class ApprovalsFragment : Fragment() {
    private var _binding: FragmentApprovalsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ApprovalsViewModel by viewModels()
    private lateinit var adapter: ApprovalsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentApprovalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ApprovalsAdapter(emptyList()) { req, status -> viewModel.submitReview(req.id, status) }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.requests.observe(viewLifecycleOwner) { state ->
            when (state) {
                // 🛠️ FIXED: Replaced old .show() / .hide() custom extension hooks with clear native visibility constants
                is Resource.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.recyclerView.visibility = View.VISIBLE
                    adapter.update(state.data)
                    binding.tvEmpty.visibility = if (state.data.isEmpty()) View.VISIBLE else View.GONE
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    toast(state.message)
                }
            }
        }

        viewModel.reviewResult.observe(viewLifecycleOwner) { state ->
            if (state is Resource.Success) { toast("Decision matrix recorded: ${state.data}"); viewModel.load() }
            else if (state is Resource.Error) toast(state.message)
        }
        viewModel.load()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}