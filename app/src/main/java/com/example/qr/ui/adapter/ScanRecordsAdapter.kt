package com.example.qr.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.qr.R
import com.example.qr.data.dao.ScanRecordDao
import com.example.qr.data.entity.ScanType
import com.example.qr.databinding.ItemScanRecordBinding
import java.text.SimpleDateFormat
import java.util.*

data class ParticipantScanInfo(
    val participantId: Long,
    val participantName: String,
    val lastEntryTime: Long?,
    val lastExitTime: Long?,
    val isCurrentlyInside: Boolean
)

class ScanRecordsAdapter : ListAdapter<ParticipantScanInfo, ScanRecordsAdapter.ViewHolder>(
    ScanRecordDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanRecordBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemScanRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ParticipantScanInfo) {
            binding.apply {
                tvScanParticipantName.text = item.participantName

                val timeFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

                // 입장 시간 표시
                if (item.lastEntryTime != null) {
                    tvEntryTime.text = timeFormat.format(Date(item.lastEntryTime))
                } else {
                    tvEntryTime.text = "-"
                }

                // 퇴장 시간 표시 (재입장한 경우 퇴장 시간 없음)
                if (item.isCurrentlyInside) {
                    tvExitTime.text = "-"
                } else if (item.lastExitTime != null) {
                    tvExitTime.text = timeFormat.format(Date(item.lastExitTime))
                } else {
                    tvExitTime.text = "-"
                }

                // 체류 시간 계산
                if (item.lastEntryTime != null) {
                    val endTime = if (item.isCurrentlyInside) {
                        System.currentTimeMillis()  // 현재 입장중이면 현재 시간까지
                    } else {
                        item.lastExitTime ?: System.currentTimeMillis()
                    }
                    val durationMillis = endTime - item.lastEntryTime
                    tvStayDuration.text = formatDuration(durationMillis)
                } else {
                    tvStayDuration.text = "-"
                }

                // 상태 표시
                if (item.isCurrentlyInside) {
                    tvScanType.text = "입장중"
                    tvScanType.setBackgroundColor(itemView.context.getColor(R.color.status_inside))
                } else {
                    tvScanType.text = "퇴장"
                    tvScanType.setBackgroundColor(itemView.context.getColor(R.color.status_exited))
                }
            }
        }

        private fun formatDuration(millis: Long): String {
            val hours = millis / (1000 * 60 * 60)
            val minutes = (millis % (1000 * 60 * 60)) / (1000 * 60)
            return if (hours > 0) {
                "${hours}시간 ${minutes}분"
            } else {
                "${minutes}분"
            }
        }
    }

    private class ScanRecordDiffCallback : DiffUtil.ItemCallback<ParticipantScanInfo>() {
        override fun areItemsTheSame(
            oldItem: ParticipantScanInfo,
            newItem: ParticipantScanInfo
        ): Boolean {
            return oldItem.participantId == newItem.participantId
        }

        override fun areContentsTheSame(
            oldItem: ParticipantScanInfo,
            newItem: ParticipantScanInfo
        ): Boolean {
            return oldItem == newItem
        }
    }
}