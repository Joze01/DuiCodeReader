package com.applaudostudios.dui_reader

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.ml.vision.FirebaseVision
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.log.logcat
import io.fotoapparat.parameter.Zoom
import io.fotoapparat.result.transformer.scaled
import io.fotoapparat.selector.*
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.io.File
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage


class MainActivity : AppCompatActivity() {

    private val permissionsDelegate = PermissionsDelegate(this)

    private var permissionsGranted: Boolean = false
    private var activeCamera: Camera = Camera.Back

    private lateinit var fotoapparat: Fotoapparat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionsGranted = permissionsDelegate.hasCameraPermission()

        if (permissionsGranted) {
            cameraView.visibility = View.VISIBLE
        } else {
            permissionsDelegate.requestCameraPermission()
        }

        fotoapparat = Fotoapparat(
            context = this,
            view = cameraView,
            focusView = focusView,
            logger = logcat(),
            lensPosition = activeCamera.lensPosition,
            cameraConfiguration = activeCamera.configuration,
            cameraErrorCallback = { Log.e("Error", "Camera error: ", it) }
        )

        captureButton.setOnClickListener{
            val photoResult = fotoapparat
                .autoFocus()
                .takePicture()

            photoResult
                .saveToFile(
                    File(
                        getExternalFilesDir("photos"),
                        "photo.jpg"
                    )
                )

            photoResult
                .toBitmap(scaled(scaleFactor = 0.25f))
                .whenAvailable { photo ->
                    photo
                        ?.let {

                            val options = FirebaseVisionBarcodeDetectorOptions.Builder()
                                .setBarcodeFormats(
                                    FirebaseVisionBarcode.FORMAT_ALL_FORMATS
                                )
                                .build()

                            val image = FirebaseVisionImage.fromBitmap(it.bitmap)
                            val detector = FirebaseVision.getInstance()
                                .getVisionBarcodeDetector(options)

                            detector.detectInImage(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                    toast(barcode.rawValue.toString())
                                    }

                                }
                                .addOnFailureListener {
                                    toast("Error")
                                }


                        }
                        ?: Log.e("ERRPR", "Couldn't capture photo.")
                }
        }

        //capture onClick takePicture()
        //switchCamera onClick changeCamera()
        //torchSwitch onCheckedChanged toggleFlash()
    }



     fun takePicture() = {

    }


    override fun onStart() {
        super.onStart()
        if (permissionsGranted) {
            fotoapparat.start()
        }
    }

    override fun onStop() {
        super.onStop()
        if (permissionsGranted) {
            fotoapparat.stop()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionsDelegate.resultGranted(requestCode, permissions, grantResults)) {
            permissionsGranted = true
            fotoapparat.start()
            cameraView.visibility = View.VISIBLE
        }
    }

}


private sealed class Camera(
    val lensPosition: LensPositionSelector,
    val configuration: CameraConfiguration
) {

    object Back : Camera(
        lensPosition = back(),
        configuration = CameraConfiguration(
            previewResolution = firstAvailable(
                wideRatio(highestResolution()),
                standardRatio(highestResolution())
            ),
            previewFpsRange = highestFps(),
            flashMode = off(),
            focusMode = firstAvailable(
                continuousFocusPicture(),
                autoFocus()
            )
        )
    )


}
