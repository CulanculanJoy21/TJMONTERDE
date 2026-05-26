package com.scms.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.scms.app.api.RetrofitClient
import com.scms.app.databinding.FragmentProfileBinding
import com.scms.app.ui.login.LoginActivity
import com.scms.app.utils.SessionManager
import com.scms.app.utils.safeApiCall
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = SessionManager(requireContext())
        val user = session.user

        user?.let {
            binding.tvProfileName.text = it.name
            binding.tvProfileRole.text = it.role.lowercase().replaceFirstChar { char -> char.uppercase() }
            binding.tvProfileEmail.text = it.email ?: "No email set"

            if (it.name.isNotEmpty()) {
                binding.tvAvatarFallback.text = it.name.take(1).uppercase()
            }
        }

        binding.btnProfileLogout.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                safeApiCall { RetrofitClient.instance.logout() }
            }
            session.clear()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            activity?.finish()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}