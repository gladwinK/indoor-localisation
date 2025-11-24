package com.example.indoor_localisation_again.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.indoor_localisation_again.model.Fingerprint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FingerprintAdapter : RecyclerView.Adapter<FingerprintAdapter.ViewHolder>() {
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    private var items: List<Fingerprint> = emptyList()

    fun submit(list: List<Fingerprint>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], dateFormat)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitle: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(item: Fingerprint, dateFormat: SimpleDateFormat) {
            title.text = "${item.locationName} - ${item.readings.size} APs"
            subtitle.text = "Captured ${dateFormat.format(Date(item.timestamp))}"
        }
    }
}
