package com.example.paddleocrapp.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.paddleocrapp.R
import com.example.paddleocrapp.model.PageData

/**
 * 分页结果适配器 - 用于 ViewPager2
 */
class PageAdapter : ListAdapter<PageData, PageAdapter.PageViewHolder>(PageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val tvPageNumber: TextView = itemView.findViewById(R.id.tvPageNumber)
        private val tvImageInfo: TextView = itemView.findViewById(R.id.tvImageInfo)
        private val tvTextContent: TextView = itemView.findViewById(R.id.tvTextContent)
        private val tvError: TextView = itemView.findViewById(R.id.tvError)

        fun bind(pageData: PageData) {
            // 加载图片
            Glide.with(itemView.context)
                .load(pageData.imageItem.uri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .into(imageView)

            // 设置文字
            tvPageNumber.text = pageData.getFormattedPageNumber()
            tvImageInfo.text = pageData.getImageInfo()

            if (pageData.errorMessage != null) {
                tvError.visibility = View.VISIBLE
                tvError.text = pageData.errorMessage
                tvTextContent.visibility = View.GONE
            } else {
                tvError.visibility = View.GONE
                tvTextContent.visibility = View.VISIBLE
                tvTextContent.text = pageData.textContent.ifEmpty { "暂无识别结果" }
            }
        }
    }

    class PageDiffCallback : DiffUtil.ItemCallback<PageData>() {
        override fun areItemsTheSame(oldItem: PageData, newItem: PageData): Boolean {
            return oldItem.pageNumber == newItem.pageNumber
        }

        override fun areContentsTheSame(oldItem: PageData, newItem: PageData): Boolean {
            return oldItem == newItem
        }
    }
}
