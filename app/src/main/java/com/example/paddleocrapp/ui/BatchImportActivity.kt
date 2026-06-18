package com.example.paddleocrapp.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.paddleocrapp.R
import com.example.paddleocrapp.adapter.ImageGridAdapter
import com.example.paddleocrapp.databinding.ActivityBatchImportBinding
import com.example.paddleocrapp.model.ImageItem
import com.example.paddleocrapp.model.SortMode
import com.example.paddleocrapp.utils.ImagePicker
import com.example.paddleocrapp.utils.PermissionHelper
import kotlinx.coroutines.launch

/**
 * 批量导入界面 - 详细图片管理
 */
class BatchImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchImportBinding
    private lateinit var imagePicker: ImagePicker
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var imageAdapter: ImageGridAdapter
    private val selectedImages = mutableListOf<ImageItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.batch_import)
        }

        permissionHelper = PermissionHelper(this)
        imagePicker = ImagePicker(this)

        setupRecyclerView()
        setupButtons()
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageGridAdapter(
            onItemClick = { imageItem ->
                toggleSelection(imageItem)
            },
            onItemLongClick = { imageItem ->
                showImageDetails(imageItem)
                true
            }
        )

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@BatchImportActivity, 3)
            adapter = imageAdapter
        }
    }

    private fun setupButtons() {
        binding.fabAddMore.setOnClickListener {
            checkPermissionAndPickImages()
        }

        binding.btnConfirm.setOnClickListener {
            if (selectedImages.isEmpty()) {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            returnSelectedImages()
        }

        binding.btnSortDate.setOnClickListener {
            sortImages(SortMode.BY_DATE_TAKEN)
        }

        binding.btnSortName.setOnClickListener {
            sortImages(SortMode.BY_NAME)
        }
    }

    private fun checkPermissionAndPickImages() {
        permissionHelper.requestStoragePermissions { granted ->
            if (granted) {
                imagePicker.pickImages { images ->
                    lifecycleScope.launch {
                        selectedImages.addAll(images)
                        updateImageList()
                    }
                }
            } else {
                Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSelection(imageItem: ImageItem) {
        // 实现选择/取消选择逻辑
    }

    private fun sortImages(sortMode: SortMode) {
        val sorted = ImagePicker.sortImages(selectedImages, sortMode)
        selectedImages.clear()
        selectedImages.addAll(sorted)
        updateImageList()
    }

    private fun updateImageList() {
        imageAdapter.submitList(selectedImages.toList())
        binding.tvCount.text = "已选择 ${selectedImages.size} 张"

        if (selectedImages.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showImageDetails(imageItem: ImageItem) {
        val message = """
            文件名: ${imageItem.name}
            尺寸: ${imageItem.width}x${imageItem.height}
            大小: ${formatFileSize(imageItem.size)}
            拍摄时间: ${formatDate(imageItem.dateTaken)}
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("图片详情")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
        }
    }

    private fun formatDate(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }

    private fun returnSelectedImages() {
        // 返回选中的图片到主界面
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
