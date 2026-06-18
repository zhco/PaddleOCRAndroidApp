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
import com.example.paddleocrapp.model.ImageItem

/**
 * 图片网格适配器
 */
class ImageGridAdapter(
    private val onItemClick: (ImageItem) -> Unit,
    private val onItemLongClick: (ImageItem) -> Boolean
) : ListAdapter<ImageItem, ImageGridAdapter.ImageViewHolder>(ImageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_grid, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val tvOrder: TextView = itemView.findViewById(R.id.tvOrder)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)

        fun bind(imageItem: ImageItem) {
            // 加载图片缩略图
            Glide.with(itemView.context)
                .load(imageItem.uri)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .into(imageView)

            // 显示顺序
            tvOrder.text = "${adapterPosition + 1}"
            tvName.text = imageItem.name

            itemView.setOnClickListener { onItemClick(imageItem) }
            itemView.setOnLongClickListener { onItemLongClick(imageItem) }
        }
    }

    class ImageDiffCallback : DiffUtil.ItemCallback<ImageItem>() {
        override fun areItemsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
            return oldItem == newItem
        }
    }
}
