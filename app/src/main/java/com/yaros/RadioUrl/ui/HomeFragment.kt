package com.yaros.RadioUrl.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.adapters.CategoryAdapter
import com.yaros.RadioUrl.adapters.NewsAdapter
import com.yaros.RadioUrl.adapters.RadioStationAdapter
import com.yaros.RadioUrl.adapters.RecommendedStationAdapter
import com.yaros.RadioUrl.core.supabase.SupabaseApiRepository
import com.yaros.RadioUrl.core.APIInterface.data.Category
import com.yaros.RadioUrl.core.APIInterface.data.NewsItem
import com.yaros.RadioUrl.data.Station
import kotlinx.coroutines.launch
import timber.log.Timber

class HomeFragment : Fragment() {

    private lateinit var recommendedStationsRecyclerView: RecyclerView
    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var newsRecyclerView: RecyclerView
    private lateinit var recentlyPlayedRecyclerView: RecyclerView

    private lateinit var recommendedStationAdapter: RecommendedStationAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var newsAdapter: NewsAdapter
    private lateinit var stationAdapter: RadioStationAdapter

    private val apiRepository by lazy { SupabaseApiRepository() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupAdapters()
        loadDataFromAPI()
    }

    private fun initViews(view: View) {
        recommendedStationsRecyclerView = view.findViewById(R.id.recommendedStationsRecyclerView)
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView)
        newsRecyclerView = view.findViewById(R.id.newsRecyclerView)
        recentlyPlayedRecyclerView = view.findViewById(R.id.recentlyPlayedRecyclerView)
    }

    private fun setupAdapters() {
        setupRecommendedStationsRecyclerView()
        setupCategoriesRecyclerView()
        setupNewsRecyclerView()
        setupRecentlyPlayedRecyclerView()
    }

    private fun loadDataFromAPI() {
        lifecycleScope.launch {
            fetchHomeData()
            fetchCategories()
            fetchRecentStations()
        }
    }

    private fun setupRecommendedStationsRecyclerView() {
        recommendedStationAdapter = RecommendedStationAdapter(
            imageLoader = { imageView: ImageView, url: String ->
                // TODO: Реализация загрузки изображения (Glide/Picasso)
            },
            onItemClick = { station: Station ->
                // TODO: Обработка клика
            }
        )
        recommendedStationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = recommendedStationAdapter
        }
    }

    private fun setupCategoriesRecyclerView() {
        categoryAdapter = CategoryAdapter(
            imageLoader = { imageView: ImageView, url: String ->
                // TODO: Реализация загрузки изображения
            },
            onItemClick = { category: Category ->
                // TODO: Обработка клика
            }
        )
        categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupNewsRecyclerView() {
        newsAdapter = NewsAdapter(
            imageLoader = { imageView: ImageView, url: String ->
                // TODO: Реализация загрузки изображения
            },
            onItemClick = { news: NewsItem ->
                // TODO: Обработка клика
            }
        )
        newsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }
    }

    private fun setupRecentlyPlayedRecyclerView() {
        stationAdapter = RadioStationAdapter(
            imageLoader = { imageView: ImageView, url: String ->
                // TODO: Реализация загрузки изображения
            },
            onItemClick = { station: Station ->
                // TODO: Обработка клика
            }
        )
        recentlyPlayedRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stationAdapter
        }
    }

    private suspend fun fetchHomeData() {
        when (val result = apiRepository.getHomeData()) {
            is SupabaseApiRepository.NetworkResult.Success -> {
                result.data.news?.let(newsAdapter::submitList)
                result.data.favorites?.let(recommendedStationAdapter::submitList)
            }
            is SupabaseApiRepository.NetworkResult.Error -> {
                Timber.e("Home data error: ${result.message}")
                // Показать состояние ошибки
            }
        }
    }

    private suspend fun fetchCategories() {
        when (val result = apiRepository.getCategories()) {
            is SupabaseApiRepository.NetworkResult.Success -> {
                categoryAdapter.submitList(result.data)
            }
            is SupabaseApiRepository.NetworkResult.Error -> {
                Timber.e("Categories error: ${result.message}")
            }
        }
    }

    private suspend fun fetchRecentStations() {
        when (val result = apiRepository.getRecentRadio()) {
            is SupabaseApiRepository.NetworkResult.Success -> {
                stationAdapter.submitList(result.data)
            }
            is SupabaseApiRepository.NetworkResult.Error -> {
                Timber.e("Recent stations error: ${result.message}")
            }
        }
    }
}
