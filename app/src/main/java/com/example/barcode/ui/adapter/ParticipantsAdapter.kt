package com.example.barcode.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barcode.R
import com.example.barcode.data.entity.ParticipantWithScanInfo
import com.example.barcode.databinding.ItemParticipantBinding
import java.text.SimpleDateFormat
import java.util.*

class ParticipantsAdapter : ListAdapter<ParticipantWithScanInfo, ParticipantsAdapter.ViewHolder>(
    ParticipantDiffCallback()
) {

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

    class ViewHolder(private val binding: ItemParticipantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(participantInfo: ParticipantWithScanInfo) {
            binding.apply {
                tvParticipantName.text = participantInfo.participant.fullName
                tvLicenseNumber.text = participantInfo.participant.licenseNo

                val scanCount = participantInfo.totalScans
                tvScanCount.text = "스캔 횟수: ${scanCount}회"

                val lastScanText = participantInfo.lastScanTime?.let {
                    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "-"
                tvLastScan.text = "마지막: $lastScanText"

                // 상태 배지 설정
                tvStatusBadge.text = participantInfo.statusText
                val statusColor = when (participantInfo.statusText) {
                    "입장중" -> itemView.context.getColor(R.color.status_inside)
                    "퇴장완료" -> itemView.context.getColor(R.color.status_exited)
                    else -> itemView.context.getColor(R.color.status_waiting)
                }
                tvStatusBadge.setBackgroundColor(statusColor)
            }
        }
    }

    private class ParticipantDiffCallback : DiffUtil.ItemCallback<ParticipantWithScanInfo>() {
        override fun areItemsTheSame(
            oldItem: ParticipantWithScanInfo,
            newItem: ParticipantWithScanInfo
        ): Boolean {
            return oldItem.participant.id == newItem.participant.id
        }

        override fun areContentsTheSame(
            oldItem: ParticipantWithScanInfo,
            newItem: ParticipantWithScanInfo
        ): Boolean {
            return oldItem == newItem
        }
    }
}