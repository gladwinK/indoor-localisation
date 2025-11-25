package com.example.indoor_localisation_again.ui

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.indoor_localisation_again.R
import com.example.indoor_localisation_again.model.Fingerprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FingerprintAdapter(
    private val onDelete: (Fingerprint) -> Unit
) : RecyclerView.Adapter<FingerprintAdapter.ViewHolder>() {
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    private var items: List<Fingerprint> = emptyList()

    fun submit(list: List<Fingerprint>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fingerprint, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], dateFormat) { fingerprint ->
            // Show confirmation dialog before deletion
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("Delete Fingerprint")
                .setMessage("Are you sure you want to delete the fingerprint for '${fingerprint.locationName}'?\nThis action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    onDelete(fingerprint)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.fingerprintTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.fingerprintSubtitle)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(item: Fingerprint, dateFormat: SimpleDateFormat, onDelete: (Fingerprint) -> Unit) {
            title.text = "${item.locationName} - ${item.readings.size} APs"
            subtitle.text = "Captured ${dateFormat.format(Date(item.timestamp))}"

            deleteButton.setOnClickListener {
                onDelete(item)
            }
        }
    }
}
