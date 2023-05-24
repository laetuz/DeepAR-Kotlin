package com.neotica.deeparkotlin

import ai.deepar.ar.ARErrorType
import ai.deepar.ar.AREventListener
import ai.deepar.ar.CameraResolutionPreset
import ai.deepar.ar.DeepAR
import ai.deepar.ar.DeepARImageFormat
import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.text.format.DateFormat
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutionException

class MainActivity1 : AppCompatActivity(), SurfaceHolder.Callback, AREventListener {
    // Default camera lens value, change to CameraSelector.LENS_FACING_BACK to initialize with back camera
    private val defaultLensFacing = CameraSelector.LENS_FACING_FRONT
    private var surfaceProvider: ARSurfaceProvider? = null
    private var lensFacing = defaultLensFacing
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private lateinit var buffers: Array<ByteBuffer?>
    private var currentBuffer = 0
    private var buffersInitialized = false
    private var deepAR: DeepAR? = null
    private val currentMask = 0
    private var currentEffect = 0
    private val currentFilter = 0// if the device's natural orientation is portrait:

    /*    *//*
               get interface orientation from
               https://stackoverflow.com/questions/10380989/how-do-i-get-the-current-orientation-activityinfo-screen-orientation-of-an-a/10383164
            */
    private val screenOrientation: Int
        get() {
            val rotation = windowManager.defaultDisplay.rotation
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(dm)
            width = dm.widthPixels
            height = dm.heightPixels
            // if the device's natural orientation is portrait:
            val orientation: Int = if ((rotation == Surface.ROTATION_0
                        || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height
            ) {
                when (rotation) {
                    Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                when (rotation) {
                    Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
            return orientation
        }
    private var effects: ArrayList<String>? = null
    private var recording = false
    private var currentSwitchRecording = false
    private var width = 0
    private var height = 0
    private var videoFileName: File? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
                ),
                1
            )
        } else {
            // Permission has already been granted
            initialize()
        }
        super.onStart()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty()) {
            for (grantResult in grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    return  // no permission
                }
            }
            initialize()
        }
    }

    private fun initialize() {
        initializeDeepAR()
        initializeFilters()
        initalizeViews()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun initializeFilters() {
        effects = ArrayList()
        effects!!.add("none")
        effects!!.add("Fire_Effect.deepar")
        effects!!.add("kacamataa.deepar")
        effects!!.add("https://storage.googleapis.com/deepar-sample/kacamata.deepar")

        // Download and save the effect file from the URL
        /*GlobalScope.launch(Dispatchers.IO) {
            Log.d("neo-kaca", "initializeFilters: entering coroutine")
            val effectUrl = "https://storage.googleapis.com/deepar-sample/kacamata.deepar"
            val effectFileName = "kacamataa.deepar"

            val effectFile = File(getExternalFilesDir(null), effectFileName)
            val url = URL(effectUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(effectFile)
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.close()
                inputStream.close()

                // Add the local file path to the effects list
                Log.d("neo-kaca","Success saved.")
                effects!!.add(effectFile.absolutePath)
            }
        }*/

    }


    private fun initalizeViews() {
        val previousMask = findViewById<ImageButton>(R.id.previousMask)
        val nextMask = findViewById<ImageButton>(R.id.nextMask)
        val arView = findViewById<SurfaceView>(R.id.surface)
        arView.holder.addCallback(this)

        // Surface might already be initialized, so we force the call to onSurfaceChanged
        arView.visibility = View.GONE
        arView.visibility = View.VISIBLE
        val screenshotBtn = findViewById<ImageButton>(R.id.recordButton)
        screenshotBtn.setOnClickListener { deepAR!!.takeScreenshot() }
        val switchCamera = findViewById<ImageButton>(R.id.switchCamera)
        switchCamera.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            //unbind immediately to avoid mirrored frame.
            val cameraProvider: ProcessCameraProvider?
            try {
                cameraProvider = cameraProviderFuture!!.get()
                cameraProvider.unbindAll()
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            setupCamera()
        }
        val openActivity = findViewById<ImageButton>(R.id.openActivity)
        openActivity.setOnClickListener {
            val myIntent = Intent(this@MainActivity1, BasicActivity::class.java)
            this@MainActivity1.startActivity(myIntent)
        }
        val screenShotModeButton = findViewById<TextView>(R.id.screenshotModeButton)
        val recordModeBtn = findViewById<TextView>(R.id.recordModeButton)
        recordModeBtn.background.alpha = 0x00
        screenShotModeButton.background.alpha = 0xA0
        screenShotModeButton.setOnClickListener(View.OnClickListener {
            if (currentSwitchRecording) {
                if (recording) {
                    Toast.makeText(
                        applicationContext,
                        "Cannot switch to screenshots while recording!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@OnClickListener
                }
                recordModeBtn.background.alpha = 0x00
                screenShotModeButton.background.alpha = 0xA0
                screenshotBtn.setOnClickListener { deepAR!!.takeScreenshot() }
                currentSwitchRecording = !currentSwitchRecording
            }
        })
        recordModeBtn.setOnClickListener {
            if (!currentSwitchRecording) {
                recordModeBtn.background.alpha = 0xA0
                screenShotModeButton.background.alpha = 0x00
                screenshotBtn.setOnClickListener {
                    if (recording) {
                        deepAR!!.stopVideoRecording()
                        Toast.makeText(
                            applicationContext,
                            "Recording " + videoFileName!!.name + " saved.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        videoFileName = File(
                            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                            "video_" + SimpleDateFormat.getDateTimeInstance() + ".mp4"
                        )
                        deepAR!!.startVideoRecording(
                            videoFileName.toString(),
                            width / 2,
                            height / 2
                        )
                        Toast.makeText(applicationContext, "Recording started.", Toast.LENGTH_SHORT)
                            .show()
                    }
                    recording = !recording
                }
                currentSwitchRecording = !currentSwitchRecording
            }
        }
        previousMask.setOnClickListener { gotoPrevious() }
        nextMask.setOnClickListener { gotoNext() }
    }

    private fun initializeDeepAR() {
        deepAR = DeepAR(this)
        deepAR!!.setLicenseKey("11c23e742269852900d7f3fd88c4cbab206d5b1d84fa9c734ca255cfc74a3ae7b1f8c5083875d3f0")
        deepAR!!.initialize(this, this)
        setupCamera()
    }

    private fun setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener({
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                bindImageAnalysis(cameraProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        val cameraResolutionPreset = CameraResolutionPreset.P1920x1080
        val width: Int
        val height: Int
        val orientation = screenOrientation
        if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE || orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            width = cameraResolutionPreset.width
            height = cameraResolutionPreset.height
        } else {
            width = cameraResolutionPreset.height
            height = cameraResolutionPreset.width
        }
        val cameraResolution = Size(width, height)
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        if (useExternalCameraTexture) {
            val preview = Preview.Builder()
                .setTargetResolution(cameraResolution)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle((this as LifecycleOwner), cameraSelector, preview)
            if (surfaceProvider == null) {
                surfaceProvider = deepAR?.let { ARSurfaceProvider(this, it) }
            }
            preview.setSurfaceProvider(surfaceProvider)
            surfaceProvider!!.setMirror(lensFacing == CameraSelector.LENS_FACING_FRONT)
        } else {
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(cameraResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageAnalyzer)
            buffersInitialized = false
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle((this as LifecycleOwner), cameraSelector, imageAnalysis)
        }
    }

    private fun initializeBuffers(size: Int) {
        buffers = arrayOfNulls(NUMBER_OF_BUFFERS)
        for (i in 0 until NUMBER_OF_BUFFERS) {
            buffers[i] = ByteBuffer.allocateDirect(size)
            buffers[i]?.order(ByteOrder.nativeOrder())
            buffers[i]?.position(0)
        }
    }

    private val imageAnalyzer = ImageAnalysis.Analyzer { image ->
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        if (!buffersInitialized) {
            buffersInitialized = true
            initializeBuffers(ySize + uSize + vSize)
        }
        val byteData = ByteArray(ySize + uSize + vSize)
        val width = image.width
        val yStride = image.planes[0].rowStride
        val uStride = image.planes[1].rowStride
        val vStride = image.planes[2].rowStride
        var outputOffset = 0
        if (width == yStride) {
            yBuffer[byteData, outputOffset, ySize]
            outputOffset += ySize
        } else {
            var inputOffset = 0
            while (inputOffset < ySize) {
                yBuffer.position(inputOffset)
                yBuffer[byteData, outputOffset, yBuffer.remaining().coerceAtMost(width)]
                outputOffset += width
                inputOffset += yStride
            }
        }
        //U and V are swapped
        if (width == vStride) {
            vBuffer[byteData, outputOffset, vSize]
            outputOffset += vSize
        } else {
            var inputOffset = 0
            while (inputOffset < vSize) {
                vBuffer.position(inputOffset)
                vBuffer[byteData, outputOffset, vBuffer.remaining().coerceAtMost(width)]
                outputOffset += width
                inputOffset += vStride
            }
        }
        if (width == uStride) {
            uBuffer[byteData, outputOffset, uSize]
            outputOffset += uSize
        } else {
            var inputOffset = 0
            while (inputOffset < uSize) {
                uBuffer.position(inputOffset)
                uBuffer[byteData, outputOffset, uBuffer.remaining().coerceAtMost(width)]
                outputOffset += width
                inputOffset += uStride
            }
        }
        buffers[currentBuffer]!!.put(byteData)
        buffers[currentBuffer]!!.position(0)
        if (deepAR != null) {
            deepAR!!.receiveFrame(
                buffers[currentBuffer],
                image.width, image.height,
                image.imageInfo.rotationDegrees,
                lensFacing == CameraSelector.LENS_FACING_FRONT,
                DeepARImageFormat.YUV_420_888,
                image.planes[1].pixelStride
            )
        }
        currentBuffer = (currentBuffer + 1) % NUMBER_OF_BUFFERS
        image.close()
    }

    private fun getFilterPath(filterName: String): String? {
        return if (filterName == "none") {
            null
        } else "file:///android_asset/$filterName"
    }

    private fun gotoNext() {
        currentEffect = (currentEffect + 1) % effects!!.size
        deepAR!!.switchEffect("effect", getFilterPath(effects!![currentEffect]))
    }

    private fun gotoPrevious() {
        currentEffect = (currentEffect - 1 + effects!!.size) % effects!!.size
        deepAR!!.switchEffect("effect", getFilterPath(effects!![currentEffect]))
    }

    override fun onStop() {
        recording = false
        currentSwitchRecording = false
        val cameraProvider: ProcessCameraProvider?
        try {
            cameraProvider = cameraProviderFuture!!.get()
            cameraProvider.unbindAll()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (surfaceProvider != null) {
            surfaceProvider!!.stop()
            surfaceProvider = null
        }
        deepAR!!.release()
        deepAR = null
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (surfaceProvider != null) {
            surfaceProvider!!.stop()
        }
        if (deepAR == null) {
            return
        }
        deepAR!!.setAREventListener(null)
        deepAR!!.release()
        deepAR = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // If we are using on screen rendering we have to set surface view where DeepAR will render
        deepAR!!.setRenderSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (deepAR != null) {
            deepAR!!.setRenderSurface(null, 0, 0)
        }
    }

    override fun screenshotTaken(bitmap: Bitmap) {
        val now = DateFormat.format("yyyy_MM_dd_hh_mm_ss", Date())
        try {
            val imageFile =
                File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "image_$now.jpg")
            val outputStream = FileOutputStream(imageFile)
            val quality = 100
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.flush()
            outputStream.close()
            MediaScannerConnection.scanFile(
                this@MainActivity1,
                arrayOf(imageFile.toString()),
                null,
                null
            )
            Toast.makeText(
                this@MainActivity1,
                "Screenshot " + imageFile.name + " saved.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun videoRecordingStarted() {}
    override fun videoRecordingFinished() {}
    override fun videoRecordingFailed() {}
    override fun videoRecordingPrepared() {}
    override fun shutdownFinished() {}
    override fun initialized() {
        // Restore effect state after deepar release
        deepAR!!.switchEffect("effect", getFilterPath(effects!![currentEffect]))
    }

    override fun faceVisibilityChanged(b: Boolean) {}
    override fun imageVisibilityChanged(s: String, b: Boolean) {}
    override fun frameAvailable(image: Image) {}
    override fun error(arErrorType: ARErrorType, s: String) {}
    override fun effectSwitched(s: String) {}

    companion object {
        private const val NUMBER_OF_BUFFERS = 2
        private const val useExternalCameraTexture = true
    }
}