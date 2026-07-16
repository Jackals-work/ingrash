package work.jackals.ingrash

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // متغيرات لاستقبال الملفات
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    companion object {
        private const val TAG = "IngrashApp"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    // تسجيل اختيار الملفات من النظام
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val results: Array<Uri>? = when {
                    result.resultCode == RESULT_OK && result.data != null -> {
                        // الحالة العادية: اختيار ملف من المستكشف
                        result.data?.data?.let { arrayOf(it) }
                    }
                    result.resultCode == RESULT_OK && cameraPhotoUri != null -> {
                        // الحالة: التقاط صورة من الكاميرا
                        arrayOf(cameraPhotoUri!!)
                    }
                    else -> null
                }

                filePathCallback?.onReceiveValue(results)
                filePathCallback = null
                cameraPhotoUri = null

            } catch (e: Exception) {
                Log.e(TAG, "Error in file chooser: ${e.message}")
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                cameraPhotoUri = null
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        // إعدادات WebView
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }
        }

        // تعيين WebViewClient للتعامل مع التنقل
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Log.e(TAG, "WebView error: $description (Code: $errorCode)")
                Toast.makeText(
                    this@MainActivity,
                    "خطأ في تحميل الصفحة: $description",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // تعيين WebChromeClient للتعامل مع اختيار الملفات
        webView.webChromeClient = object : WebChromeClient() {

            // طريقة اختيار الملفات (للإصدارات الجديدة API 21+)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {

                Log.d(TAG, "onShowFileChooser called")

                // إلغاء أي عملية سابقة
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback

                try {
                    // إنشاء Intent لاختيار الملفات
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                                "application/json",
                                "text/csv",
                                "application/pdf",
                                "text/plain"
                            ))
                        }

                    // إضافة خيار الكاميرا
                    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    cameraIntent.resolveActivity(packageManager)?.let {
                        val photoFile = createImageFile()
                        cameraPhotoUri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            photoFile
                        )
                        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                        intent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    }

                    fileChooserLauncher.launch(intent)
                    return true

                } catch (e: Exception) {
                    Log.e(TAG, "Error showing file chooser: ${e.message}")
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = null
                    return false
                }
            }
        }

        // ربط JavaScript مع Kotlin
        webView.addJavascriptInterface(WebAppInterface(), "Android")

        // تحميل الملف الرئيسي
        webView.loadUrl("file:///android_asset/index.html")

        Log.d(TAG, "App initialized successfully")
    }

    // دالة لإنشاء ملف للكاميرا
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            cameraPhotoUri = Uri.fromFile(this)
        }
    }

    // واجهة الاتصال بين JavaScript و Android
    inner class WebAppInterface {

        @JavascriptInterface
        fun share(text: String) {
            try {
                Log.d(TAG, "Share called with text: $text")

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooserIntent = Intent.createChooser(shareIntent, "مشاركة الكرت")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(chooserIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Error sharing text: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "حدث خطأ أثناء المشاركة: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(TAG, "JavaScript log: $message")
        }

        @JavascriptInterface
        fun getAppVersion(): String {
            return try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("خروج")
                    .setMessage("هل تريد الخروج من التطبيق؟")
                    .setPositiveButton("نعم") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
        }

        // ====== دوال حفظ الملفات ======

        @JavascriptInterface
        fun saveFile(content: String, mimeType: String, filename: String) {
            try {
                Log.d(TAG, "Saving file: $filename, mimeType: $mimeType, size: ${content.length} bytes")

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "جاري حفظ الملف...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ استخدام MediaStore
                    saveFileWithMediaStore(content, mimeType, filename)
                } else {
                    // Android 9 والإصدارات الأقدم
                    saveFileLegacy(content, mimeType, filename)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving file: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "❌ خطأ في حفظ الملف: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // حفظ الملف باستخدام MediaStore (Android 10+)
        private fun saveFileWithMediaStore(content: String, mimeType: String, filename: String) {
            try {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Ingrash")
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                        outputStream.flush()
                    }

                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "✅ تم حفظ الملف في مجلد التحميلات/Ingrash",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    // عرض الملف
                    openFile(uri, mimeType)
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "❌ فشل حفظ الملف",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving with MediaStore: ${e.message}")
                throw e
            }
        }

        // حفظ الملف للأنظمة القديمة (Android 9-)
        private fun saveFileLegacy(content: String, mimeType: String, filename: String) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "Ingrash")

                if (!appDir.exists()) {
                    appDir.mkdirs()
                }

                val file = File(appDir, filename)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(content.toByteArray())
                    outputStream.flush()
                }

                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "✅ تم حفظ الملف في: ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                // عرض الملف
                val uri = Uri.fromFile(file)
                openFile(uri, mimeType)

            } catch (e: Exception) {
                Log.e(TAG, "Error saving legacy file: ${e.message}")
                throw e
            }
        }

        // دالة لفتح الملف
        private fun openFile(uri: Uri, mimeType: String) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(intent, "فتح الملف")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                startActivity(chooserIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Error opening file: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "تم حفظ الملف ولكن لا يمكن فتحه",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // دالة لمشاركة ملف
        @JavascriptInterface
        fun shareFile(fileUri: String) {
            try {
                val uri = Uri.parse(fileUri)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "مشاركة الملف"))
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing file: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "خطأ في مشاركة الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // دالة لاختيار ملف
        @JavascriptInterface
        fun chooseFile() {
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/json",
                        "text/csv",
                        "text/plain"
                    ))
                }
                fileChooserLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error choosing file: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "خطأ في اختيار الملف", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // التعامل مع ضغط زر الرجوع
    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("خروج")
                .setMessage("هل تريد الخروج من التطبيق؟")
                .setPositiveButton("نعم") { _, _ ->
                    super.onBackPressed()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // تنظيف الموارد
        filePathCallback = null
        cameraPhotoUri = null
        webView.destroy()
    }
}