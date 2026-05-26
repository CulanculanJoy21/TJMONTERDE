package com.scms.app.ui.notifications

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentNotificationsBinding
import com.scms.app.databinding.ItemNotificationBinding
import com.scms.app.models.Notification
import com.scms.app.utils.*
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// ─── VIEW MODEL ───────────────────────────────────────────────────────────────

class NotificationsViewModel : ViewModel() {
    val notifications = MutableLiveData<Resource<List<Notification>>>()
    val markResult    = MutableLiveData<Resource<Unit>>()

    fun load() {
        viewModelScope.launch {
            notifications.value = Resource.Loading()
            val result = safeApiCall { RetrofitClient.instance.getNotifications() }
            when (result) {
                is Resource.Success -> notifications.value = Resource.Success(result.data.data)
                is Resource.Error   -> notifications.value = Resource.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun markRead(id: Int) {
        viewModelScope.launch {
            val result = safeApiCall { RetrofitClient.instance.markNotificationRead(id) }
            markResult.value = when (result) {
                is Resource.Success -> Resource.Success(Unit)
                is Resource.Error   -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Loading()
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            safeApiCall { RetrofitClient.instance.markAllNotificationsRead() }
            load()
        }
    }
}

// ─── ADAPTER ─────────────────────────────────────────────────────────────────

class NotificationAdapter(
    private var items: List<Notification>,
    private val onMarkRead: (Notification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.VH>() {

    inner class VH(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val n = items[position]
        holder.binding.apply {
            tvTitle.text   = n.title
            tvMessage.text = n.message
            tvTime.text    = n.createdAt.take(10)

            // 🛠️ FIXED: Uses native structural flags instead of custom extension calls
            if (!n.isRead) {
                viewUnread.visibility = View.VISIBLE
                root.alpha = 1.0f
            } else {
                viewUnread.visibility = View.GONE
                // Gives read items a clean, soft faded layout style without changing card background structures
                root.alpha = 0.65f
            }

            root.setOnClickListener {
                if (!n.isRead) onMarkRead(n)
            }
        }
    }

    fun update(newItems: List<Notification>) {
        items = newItems
        notifyDataSetChanged()
    }
}

// ─── FRAGMENT ─────────────────────────────────────────────────────────────────

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter(emptyList()) { n ->
            viewModel.markRead(n.id)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        binding.btnMarkAllRead.setOnClickListener {
            viewModel.markAllRead()
            toast("All notifications marked as read")
        }

        viewModel.notifications.observe(viewLifecycleOwner) { state ->
            when (state) {
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

                    val unread = state.data.count { !it.isRead }
                    binding.tvUnreadCount.text = "$unread unread updates"
                }
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    toast(state.message)
                }
            }
        }

        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}