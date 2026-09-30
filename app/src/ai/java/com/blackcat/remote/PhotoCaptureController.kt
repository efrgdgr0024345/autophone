package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.atomic.AtomicBoolean

internal class PhotoCaptureController(
    private val activity: Activity,
    private val texture: TextureView,
    private val onPhoto: (ByteArray) -> Unit,
    private val onError: (String) -> Unit
) {
    private val manager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("blackcat-camera")
    private lateinit var handler: Handler
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var cameraId: String? = null
    private var previewSize = Size(1280, 720)
    private var jpegSize = Size(1280, 720)
    private val started = AtomicBoolean(false)

    fun start() {
        if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onError("Camera permission is required.")
            return
        }
        if (!started.compareAndSet(false, true)) return
        if (!thread.isAlive) thread.start()
        handler = Handler(thread.looper)
        try {
            chooseCamera()
        } catch (_: Exception) {
            started.set(false)
            onError("No usable camera was found.")
            return
        }
        if (texture.isAvailable) openCamera() else {
            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) = openCamera()
                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
            }
        }
    }

    private fun chooseCamera() {
        val ids = manager.cameraIdList
        if (ids.isEmpty()) throw IllegalStateException("no camera")
        val id = ids.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.first()
        cameraId = id
        val map = manager.getCameraCharacteristics(id).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpeg = map?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        jpegSize = jpeg.filter { it.width <= 1920 && it.height <= 1920 }
            .maxByOrNull { it.width * it.height } ?: jpeg.firstOrNull() ?: Size(1280, 720)
        val preview = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java).orEmpty()
        previewSize = preview.minByOrNull {
            kotlin.math.abs(it.width - jpegSize.width) + kotlin.math.abs(it.height - jpegSize.height)
        } ?: Size(1280, 720)
    }

    private fun openCamera() {
        val id = cameraId ?: return
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; createSession() }
                override fun onDisconnected(device: CameraDevice) {
                    device.close(); camera = null
                    activity.runOnUiThread { onError("Camera disconnected.") }
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close(); camera = null
                    activity.runOnUiThread { onError("Camera error ($error).") }
                }
            }, handler)
        } catch (_: SecurityException) {
            activity.runOnUiThread { onError("Camera permission was not available.") }
        } catch (_: Exception) {
            activity.runOnUiThread { onError("Could not open the camera.") }
        }
    }

    private fun createSession() {
        val device = camera ?: return
        val st = texture.surfaceTexture ?: return
        st.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(st)
        reader?.close()
        reader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    activity.runOnUiThread { onPhoto(bytes) }
                } finally { image.close() }
            }, handler)
        }
        val output = reader?.surface ?: return
        device.createCaptureSession(listOf(previewSurface, output), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(value: CameraCaptureSession) {
                session = value
                try {
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }
                    value.setRepeatingRequest(request.build(), null, handler)
                } catch (_: Exception) {
                    activity.runOnUiThread { onError("Could not start camera preview.") }
                }
            }
            override fun onConfigureFailed(value: CameraCaptureSession) {
                activity.runOnUiThread { onError("Could not configure camera preview.") }
            }
        }, handler)
    }

    fun capture() {
        val device = camera ?: return onError("Camera is not ready.")
        val output = reader?.surface ?: return onError("Camera is not ready.")
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(output)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation())
            }
            session?.capture(request.build(), null, handler)
        } catch (_: Exception) { onError("Could not take the photo.") }
    }

    @Suppress("DEPRECATION")
    private fun jpegOrientation(): Int {
        val degrees = when (activity.windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val sensor = cameraId?.let { manager.getCameraCharacteristics(it).get(CameraCharacteristics.SENSOR_ORIENTATION) } ?: 90
        return (sensor + degrees + 360) % 360
    }

    fun close() {
        started.set(false)
        try { session?.close() } catch (_: Exception) {}
        try { camera?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        session = null; camera = null; reader = null
        if (thread.isAlive) {
            thread.quitSafely()
            try { thread.join(700) } catch (_: InterruptedException) {}
        }
    }
}
