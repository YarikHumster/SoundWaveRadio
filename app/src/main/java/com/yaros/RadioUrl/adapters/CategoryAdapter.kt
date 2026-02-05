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
import com.yaros.RadioUrl.core.APIInterface.data.Category

class CategoryAdapter(
    private val imageLoader: (ImageView, String) -> Unit,
    private val onItemClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view, imageLoader, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val imageLoader: (ImageView, String) -> Unit,
        private val onItemClick: (Category) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val categoryImage: ImageView = itemView.findViewById(R.id.categoryImage)
        private val categoryName: TextView = itemView.findViewById(R.id.categoryName)

        fun bind(category: Category) {
            categoryName.text = category.name
            imageLoader(categoryImage, category.image ?: "")

            itemView.setOnClickListener {
                onItemClick(category)
            }
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
        override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
            return oldItem == newItem
        }
    }
}
