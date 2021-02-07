package r2.studios.skincancerdetect.fragments

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.android.synthetic.main.fragment_classification_layout.*
import org.intellij.lang.annotations.JdkConstants
import r2.studios.skincancerdetect.R
import r2.studios.skincancerdetect.tflite.Classifier
import r2.studios.skincancerdetect.tflite.Classifier.Device
import r2.studios.skincancerdetect.tflite.Classifier.Model
import r2.studios.skincancerdetect.tflite.TFLiteClassifier
import java.io.IOException


class ClassificationFragment : Fragment() {

    val args: ClassificationFragmentArgs by navArgs()

    private val TAG = "ClassificationFragment"


    private var image: Bitmap? = null

    private var tfLiteClassifier: TFLiteClassifier? = null

    private var classifier: Classifier? = null
    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var model = Model.FLOAT
    private val device = Device.CPU
    private val numThreads = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        image = args.image

        recreateClassifier(model, device, numThreads)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_classification_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Disable full screen
        activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        // set lesion image to imageview
        Glide.with(this).load(image).into(ivClassificationImage)
        classify()

     }

    @Synchronized
    protected fun runInBackground(r: Runnable?) {
        if (handler != null) {
            handler?.post(r)
        }
    }

    // run the image through classification model
    private fun classify() {
        if (classifier != null) {
            val startTime = SystemClock.uptimeMillis()
            val results: List<Classifier.Recognition> = classifier!!.recognizeImage(image, 90)
            showResults(results)
        }
    }

    // show the result to the user
    private fun showResults(results: List<Classifier.Recognition>) {
        Log.d(TAG, "Results : $results")

        Log.d(TAG, "RESULT : ${results[0].confidence}")

        // Get melanoma and benign percentages
        var melPercent = 0.0
        var benignPercent = 0.0
        results.forEach {
            if (it.title == "MEL")
                melPercent = (it.confidence * 100).toDouble()

            if (it.title == "NV")
                benignPercent = (it.confidence * 100).toDouble()
        }

        // show the higher classfiication
        val result = Math.max(melPercent, benignPercent)

        var classificationLabel = ""

        if (melPercent > benignPercent)
            classificationLabel = "Melanoma"
        else
            classificationLabel = "Benign"

        Log.d(TAG, "MEL : $melPercent")

        tvClassificationPercent.text = result.toInt().toString() + "%"
        tvClassificationLabel.text = classificationLabel
    }

    private fun recreateClassifier(
        model: Model,
        device: Device,
        numThreads: Int
    ) {
        if (classifier != null) {
            classifier?.close()
            classifier = null
        }
        try {

            classifier = Classifier.create(activity, model, device, numThreads)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    override fun onResume() {
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread?.start()
        handler = Handler(handlerThread?.looper)
    }

    override fun onDestroy() {
        super.onDestroy()

    }
}
