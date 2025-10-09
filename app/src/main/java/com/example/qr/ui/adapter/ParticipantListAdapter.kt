package com.example.qr.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.qr.data.entity.Participant
import com.example.qr.databinding.ItemParticipantBinding
import java.text.SimpleDateFormat
import java.util.*

data class ParticipantWithSelection(
    val participant: Participant,
    val isSelected: Boolean = false,
    val scanCount: Int = 0,
    val lastScanTime: Long? = null,
    val isCurrentlyInside: Boolean = false
)

class ParticipantListAdapter(
    private val onItemClick: (ParticipantWithSelection) -> Unit,
    private val onSelectionChange: (ParticipantWithSelection, Boolean) -> Unit,
    private val onDetailClick: (ParticipantWithSelection) -> Unit
) : ListAdapter<ParticipantWithSelection, ParticipantListAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemParticipantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ParticipantWithSelection) {
            binding.apply {
                // Checkbox - Remove listener first to prevent triggering during state change
                cbSelect.setOnCheckedChangeListener(null)
                cbSelect.isChecked = item.isSelected
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onSelectionChange(item, isChecked)
                }

                // Participant Info
                tvParticipantName.text = item.participant.fullName
                tvPhoneNumber.text = item.participant.phoneNumber
                tvLicenseNumber.text = "라이센스: ${item.participant.licenseNo}"

                // Status Badge
                if (item.isCurrentlyInside) {
                    tvStatusBadge.text = "입장중"
                    tvStatusBadge.setBackgroundColor(
                        binding.root.context.getColor(com.example.qr.R.color.status_inside)
                    )
                } else if (item.scanCount > 0) {
                    tvStatusBadge.text = "퇴장"
                    tvStatusBadge.setBackgroundColor(
                        binding.root.context.getColor(com.example.qr.R.color.status_exited)
                    )
                } else {
                    tvStatusBadge.text = "미입장"
                    tvStatusBadge.setBackgroundColor(
                        binding.root.context.getColor(android.R.color.darker_gray)
                    )
                }

                // Scan Info
                tvScanCount.text = "스캔: ${item.scanCount}회"
                tvLastScan.text = if (item.lastScanTime != null) {
                    "최근: ${dateFormat.format(Date(item.lastScanTime))}"
                } else {
                    "최근: -"
                }

                // Click Listeners
                root.setOnClickListener {
                    onItemClick(item)
                }

                btnDetail.setOnClickListener {
                    onDetailClick(item)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ParticipantWithSelection>() {
        override fun areItemsTheSame(
            oldItem: ParticipantWithSelection,
            newItem: ParticipantWithSelection
        ): Boolean {
            return oldItem.participant.id == newItem.participant.id
        }

        override fun areContentsTheSame(
            oldItem: ParticipantWithSelection,
            newItem: ParticipantWithSelection
        ): Boolean {
            return oldItem == newItem
        }
    }
}
