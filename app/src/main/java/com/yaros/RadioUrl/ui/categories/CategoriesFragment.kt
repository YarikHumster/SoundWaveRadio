package com.yaros.RadioUrl.ui.categories

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.adapters.CategoryGridAdapter
import com.yaros.RadioUrl.core.supabase.SupabaseApiRepository
import com.yaros.RadioUrl.core.APIInterface.data.Category
import kotlinx.coroutines.launch
import timber.log.Timber

class CategoriesFragment : Fragment() {

    private lateinit var categoriesRecyclerView: RecyclerView
    private lateinit var categoryAdapter: CategoryGridAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var searchView: SearchView
    private var allCategories: List<Category> = emptyList()

    private val apiRepository by lazy { SupabaseApiRepository(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        searchView = view.findViewById(R.id.searchView)

        setupRecyclerView()
        setupSearchView()
        loadCategories()
    }

    private fun setupRecyclerView() {
        categoryAdapter = CategoryGridAdapter { category ->
            navigateToCategoryStations(category)
        }
        categoriesRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = categoryAdapter
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterCategories(newText ?: "")
                return true
            }
        })
    }

    private fun filterCategories(query: String) {
        val filteredList = if (query.isEmpty()) {
            allCategories
        } else {
            allCategories.filter { category ->
                category.name.contains(query, ignoreCase = true)
            }
        }
        categoryAdapter.submitList(filteredList)
    }

    private fun navigateToCategoryStations(category: Category) {
        // Навигация к списку станций категории
        val bundle = Bundle().apply {
            putInt("categoryId", category.id)
            putString("categoryName", category.name)
        }

        // Если используется Navigation Component
        try {
            findNavController().navigate(
                //R.id.action_navigation_categories_to_categoryStationsFragment,
                bundle
            )
        } catch (e: Exception) {
            // Если навигация не настроена, показываем Toast
            Toast.makeText(
                requireContext(),
                "Открытие категории: ${category.name}",
                Toast.LENGTH_SHORT
            ).show()
            Timber.e(e, "Navigation error")
        }
    }

    private fun loadCategories() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            when (val result = apiRepository.getCategories()) {
                is SupabaseApiRepository.NetworkResult.Success -> {
                    progressBar.visibility = View.GONE
                    showCategories(result.data)
                }
                is SupabaseApiRepository.NetworkResult.Error -> {
                    progressBar.visibility = View.GONE
                    showError(result.message)
                }
            }
        }
    }

    private fun showCategories(categories: List<Category>) {
        allCategories = categories
        categoryAdapter.submitList(categories)
    }

    private fun showError(message: String) {
        Snackbar.make(
            requireView(),
            "Ошибка загрузки категорий: $message",
            Snackbar.LENGTH_LONG
        ).show()

        Timber.e("Error loading categories: $message")
    }
}
