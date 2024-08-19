// Caspar Rollo 21010371

package com.example.assignment2

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class ImageAdapter(
    private val mContext: Context,
    cursor: Cursor?,
    private val gridItemWidth: Int,
    private val gridItemHeight: Int
) : BaseAdapter() {
    private val mImageDetailsList: MutableList<ImageDetails> = ArrayList()
    private val imageCache: LruCache<Long, Bitmap>
    private val executor: Executor = Executors.newFixedThreadPool(4) // Number of background threads
    private val handler = Handler(Looper.getMainLooper())

    init {
        // Initialise the image cache
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        imageCache = object : LruCache<Long, Bitmap>(cacheSize) {
            override fun sizeOf(key: Long, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }

        // Populate the image details list
        populateImageDetails(cursor)
    }

    override fun getCount(): Int {
        return mImageDetailsList.size
    }

    override fun getItem(position: Int): Any {
        return mImageDetailsList[position]
    }

    override fun getItemId(position: Int): Long {
        return mImageDetailsList[position].imageID
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
        var reusedView = convertView
        val holder: ViewHolder
        if (reusedView == null) {
            reusedView = LayoutInflater.from(mContext).inflate(R.layout.grid_item, parent, false)
            holder = ViewHolder()
            holder.imageView = reusedView.findViewById(R.id.grid_item_image)
            reusedView.tag = holder
        } else {
            holder = reusedView.tag as ViewHolder
        }

        // Retrieve image details
        val imageDetails = mImageDetailsList[position]

        // Load and crop the thumbnail asynchronously
        loadAndCropThumbnailAsync(imageDetails.imageID, holder.imageView)
        return reusedView
    }

    @SuppressLint("Range")
    private fun populateImageDetails(cursor: Cursor?) {
        mImageDetailsList.clear()
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val imageID = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID))

                // Extract content creation date
                val dateAdded = extractContentCreationDate(imageID)

                val imageDetails = ImageDetails(imageID, dateAdded)
                mImageDetailsList.add(imageDetails)
            } while (cursor.moveToNext())
        }

        // Sort the list by content added date (most recent first)
        mImageDetailsList.sortWith { o1, o2 ->
            o2.dateAdded.compareTo(o1.dateAdded)
        }
    }

    private fun loadAndCropThumbnailAsync(imageID: Long, imageView: ImageView?) {
        val cachedThumbnail = imageCache.get(imageID)
        if (cachedThumbnail != null) {
            imageView?.setImageBitmap(cachedThumbnail)
        } else {
            executor.execute {
                val thumbnail = loadAndCropThumbnail(imageID, gridItemWidth, gridItemHeight)
                if (thumbnail != null) {
                    // Cache the loaded thumbnail
                    imageCache.put(imageID, thumbnail)

                    // Update the UI on the main thread
                    handler.post {
                        imageView?.setImageBitmap(thumbnail)
                    }
                }
            }
        }
    }

    private fun loadAndCropThumbnail(imageID: Long, targetWidth: Int, targetHeight: Int): Bitmap? {
        var thumbnail: Bitmap? = null
        try {
            val imageFile = getImageFile(imageID)
            if (imageFile != null && imageFile.exists()) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(imageFile.absolutePath, options)

                // Get the orientation of the image from the Exif data
                val orientation = getOrientation(imageFile.absolutePath)

                // Calculate the inSampleSize to resize the image
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)

                // Decode the image with the calculated inSampleSize
                options.inJustDecodeBounds = false
                val decodedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (decodedBitmap != null) {
                    // Apply orientation correction if needed
                    val rotatedBitmap = applyOrientation(decodedBitmap, orientation)

                    // Crop the rotated bitmap to the desired size
                    thumbnail = ThumbnailUtils.extractThumbnail(rotatedBitmap, targetWidth, targetHeight)
                    decodedBitmap.recycle() // Recycle the original decoded bitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return thumbnail
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val imageWidth = options.outWidth
        val imageHeight = options.outHeight
        var inSampleSize = 1
        if (imageWidth > reqWidth || imageHeight > reqHeight) {
            val halfWidth = imageWidth / 2
            val halfHeight = imageHeight / 2
            while (halfWidth / inSampleSize >= reqWidth && halfHeight / inSampleSize >= reqHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun getOrientation(imagePath: String): Int {
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            val exif = ExifInterface(imagePath)
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return orientation
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap // No rotation needed
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getImageFile(imageID: Long): File? {
        val imageUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageID.toString())
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = mContext.contentResolver.query(imageUri, projection, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") val filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
            cursor.close()
            return File(filePath)
        }
        return null
    }


    @SuppressLint("Range")
    private fun extractContentCreationDate(imageID: Long): Date {
        var dateAdded: Long = 0
        val cursor = mContext.contentResolver.query(
            Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageID.toString()),
            arrayOf(MediaStore.Images.Media.DATE_ADDED),
            null,
            null,
            null
        )
        if (cursor != null && cursor.moveToFirst()) {
            dateAdded = cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED))
            cursor.close()
        }

        // Convert seconds to milliseconds
        dateAdded *= 1000
        return Date(dateAdded)
    }

    private class ViewHolder {
        var imageView: ImageView? = null
    }

    private data class ImageDetails(val imageID: Long, val dateAdded: Date)
}