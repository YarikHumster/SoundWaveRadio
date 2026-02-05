package com.yaros.RadioUrl.adapters

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.slider.Slider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.data.Station

class RadioStationAdapter(
    private val imageLoader: ((ImageView, String) -> Unit)? = null,
    private val onItemClick: (Station) -> Unit,
    private val onPlayClick: ((Station) -> Unit)? = null,
    private val onAddToCollectionClick: ((Station) -> Unit)? = null,
    private val onVolumeClick: ((Station) -> Unit)? = null
) : ListAdapter<Station, RadioStationAdapter.StationViewHolder>(StationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_radio_station, parent, false)
        return StationViewHolder(view, imageLoader, onItemClick, onPlayClick, onAddToCollectionClick, onVolumeClick)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StationViewHolder(
        itemView: View,
        private val imageLoader: ((ImageView, String) -> Unit)?,
        private val onItemClick: (Station) -> Unit,
        private val onPlayClick: ((Station) -> Unit)?,
        private val onAddToCollectionClick: ((Station) -> Unit)?,
        private val onVolumeClick: ((Station) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {

        private val stationImage: ImageView = itemView.findViewById(R.id.stationImage)
        private val stationNameText: TextView = itemView.findViewById(R.id.stationNameText)
        private val stationFrequencyText: TextView = itemView.findViewById(R.id.stationFrequencyText)
        private val playIndicator: View = itemView.findViewById(R.id.playIndicator)
        private val playButton: ImageView = itemView.findViewById(R.id.playButton)
        private val addToCollectionButton: ImageView = itemView.findViewById(R.id.addToCollectionButton)
        private val volumeButton: ImageView = itemView.findViewById(R.id.volumeButton)

        fun bind(station: Station) {
            stationNameText.text = station.name
            stationFrequencyText.text = station.url

            // Load image if imageLoader is provided
            imageLoader?.invoke(stationImage, station.image ?: "")

            // Set click listener
            itemView.setOnClickListener {
                onItemClick(station)
            }

            // Play button click listener
            playButton.setOnClickListener {
                onPlayClick?.invoke(station)
            }

            // Add to collection button click listener
            addToCollectionButton.setOnClickListener {
                onAddToCollectionClick?.invoke(station)
            }

            // Volume button click listener
            volumeButton.setOnClickListener {
                onVolumeClick?.invoke(station)
            }

            // Show/hide play indicator based on playing state
            playIndicator.visibility = if (station.isPlaying) View.VISIBLE else View.GONE
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
