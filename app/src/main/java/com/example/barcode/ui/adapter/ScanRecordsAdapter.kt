package com.example.barcode.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.barcode.R
import com.example.barcode.data.dao.ScanRecordDao
import com.example.barcode.databinding.ItemScanRecordBinding
import java.text.SimpleDateFormat
import java.util.*

class ScanRecordsAdapter : ListAdapter<ScanRecordDao.ScanRecordWithParticipant, ScanRecordsAdapter.ViewHolder>(
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

        fun bind(item: ScanRecordDao.ScanRecordWithParticipant) {
            binding.apply {
                tvScanParticipantName.text = item.participantName

                val timeFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                tvScanTime.text = timeFormat.format(Date(item.scanTime))

                val scanTypeText = if (item.scanType == com.example.barcode.data.entity.ScanType.ENTRY) "입장" else "퇴장"
                tvScanType.text = scanTypeText

                val scanTypeColor = if (item.scanType == com.example.barcode.data.entity.ScanType.ENTRY) {
                    itemView.context.getColor(R.color.status_inside)
                } else {
                    itemView.context.getColor(R.color.status_exited)
                }
                tvScanType.setBackgroundColor(scanTypeColor)
            }
        }
    }

    private class ScanRecordDiffCallback : DiffUtil.ItemCallback<ScanRecordDao.ScanRecordWithParticipant>() {
        override fun areItemsTheSame(
            oldItem: ScanRecordDao.ScanRecordWithParticipant,
            newItem: ScanRecordDao.ScanRecordWithParticipant
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: ScanRecordDao.ScanRecordWithParticipant,
            newItem: ScanRecordDao.ScanRecordWithParticipant
        ): Boolean {
            return oldItem == newItem
        }
    }
}