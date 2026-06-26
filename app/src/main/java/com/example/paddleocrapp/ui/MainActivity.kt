package com.example.paddleocrapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.paddleocrapp.R
import com.example.paddleocrapp.adapter.ImageGridAdapter
import com.example.paddleocrapp.databinding.ActivityMainBinding
import com.example.paddleocrapp.model.ImageItem
import com.example.paddleocrapp.model.PageData
import com.example.paddleocrapp.model.SortMode
import com.example.paddleocrapp.utils.FileExporter
import com.example.paddleocrapp.utils.ImagePicker
import com.example.paddleocrapp.utils.PermissionHelper
import com.example.paddleocrapp.viewmodel.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 主界面 - 批量导入和排序图片
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var imagePicker: ImagePicker
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var imageAdapter: ImageGridAdapter

    /**
     * 接收 TextResultActivity 返回的识别结果
     */
    private val recognitionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val updatedImages = result.data?.getParcelableArrayListExtra<ImageItem>("updated_images")
            if (!updatedImages.isNullOrEmpty()) {
                viewModel.updateRecognizedResults(updatedImages)
                val count = viewModel.getRecognizedCount()
                Toast.makeText(this, "已识别 $count 页，可导出全部结果", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionHelper = PermissionHelper(this)
        imagePicker = ImagePicker(this)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageGridAdapter(
            onItemClick = { imageItem ->
                // 预览图片
            },
            onItemLongClick = { imageItem ->
                showItemOptions(imageItem)
                true
            }
        )

        binding.recyclerViewImages.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = imageAdapter
        }

        // 拖拽排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                viewModel.moveItem(fromPosition, toPosition)
                imageAdapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewImages)
    }

    private fun setupButtons() {
        // 导入图片按钮
        binding.fabAddImages.setOnClickListener {
            checkPermissionAndPickImages()
        }

        // 开始识别按钮
        binding.btnStartRecognition.setOnClickListener {
            if (viewModel.selectedImages.value.isEmpty()) {
                Toast.makeText(this, R.string.no_images_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startRecognition()
        }

        // 排序按钮
        binding.btnSort.setOnClickListener {
            showSortOptions()
        }

        // 清空按钮
        binding.btnClear.setOnClickListener {
            showClearConfirmDialog()
        }

        // 导出全部结果按钮
        binding.btnExportAll.setOnClickListener {
            exportAllResults()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedImages.collect { images ->
                    imageAdapter.submitList(images.toList())
                    updateUIState(images)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    binding.btnStartRecognition.isEnabled = !isLoading
                }
            }
        }
    }

    private fun updateUIState(images: List<ImageItem>) {
        val count = images.size
        val recognizedCount = viewModel.getRecognizedCount()

        binding.tvImageCount.text = getString(R.string.image_count, count)
        binding.btnStartRecognition.isEnabled = count > 0

        // 控制导出按钮的可见性：有识别结果时显示
        binding.btnExportAll.visibility = if (recognizedCount > 0) View.VISIBLE else View.GONE

        // 更新导出按钮文字
        if (recognizedCount > 0) {
            binding.btnExportAll.text = "导出全部结果 ($recognizedCount 页)"
        }

        if (count == 0) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recyclerViewImages.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recyclerViewImages.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionAndPickImages() {
        permissionHelper.requestStoragePermissions { granted ->
            if (granted) {
                imagePicker.pickImages { images ->
                    viewModel.addImages(images)
                }
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startRecognition() {
        val intent = Intent(this, TextResultActivity::class.java).apply {
            putParcelableArrayListExtra("images", ArrayList(viewModel.selectedImages.value))
        }
        recognitionResultLauncher.launch(intent)
    }

    /**
     * 导出全部识别结果为一个 txt 文件
     */
    private fun exportAllResults() {
        val recognizedImages = viewModel.getRecognizedImagesInOrder()
        if (recognizedImages.isEmpty()) {
            Toast.makeText(this, "没有可导出的识别结果", Toast.LENGTH_SHORT).show()
            return
        }

        // 构建 PageData 列表
        val pages = recognizedImages.mapIndexed { index, imageItem ->
            PageData(
                pageNumber = index + 1,
                imageItem = imageItem,
                textContent = imageItem.recognizedText ?: ""
            )
        }

        lifecycleScope.launch {
            val uri = FileExporter.exportToText(this@MainActivity, pages, includePageHeader = true)
            if (uri != null) {
                Toast.makeText(
                    this@MainActivity,
                    "已保存到下载文件夹（共 ${pages.size} 页）",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@MainActivity, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSortOptions() {
        val options = arrayOf(
            getString(R.string.sort_by_time),
            getString(R.string.sort_by_name)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("排序方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.sortImages(SortMode.BY_DATE_TAKEN)
                    1 -> viewModel.sortImages(SortMode.BY_NAME)
                }
            }
            .show()
    }

    private fun showItemOptions(imageItem: ImageItem) {
        val options = arrayOf("上移", "下移", "删除")
        MaterialAlertDialogBuilder(this)
            .setTitle(imageItem.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.moveItemUp(imageItem)
                    1 -> viewModel.moveItemDown(imageItem)
                    2 -> viewModel.removeImage(imageItem)
                }
            }
            .show()
    }

    private fun showClearConfirmDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("确认清空")
            .setMessage("确定要清空所有已选择的图片吗？")
            .setPositiveButton(R.string.ok) { _, _ ->
                viewModel.clearImages()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
