// Caspar Rollo 21010371

package com.example.assignment2

import android.annotation.SuppressLint
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class FullImageActivity : AppCompatActivity() {

    private lateinit var fullImageView: ImageView
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    private var scaleFactor = 1.0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_image)

        fullImageView = findViewById(R.id.fullImageView)

        // Retrieve the image ID from the intent
        val imageId = intent.getLongExtra("imageId", -1)

        if (imageId != -1L) {
            // Load and display the full size image
            val fullSizeBitmap = loadFullSizeImage(imageId)
            if (fullSizeBitmap != null) {
                fullImageView.setImageBitmap(fullSizeBitmap)
            }
        }

        // Initialise gesture detectors
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        gestureDetector = GestureDetector(this, GestureListener())

        // Set an onTouchListener for the fullImageView to handle touch events
        fullImageView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    @SuppressLint("Range")
    private fun loadFullSizeImage(imageId: Long): Bitmap? {
        var fullSizeBitmap: Bitmap? = null
        try {
            val imageUri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageId.toString()
            )
            val cursor: Cursor? = contentResolver.query(imageUri, null, null, null, null)

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val imagePath =
                        c.getString(c.getColumnIndex(MediaStore.Images.Media.DATA))
                    val options = BitmapFactory.Options()
                    options.inSampleSize = 1 // No down sampling for full-size image
                    fullSizeBitmap = BitmapFactory.decodeFile(imagePath, options)

                    // Apply orientation correction
                    val orientation = getOrientation(imagePath)
                    fullSizeBitmap = rotateBitmap(fullSizeBitmap, orientation)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fullSizeBitmap
    }

    private fun getOrientation(imagePath: String): Int {
        try {
            val exif = ExifInterface(imagePath)
            return exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ExifInterface.ORIENTATION_UNDEFINED
    }

    private fun rotateBitmap(bitmap: Bitmap?, orientation: Int): Bitmap? {
        if (bitmap == null) {
            return null
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap // No rotation needed
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Gesture listener for pinch-to-zoom
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.1f, 10.0f)
            fullImageView.scaleX = scaleFactor
            fullImageView.scaleY = scaleFactor
            return true
        }
    }

    // Gesture listener for swipe-up to go back
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val deltaY = e2.y - e1.y
            if (deltaY < -200) {
                // Swipe up detected, go back to the main activity
                finish()
            }
            return super.onFling(e1, e2, velocityX, velocityY)
        }
    }

}
