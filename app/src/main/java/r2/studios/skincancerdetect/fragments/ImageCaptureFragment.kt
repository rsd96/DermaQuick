package r2.studios.skincancerdetect.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.ImageButton
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import kotlinx.android.synthetic.main.image_capture_fragment.*
import r2.studios.skincancerdetect.R
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.Math.*
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ImageCaptureFragment : Fragment() {

    val TAG = "ImageCaptureFragment"


    companion object {
        fun newInstance() = ImageCaptureFragment()
        private const val TAG = "MainFragment"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Milliseconds used for UI animations */
        const val ANIMATION_FAST_MILLIS = 50L
        const val ANIMATION_SLOW_MILLIS = 100L

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)
    }

    private lateinit var viewModel: ImageCaptureViewModel
    val previewConfig = Preview.Builder()


    private lateinit var mainExecutor: Executor

    private lateinit var container: ConstraintLayout

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var outputDirectory: File


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainExecutor = ContextCompat.getMainExecutor(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(R.layout.image_capture_fragment, container, false)
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(ImageCaptureViewModel::class.java)
        // TODO: Use the ViewModel
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN

        container = view as ConstraintLayout
        // Determine the output directory
        outputDirectory = getOutputDirectory(requireContext())
        Log.d(TAG, outputDirectory.toString())
        cameraView.post {
            // Keep track of the display in which this view is attached
            displayId = cameraView.display.displayId
            updateCameraUi()
            bindCameraUseCases()
            setFocusListener()
        }
    }

    /** Use external media if it is available, our app's file directory otherwise */
    fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    private fun setFocusListener() {
        cameraView.afterMeasured {
            val factory: MeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                cameraView.width.toFloat(), cameraView.height.toFloat())
            val centerWidth = cameraView.width.toFloat() / 2
            val centerHeight = cameraView.height.toFloat() / 2
            val autoFocusPoint = factory.createPoint(centerWidth, centerHeight)
            try {
                camera?.cameraControl?.startFocusAndMetering(
                    FocusMeteringAction.Builder(
                        autoFocusPoint,
                        FocusMeteringAction.FLAG_AF
                    ).apply {
                        //auto-focus every 1 seconds
                        setAutoCancelDuration(1, TimeUnit.SECONDS)
                    }.build()
                )
            } catch (e: CameraInfoUnavailableException) {
                Log.d("ERROR", "cannot access camera", e)
            }
        }
    }

    inline fun View.afterMeasured(crossinline block: () -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            }
        })
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { cameraView.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = cameraView.display.rotation

        // Bind the CameraProvider to the LifeCycleOwner
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build()

            // Default PreviewSurfaceProvider
            preview?.previewSurfaceProvider = cameraView.previewSurfaceProvider

            // ImageCapture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                // We request aspect ratio but no resolution to match preview config, but letting
                // CameraX optimize for whatever specific resolution best fits requested capture mode
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()
            
            // Must unbind the use-cases before rebinding them.
            cameraProvider.unbindAll()

            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, mainExecutor)
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }


    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.


        if (!PermissionsFragment.hasPermissions(requireContext())) {
            view?.findNavController()?.navigate(
                R.id.action_imageCaptureFragment_to_permissionsFragment)
        }
    }

    override fun onDetach() {
        super.onDetach()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }



    // listener called after image captured is saved
    val imageSavedListener = object: ImageCapture.OnImageSavedCallback {

        override fun onImageSaved(file: File) {
            Log.d(TAG, "Image saved !")
        }

        override fun onError(imageCaptureError: Int, message: String, cause: Throwable?) {
            Log.d(TAG, "Image not saved! :(")
        }

    }


    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {

        // Remove previous UI if any
        container.findViewById<ConstraintLayout>(R.id.camera_ui_container)?.let {
            container.removeView(it)
        }

        // Inflate a new view containing all UI for controlling the camera
        val controls = View.inflate(requireContext(), R.layout.camera_overlay_layout, container)

        // Listener for button used to capture photo
        controls.findViewById<ImageButton>(R.id.btnCameraCapture).setOnClickListener {

            // Get a stable reference of the modifiable image capture use case
            imageCapture?.let { imageCapture ->


                // Create output file to hold the image
                val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

                // Setup image capture metadata
                val metadata = ImageCapture.Metadata().apply {
                    // Mirror image when using the front camera
                    isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                }

                // Setup image capture listener which is triggered after photo has been taken
//                imageCapture.takePicture(photoFile, metadata, mainExecutor, imageSavedListener)
                imageCapture.takePicture(mainExecutor, object:
                    ImageCapture.OnImageCapturedCallback() {



                    override fun onCaptureSuccess(image: ImageProxy) {
                        Log.d(TAG, "FORMAT : ${image.format}")

                        Log.d(TAG, "INFO :  ${image.imageInfo}")

                        val bitmap = image.toBitmap()
//                        val stream = ByteArrayOutputStream()
//                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
//                        val byteArray = stream.toByteArray()
//
//                        val bundle = Bundle()
//                        bundle.putByteArray(Const.CLASSIFY_IMAGE_KEY, byteArray)

                        val action = ImageCaptureFragmentDirections
                            .actionImageCaptureFragmentToClassificationFragment(bitmap)


                        Navigation.findNavController(requireActivity(), R.id.fragmentContainer)
                            .navigate(action)

                        super.onCaptureSuccess(image)
                    }

                    override fun onError(
                        imageCaptureError: Int,
                        message: String,
                        cause: Throwable?
                    ) {
                        super.onError(imageCaptureError, message, cause)
                    }


                })

                // We can only change the foreground Drawable using API level 23+ API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    // Display flash animation to indicate that photo was captured
                    container.postDelayed({
                        container.foreground = ColorDrawable(Color.WHITE)
                        container.postDelayed(
                            { container.foreground = null }, ANIMATION_FAST_MILLIS
                        )
                    }, ANIMATION_SLOW_MILLIS)
                }
            }
        }
    }

    fun ImageProxy.toBitmap(): Bitmap {
//        val yBuffer = planes[0].buffer // Y
//        val uBuffer = planes[1].buffer // U
//        val vBuffer = planes[2].buffer // V
//
//        val ySize = yBuffer.remaining()
//        val uSize = uBuffer.remaining()
//        val vSize = vBuffer.remaining()
//
//        val nv21 = ByteArray(ySize + uSize + vSize)
//
//        //U and V are swapped
//        yBuffer.get(nv21, 0, ySize)
//        vBuffer.get(nv21, ySize, vSize)
//        uBuffer.get(nv21, ySize + vSize, uSize)
//
//        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
//        val out = ByteArrayOutputStream()
//        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
//        val imageBytes = out.toByteArray()

        val buffer = planes[0].buffer

        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val metrics = resources.displayMetrics
        val square_width = (metrics.density * 512).toInt()
//        val out = ByteArrayOutputStream()
        val srcBmp =  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)

        var dstBmp = Bitmap.createBitmap(
            srcBmp,
            (srcBmp.width /2) - (square_width/2),
            (srcBmp.height/2) - (square_width/2),
            square_width,
            square_width
        )

        dstBmp = Bitmap.createScaledBitmap(dstBmp, 224, 224, false)

        return dstBmp
//        return BitmapFactory.decodeByteArray(this.image, 0, imageBytes.size)
    }
}
