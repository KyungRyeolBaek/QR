package com.example.qr.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qr.ViewModelFactory
import com.example.qr.databinding.FragmentParticipantsBinding
import com.example.qr.ui.adapter.ParticipantsAdapter
import com.example.qr.ui.main.MainViewModel

class ParticipantsFragment : Fragment() {

    private var _binding: FragmentParticipantsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ParticipantsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParticipantsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = ViewModelFactory(requireContext())
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupRecyclerView()
        setupSearch()
        observeViewModel()

        viewModel.loadAllParticipants()
    }

    private fun setupRecyclerView() {
        adapter = ParticipantsAdapter()
        binding.rvParticipants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ParticipantsFragment.adapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text.toString().trim()
            if (query.isEmpty()) {
                viewModel.loadAllParticipants()
            } else {
                viewModel.searchParticipants(query)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.participants.observe(viewLifecycleOwner) { participants ->
            if (participants.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvParticipants.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvParticipants.visibility = View.VISIBLE
                adapter.submitList(participants)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}