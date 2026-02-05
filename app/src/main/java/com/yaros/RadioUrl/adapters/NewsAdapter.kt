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
import com.yaros.RadioUrl.core.APIInterface.data.NewsItem

class NewsAdapter(
    private val imageLoader: (ImageView, String) -> Unit,
    private val onItemClick: (NewsItem) -> Unit
) : ListAdapter<NewsItem, NewsAdapter.ViewHolder>(NewsDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news, parent, false)
        return ViewHolder(view, imageLoader, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val imageLoader: (ImageView, String) -> Unit,
        private val onItemClick: (NewsItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val newsImage: ImageView = itemView.findViewById(R.id.newsImage)
        private val newsTitle: TextView = itemView.findViewById(R.id.newsTitle)
        private val newsDescription: TextView = itemView.findViewById(R.id.newsDescription)

        fun bind(newsItem: NewsItem) {
            newsTitle.text = newsItem.title
            newsDescription.text = newsItem.content
            imageLoader(newsImage, newsItem.image ?: "")

            itemView.setOnClickListener {
                onItemClick(newsItem)
            }
        }
    }

    private class NewsDiffCallback : DiffUtil.ItemCallback<NewsItem>() {
        override fun areItemsTheSame(oldItem: NewsItem, newItem: NewsItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NewsItem, newItem: NewsItem): Boolean {
            return oldItem == newItem
        }
    }
}
