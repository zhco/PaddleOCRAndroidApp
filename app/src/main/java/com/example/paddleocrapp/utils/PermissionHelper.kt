package com.example.paddleocrapp.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 权限帮助类
 */
class PermissionHelper(private val activity: FragmentActivity) {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    /**
     * 权限请求 Launcher
     */
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            onPermissionResult?.invoke(allGranted)
        }

    /**
     * 检查并请求存储权限
     */
    fun requestStoragePermissions(callback: (Boolean) -> Unit) {
        onPermissionResult = callback
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (hasPermissions(permissions)) {
            callback(true)
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    /**
     * 检查并请求相机权限
     */
    fun requestCameraPermission(callback: (Boolean) -> Unit) {
        onPermissionResult = callback
        val permissions = arrayOf(Manifest.permission.CAMERA)

        if (hasPermissions(permissions)) {
            callback(true)
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    /**
     * 检查是否已授权
     */
    private fun hasPermissions(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 检查存储权限
     */
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }
}
