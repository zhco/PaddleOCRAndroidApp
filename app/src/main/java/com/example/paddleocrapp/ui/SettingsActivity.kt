package com.example.paddleocrapp.ui

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.paddleocrapp.R
import com.example.paddleocrapp.databinding.ActivitySettingsBinding

/**
 * 设置界面
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings)
        }

        setupSettings()
    }

    private fun setupSettings() {
        // 线程数设置
        binding.sliderThreadCount.value = 4f
        binding.sliderThreadCount.addOnChangeListener { _, value, _ ->
            // 保存设置
            saveSetting("thread_count", value.toInt())
        }

        // 检测阈值
        binding.sliderDetThreshold.value = 0.3f
        binding.sliderDetThreshold.addOnChangeListener { _, value, _ ->
            saveSetting("det_threshold", value)
        }

        // 识别阈值
        binding.sliderRecThreshold.value = 0.5f
        binding.sliderRecThreshold.addOnChangeListener { _, value, _ ->
            saveSetting("rec_threshold", value)
        }

        // GPU 加速
        binding.switchGpu.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("use_gpu", isChecked)
        }

        // 语言选择
        binding.chipGroupLanguage.setOnCheckedStateChangeListener { _, checkedIds ->
            val language = when (checkedIds.firstOrNull()) {
                R.id.chipChinese -> "ch"
                R.id.chipEnglish -> "en"
                R.id.chipMixed -> "ch_en"
                else -> "ch"
            }
            saveSetting("language", language)
        }
    }

    private fun saveSetting(key: String, value: Any) {
        val prefs = getSharedPreferences("ocr_settings", MODE_PRIVATE)
        when (value) {
            is Int -> prefs.edit().putInt(key, value).apply()
            is Float -> prefs.edit().putFloat(key, value).apply()
            is Boolean -> prefs.edit().putBoolean(key, value).apply()
            is String -> prefs.edit().putString(key, value).apply()
        }
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
