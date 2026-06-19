package com.example.paddleocrapp.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.example.paddleocrapp.R
import com.example.paddleocrapp.adapter.PageAdapter
import com.example.paddleocrapp.databinding.ActivityTextResultBinding
import com.example.paddleocrapp.model.ImageItem
import com.example.paddleocrapp.model.PageData
import com.example.paddleocrapp.utils.FileExporter
import com.example.paddleocrapp.viewmodel.TextResultViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * 识别结果界面 - 分页展示文字
 */
class TextResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextResultBinding
    private val viewModel: TextResultViewModel by viewModels()
    private lateinit var pageAdapter: PageAdapter

    private var currentPages: List<PageData> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.text_result)
        }

        val images = intent.getParcelableArrayListExtra<ImageItem>("images")
        if (images.isNullOrEmpty()) {
            Toast.makeText(this, "没有图片数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViewPager()
        setupButtons()
        observeViewModel()

        viewModel.startRecognition(images)
    }

    private fun setupViewPager() {
        pageAdapter = PageAdapter()
        binding.viewPager.apply {
            adapter = pageAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updatePageIndicator(position)
                }
            })
        }
    }

    private fun setupButtons() {
        binding.btnPrev.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current > 0) {
                binding.viewPager.currentItem = current - 1
            }
        }

        binding.btnNext.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current < pageAdapter.itemCount - 1) {
                binding.viewPager.currentItem = current + 1
            }
        }

        binding.fabCopy.setOnClickListener {
            val currentPage = currentPages.getOrNull(binding.viewPager.currentItem)
            currentPage?.let { page ->
                FileExporter.copyToClipboard(this, page.textContent)
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recognitionState.collect { state ->
                    when (state) {
                        is com.example.paddleocrapp.model.RecognitionState.Idle -> {
                            showLoading(false)
                        }
                        is com.example.paddleocrapp.model.RecognitionState.Loading -> {
                            showLoading(true)
                            binding.tvProgress.text = "正在初始化..."
                        }
                        is com.example.paddleocrapp.model.RecognitionState.Processing -> {
                            showLoading(true)
                            binding.tvProgress.text = "正在识别: ${state.current}/${state.total}"
                            binding.progressBar.apply {
                                max = state.total
                                progress = state.current
                            }
                        }
                        is com.example.paddleocrapp.model.RecognitionState.Success -> {
                            showLoading(false)
                            currentPages = state.pages
                            pageAdapter.submitList(state.pages)
                            updatePageIndicator(0)
                            binding.tvTotalPages.text = getString(R.string.total_pages, state.pages.size)
                        }
                        is com.example.paddleocrapp.model.RecognitionState.Error -> {
                            showLoading(false)
                            MaterialAlertDialogBuilder(this@TextResultActivity)
                                .setTitle("识别失败")
                                .setMessage(state.message)
                                .setPositiveButton("确定") { _, _ -> finish() }
                                .setCancelable(false)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingContainer.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentContainer.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updatePageIndicator(position: Int) {
        if (currentPages.isEmpty()) return
        val page = currentPages[position]
        binding.tvPageIndicator.text = page.getFormattedPageNumber()

        binding.btnPrev.isEnabled = position > 0
        binding.btnNext.isEnabled = position < currentPages.size - 1
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_result, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save -> {
                saveResult(withPageHeader = true)
                true
            }
            R.id.action_save_plain -> {
                saveResult(withPageHeader = false)
                true
            }
            R.id.action_share -> {
                shareResult()
                true
            }
            R.id.action_copy_all -> {
                copyAllText()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveResult(withPageHeader: Boolean) {
        if (currentPages.isEmpty()) return
        lifecycleScope.launch {
            val uri = FileExporter.exportToText(this@TextResultActivity, currentPages, withPageHeader)
            if (uri != null) {
                val msg = if (withPageHeader) "已保存到下载文件夹（含页码）" else "已保存到下载文件夹（纯文本）"
                Toast.makeText(this@TextResultActivity, msg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@TextResultActivity, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareResult() {
        if (currentPages.isEmpty()) return
        FileExporter.shareText(this, currentPages)
    }

    private fun copyAllText() {
        if (currentPages.isEmpty()) return
        FileExporter.copyAllToClipboard(this, currentPages)
        Toast.makeText(this, "已复制全部文字", Toast.LENGTH_SHORT).show()
    }
}
