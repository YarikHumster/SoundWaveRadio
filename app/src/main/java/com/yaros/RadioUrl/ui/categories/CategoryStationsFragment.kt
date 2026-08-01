package com.yaros.RadioUrl.ui.categories

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import androidx.annotation.OptIn
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.adapters.RadioStationAdapter
import com.yaros.RadioUrl.core.supabase.SupabaseApiRepository
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.core.collection.CollectionViewModel
import com.yaros.RadioUrl.data.Station as ApiStation
import com.yaros.RadioUrl.helpers.CollectionHelper
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.GregorianCalendar

class CategoryStationsFragment : Fragment() {

    private lateinit var toolbar: Toolbar
    private lateinit var stationsRecyclerView: RecyclerView
    private lateinit var stationAdapter: RadioStationAdapter
    private lateinit var progressBar: ProgressBar

    private val apiRepository by lazy { SupabaseApiRepository(requireContext()) }
    private val collectionViewModel: CollectionViewModel by activityViewModels()

    private var categoryId: Int = 0
    private var categoryName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            categoryId = it.getInt("categoryId", 0)
            categoryName = it.getString("categoryName", "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_category_stations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
        stationsRecyclerView = view.findViewById(R.id.stationsRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)

        toolbar.title = categoryName
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupStationsRecyclerView()
        loadStationsForCategory(categoryId)
    }

    private fun setupStationsRecyclerView() {
        stationAdapter = RadioStationAdapter(
            imageLoader = { imageView, url ->
                // TODO: Implement image loading with Glide or Picasso
            },
            onItemClick = { station ->
                // Handle item click if needed
            },
            onPlayClick = { station ->
                playStation(station)
            },
            onAddToCollectionClick = { station ->
                addStationToCollection(station)
            },
            onVolumeClick = { station: com.yaros.RadioUrl.core.Station ->
                // Need to find the ApiStation from the adapter's current list
                val currentList = stationAdapter.currentList
                val apiStation = currentList.find { it.name == station.name && it.url == station.getStreamUri() }
                apiStation?.let { showVolumeDialog(it) }
            }
        )
        stationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stationAdapter
        }
    }

    @OptIn(UnstableApi::class)
    private fun playStation(apiStation: ApiStation) {
        lifecycleScope.launch {
            try {
                // Start playback using PlayerService without adding to collection
                val playIntent = Intent(requireContext(), com.yaros.RadioUrl.PlayerService::class.java).apply {
                    action = Keys.ACTION_PLAY_STREAM
                    putExtra(Keys.EXTRA_STREAM_URI, apiStation.url)
                    putExtra(Keys.EXTRA_STATION_NAME, apiStation.name)
                    putExtra(Keys.EXTRA_STATION_IMAGE, apiStation.image ?: "")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(playIntent)
                } else {
                    requireContext().startService(playIntent)
                }

                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Воспроизведение \"${apiStation.name}\"",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Timber.e(e, "Error playing station")
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка воспроизведения станции",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadStationsForCategory(categoryId: Int) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = apiRepository.getCategoryDetail(categoryId)) {
                is SupabaseApiRepository.NetworkResult.Success -> {
                    progressBar.visibility = View.GONE
                    stationAdapter.submitList(result.data)
                }
                is SupabaseApiRepository.NetworkResult.Error -> {
                    progressBar.visibility = View.GONE
                    Timber.e("Error loading stations: ${result.message}")
                    Toast.makeText(
                        requireContext(),
                        "Ошибка загрузки станций: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun addStationToCollection(apiStation: ApiStation) {
        lifecycleScope.launch {
            try {
                // Convert API Station to Core Station
                val coreStation = convertApiStationToCoreStation(apiStation)

                // Get current collection
                val currentCollection = collectionViewModel.collectionLiveData.value ?: Collection()

                // Add station to collection
                CollectionHelper.addStation(
                    requireContext(),
                    currentCollection,
                    coreStation
                )

                // Показываем сообщение в главном потоке
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Станция \"${apiStation.name}\" добавлена в коллекцию",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Timber.e(e, "Error adding station to collection")
                requireActivity().runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Ошибка добавления станции",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showVolumeDialog(apiStation: ApiStation) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_volume_control, null)
        val stationNameText = dialogView.findViewById<TextView>(R.id.stationNameText)
        val volumeSlider = dialogView.findViewById<Slider>(R.id.volumeSlider)
        val volumeValue = dialogView.findViewById<TextView>(R.id.volumeValue)
        val resetButton = dialogView.findViewById<MaterialButton>(R.id.resetButton)
        val closeButton = dialogView.findViewById<MaterialButton>(R.id.closeButton)

        // Конвертируем API станцию в Core станцию для получения UUID
        val coreStation = convertApiStationToCoreStation(apiStation)
        val stationUuid = coreStation.uuid

        stationNameText.text = apiStation.name

        // Загружаем текущую громкость
        val currentVolume = com.yaros.RadioUrl.helpers.VolumeSettingsHelper.getVolumePercent(requireContext(), stationUuid)
        volumeSlider.value = currentVolume.toFloat()
        volumeValue.text = "$currentVolume%"

        // Устанавливаем цвет в зависимости от громкости
        updateVolumeColor(volumeValue, currentVolume)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        volumeSlider.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                val volumePercent = value.toInt()
                volumeValue.text = "$volumePercent%"
                com.yaros.RadioUrl.helpers.VolumeSettingsHelper.setVolumePercent(requireContext(), stationUuid, volumePercent)
                updateVolumeColor(volumeValue, volumePercent)
            }
        }

        resetButton.setOnClickListener {
            volumeSlider.value = 100f
            volumeValue.text = "100%"
            com.yaros.RadioUrl.helpers.VolumeSettingsHelper.setVolumePercent(requireContext(), stationUuid, 100)
            updateVolumeColor(volumeValue, 100)
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateVolumeColor(volumeValue: TextView, volumePercent: Int) {
        when {
            volumePercent < 30 -> volumeValue.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            volumePercent < 60 -> volumeValue.setTextColor(requireContext().getColor(android.R.color.holo_orange_light))
            else -> volumeValue.setTextColor(requireContext().getColor(android.R.color.holo_green_light))
        }
    }

    private fun convertApiStationToCoreStation(apiStation: ApiStation): Station {
        return Station(
            name = apiStation.name,
            streamUris = mutableListOf(apiStation.url),
            stream = 0,
            streamContent = "audio/mpeg", // Default content type
            image = apiStation.image ?: "",
            remoteImageLocation = apiStation.image ?: "",
            modificationDate = GregorianCalendar.getInstance().time,
            country = apiStation.country,
            language = apiStation.language
        )
    }
}
