package r2.studios.skincancerdetect

import android.app.Application
import android.content.Context
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

/**
 * Created by Mohamed Ramshad on 2020-02-02.
 */
class SkinCancerDetect: Application(), CameraXConfig.Provider {

    /** @returns Camera2 default configuration */
    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext()
    }
}