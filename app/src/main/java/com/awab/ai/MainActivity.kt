package com.awab.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var requestButton: Button
    private val permissionList = mutableListOf<String>()
    private var isRequesting = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingBatches = mutableListOf<List<String>>()
    private var currentBatchIndex = 0

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            val permissionName = it.key.substringAfterLast(".")
            val isGranted = it.value
            logStatus("${if (isGranted) "✓" else "✗"} $permissionName")
        }
        
        // Process next batch after a short delay
        handler.postDelayed({
            processNextBatch()
        }, 1500)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(32, 32, 32, 32)
        }

        statusTextView = TextView(this).apply {
            text = "مرحباً! اضغط على الزر لطلب الأذونات"
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }

        requestButton = Button(this).apply {
            text = "طلب الأذونات (دفعات صغيرة)"
            setOnClickListener {
                if (!isRequesting) {
                    requestAllPermissionsInBatches()
                } else {
                    Toast.makeText(this@MainActivity, "جاري طلب الأذونات...", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val accessibilityButton = Button(this).apply {
            text = "فتح إعدادات إمكانية الوصول"
            setOnClickListener {
                openAccessibilitySettings()
            }
        }

        val specialButton = Button(this).apply {
            text = "طلب الأذونات الخاصة"
            setOnClickListener {
                requestSpecialPermissions()
            }
        }

        layout.addView(requestButton)
        layout.addView(accessibilityButton)
        layout.addView(specialButton)
        layout.addView(statusTextView)
        scrollView.addView(layout)
        setContentView(scrollView)

        setupPermissionsList()
        updatePermissionStatus()
    }

    private fun setupPermissionsList() {
        permissionList.clear()
        
        // Calendar
        permissionList.add(Manifest.permission.READ_CALENDAR)
        permissionList.add(Manifest.permission.WRITE_CALENDAR)
        
        // Camera
        permissionList.add(Manifest.permission.CAMERA)
        
        // Contacts
        permissionList.add(Manifest.permission.READ_CONTACTS)
        permissionList.add(Manifest.permission.WRITE_CONTACTS)
        permissionList.add(Manifest.permission.GET_ACCOUNTS)
        
        // Location
        permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionList.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        
        // Microphone
        permissionList.add(Manifest.permission.RECORD_AUDIO)
        
        // Phone
        permissionList.add(Manifest.permission.READ_PHONE_STATE)
        permissionList.add(Manifest.permission.CALL_PHONE)
        permissionList.add(Manifest.permission.READ_CALL_LOG)
        permissionList.add(Manifest.permission.WRITE_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissionList.add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        
        // Sensors
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            permissionList.add(Manifest.permission.BODY_SENSORS)
        }
        
        // SMS
        permissionList.add(Manifest.permission.SEND_SMS)
        permissionList.add(Manifest.permission.RECEIVE_SMS)
        permissionList.add(Manifest.permission.READ_SMS)
        
        // Storage/Media
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionList.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissionList.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionList.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionList.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            permissionList.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        
        // Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        
        // Nearby Devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Background location is requested separately later
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionList.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }
    }

    private fun requestAllPermissionsInBatches() {
        isRequesting = true
        requestButton.isEnabled = false
        requestButton.text = "جاري الطلب..."
        
        logStatus("\n=== بدء طلب الأذونات ===\n")
        
        val permissionsToRequest = permissionList.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            Toast.makeText(this, "جميع الأذونات ممنوحة بالفعل!", Toast.LENGTH_SHORT).show()
            isRequesting = false
            requestButton.isEnabled = true
            requestButton.text = "طلب الأذونات (دفعات صغيرة)"
            updatePermissionStatus()
            return
        }

        // Separate background location
        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null
        
        val regularPermissions = if (backgroundLocation != null) {
            permissionsToRequest.filter { it != backgroundLocation }
        } else {
            permissionsToRequest
        }

        // Split into batches of 3 permissions
        pendingBatches = regularPermissions.chunked(3).toMutableList()
        
        // Add background location as last batch if needed
        if (backgroundLocation != null && permissionsToRequest.contains(backgroundLocation)) {
            pendingBatches.add(listOf(backgroundLocation))
        }
        
        currentBatchIndex = 0
        logStatus("إجمالي ${permissionsToRequest.size} إذن في ${pendingBatches.size} دفعة\n")
        
        processNextBatch()
    }

    private fun processNextBatch() {
        if (currentBatchIndex >= pendingBatches.size) {
            // All batches processed
            isRequesting = false
            requestButton.isEnabled = true
            requestButton.text = "طلب الأذونات (دفعات صغيرة)"
            logStatus("\n✅ انتهى طلب جميع الأذونات!\n")
            updatePermissionStatus()
            return
        }

        val batch = pendingBatches[currentBatchIndex]
        currentBatchIndex++
        
        logStatus("\n--- دفعة $currentBatchIndex/${pendingBatches.size} ---")
        batch.forEach { 
            logStatus("طلب: ${it.substringAfterLast(".")}")
        }
        
        requestPermissionsLauncher.launch(batch.toTypedArray())
    }

    private fun requestSpecialPermissions() {
        AlertDialog.Builder(this)
            .setTitle("الأذونات الخاصة")
            .setItems(arrayOf(
                "رسم فوق التطبيقات الأخرى",
                "تعديل إعدادات النظام",
                "إدارة جميع الملفات",
                "تثبيت الحزم",
                "تجاهل تحسين البطارية"
            )) { _, which ->
                when (which) {
                    0 -> requestOverlayPermission()
                    1 -> requestWriteSettingsPermission()
                    2 -> requestManageStoragePermission()
                    3 -> requestInstallPackagesPermission()
                    4 -> requestBatteryOptimizationPermission()
                }
            }
            .show()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "الإذن ممنوح بالفعل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "الإذن ممنوح بالفعل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "غير متوفر في هذا الإصدار", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestInstallPackagesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "الإذن ممنوح بالفعل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "قم بتفعيل خدمة إمكانية الوصول", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في فتح الإعدادات", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePermissionStatus() {
        val sb = StringBuilder()
        var granted = 0
        var denied = 0
        
        permissionList.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED
            if (isGranted) granted++ else denied++
        }
        
        sb.append("الأذونات: ✓ $granted | ✗ $denied\n")
        sb.append("─".repeat(30))
        sb.append("\n\n")
        
        // Group permissions by category
        val categories = mapOf(
            "التقويم" to listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
            "الكاميرا" to listOf(Manifest.permission.CAMERA),
            "جهات الاتصال" to listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS, Manifest.permission.GET_ACCOUNTS),
            "الموقع" to listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            "الميكروفون" to listOf(Manifest.permission.RECORD_AUDIO),
            "الهاتف" to listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE),
            "الرسائل" to listOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        )
        
        categories.forEach { (category, perms) ->
            val categoryPerms = perms.filter { permissionList.contains(it) }
            if (categoryPerms.isNotEmpty()) {
                val grantedCount = categoryPerms.count { 
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
                }
                sb.append("$category: $grantedCount/${categoryPerms.size}\n")
            }
        }
        
        statusTextView.text = sb.toString()
    }

    private fun logStatus(message: String) {
        runOnUiThread {
            statusTextView.append("$message\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
