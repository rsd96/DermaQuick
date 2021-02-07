package r2.studios.skincancerdetect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

private const val IMMERSIVE_FLAG_TIMEOUT = 500L

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
//        if (savedInstanceState == null) {
//            supportFragmentManager.beginTransaction()
//                .replace(R.id.container, ImageCaptureFragment.newInstance())
//                .commitNow()
//        }
    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
//        container.postDelayed({
//            container.systemUiVisibility = FLAGS_FULLSCREEN
//        }, IMMERSIVE_FLAG_TIMEOUT)
    }

}
