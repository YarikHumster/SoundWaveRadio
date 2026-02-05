package com.yaros.RadioUrl.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.data.Station

class RecommendedStationAdapter(
    private val imageLoader: (ImageView, String) -> Unit,
    private val onItemClick: (Station) -> Unit
) : ListAdapter<Station, RecommendedStationAdapter.ViewHolder>(StationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recommended_station, parent, false)
        return ViewHolder(view, imageLoader, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val imageLoader: (ImageView, String) -> Unit,
        private val onItemClick: (Station) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val stationImage: ImageView = itemView.findViewById(R.id.stationImage)
        private val stationName: TextView = itemView.findViewById(R.id.stationName)

        fun bind(station: Station) {
            stationName.text = station.name
            imageLoader(stationImage, station.image ?: "")

            itemView.setOnClickListener {
                onItemClick(station)
            }
        }
    }

    private class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem == newItem
        }
    }
}
