package com.pothole.khadda.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pothole.khadda.databinding.ItemPotholeCardBinding
import com.pothole.khadda.model.PotholeEvent
import com.pothole.khadda.model.RepairStatus
import com.pothole.khadda.model.SeverityLevel
import android.net.Uri
import android.view.View
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PotholeAdapter(
    private val onItemClick: (PotholeEvent) -> Unit,
    private val onStatusChangeClick: ((PotholeEvent) -> Unit)? = null,
    private val onLongClick: ((PotholeEvent) -> Unit)? = null
) : ListAdapter<PotholeEvent, PotholeAdapter.PotholeViewHolder>(PotholeDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PotholeViewHolder {
        val binding = ItemPotholeCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PotholeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PotholeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PotholeViewHolder(private val binding: ItemPotholeCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(event: PotholeEvent) {
            binding.tvTimestamp.text = dateFormat.format(Date(event.timestamp))
            binding.tvAlgorithm.text = event.algorithm
            binding.tvImpact.text = String.format(Locale.US, "Impact: %.2f m/s²", event.zDiffValue)
            binding.tvLocation.text = String.format(
                Locale.US,
                "%.5f°, %.5f°",
                event.latitude,
                event.longitude
            )

            val context = binding.root.context
            val imageFile = File(context.filesDir, "pothole_${event.eventId}.jpg")
            if (imageFile.exists()) {
                binding.ivPothole.visibility = View.VISIBLE
                binding.ivPothole.setImageURI(Uri.fromFile(imageFile))
                
                binding.ivPothole.setOnClickListener {
                    val dialog = android.app.Dialog(context)
                    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                    
                    val imageView = android.widget.ImageView(context)
                    imageView.setImageURI(Uri.fromFile(imageFile))
                    imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    imageView.setOnClickListener { dialog.dismiss() }
                    
                    dialog.setContentView(
                        imageView, 
                        android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    
                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
                    dialog.window?.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    dialog.show()
                }
            } else {
                binding.ivPothole.visibility = View.GONE
                binding.ivPothole.setImageDrawable(null)
                binding.ivPothole.setOnClickListener(null)
            }

            // Severity styling
            when (event.severity) {
                SeverityLevel.HIGH -> {
                    binding.tvSeverityBadge.text = "HIGH"
                    binding.tvSeverityBadge.setBackgroundColor(Color.parseColor("#EF4444")) // Crimson
                }
                SeverityLevel.MEDIUM -> {
                    binding.tvSeverityBadge.text = "MEDIUM"
                    binding.tvSeverityBadge.setBackgroundColor(Color.parseColor("#F59E0B")) // Amber
                }
                SeverityLevel.LOW -> {
                    binding.tvSeverityBadge.text = "LOW"
                    binding.tvSeverityBadge.setBackgroundColor(Color.parseColor("#10B981")) // Emerald
                }
            }

            // Repair Status styling
            binding.tvStatusBadge.text = event.status.name.replace("_", " ")
            when (event.status) {
                RepairStatus.REPORTED -> binding.tvStatusBadge.setTextColor(Color.parseColor("#EF4444"))
                RepairStatus.UNDER_REVIEW -> binding.tvStatusBadge.setTextColor(Color.parseColor("#F59E0B"))
                RepairStatus.REPAIRED -> binding.tvStatusBadge.setTextColor(Color.parseColor("#10B981"))
            }

            binding.root.setOnClickListener {
                onItemClick(event)
            }
            
            binding.root.setOnLongClickListener {
                onLongClick?.invoke(event)
                true
            }

            binding.btnChangeStatus.setOnClickListener {
                onStatusChangeClick?.invoke(event)
            }
        }
    }

    class PotholeDiffCallback : DiffUtil.ItemCallback<PotholeEvent>() {
        override fun areItemsTheSame(oldItem: PotholeEvent, newItem: PotholeEvent): Boolean =
            oldItem.eventId == newItem.eventId

        override fun areContentsTheSame(oldItem: PotholeEvent, newItem: PotholeEvent): Boolean =
            oldItem == newItem
    }
}
