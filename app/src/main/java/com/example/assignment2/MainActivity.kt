// Caspar Rollo 21010371

package com.example.assignment2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.AdapterView
import android.widget.GridView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var gridView: GridView
    private lateinit var imageAdapter: ImageAdapter
    private var gridItemWidth: Int = 0
    private var gridItemHeight: Int = 0
    private var numColumns: Int = 3 // Default to 3 columns for portrait mode

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, load and display images
            calculateGridItemSize()
            loadImages()
        } else {
            // Permission denied, show a dialog explaining the need for the permission.
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gridView = findViewById(R.id.gridView)

        // Determine the appropriate permission based on API level
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        // Check if the app has the appropriate permission
        if (checkPermission(permission)) {
            // Permission granted, load and display images
            calculateGridItemSize()
            loadImages()
        } else {
            requestPermission(permission)
        }

        // Set item click listener for opening full-size images
        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            openFullImage(position)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for permissions again when the app resumes
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkPermission(permission)) {
            // Permission granted, load and display images
            calculateGridItemSize()
            loadImages()
        }
    }

    private fun calculateGridItemSize() {
        // Calculate the width and height of each grid item based on screen orientation
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Adjust the number of columns based on the orientation
        numColumns = if (screenWidth > screenHeight) 4 else 3

        gridItemWidth = screenWidth / numColumns
        gridItemHeight = screenHeight / if (screenWidth > screenHeight) 3 else 5
    }

    private fun loadImages() {
        // Define the projection with image ID, orientation, width, height, and date added
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED
        )

        // Sort the images by date taken (most recent first)
        val sortOrder =
            "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        if (cursor != null) {
            // Use the dynamically calculated number of columns
            gridView.numColumns = numColumns

            imageAdapter = ImageAdapter(this, cursor, gridItemWidth, gridItemHeight)
            gridView.adapter = imageAdapter
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission(permission: String) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            // Show an explanation dialog before requesting permission
            showPermissionRationaleDialog()
        } else {
            // Request the permission directly
            permissionLauncher.launch(permission)
        }
    }

    // Show an explanation dialog before requesting permission
    private fun showPermissionRationaleDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Permission Required")
        alertDialogBuilder.setMessage("This app requires permission to access images.")

        if (Build.VERSION.SDK_INT >= 33) {
            alertDialogBuilder.setPositiveButton("OK") { _, _ ->
                requestPermission(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            alertDialogBuilder.setPositiveButton("OK") { _, _ ->
                requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        alertDialogBuilder.setCancelable(false)
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (permissionLauncher.equals(requestCode)) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, load and display images
                loadImages()
            } else {
                // Permission denied, show a dialog explaining the need for the permission.
                showPermissionDeniedDialog()
            }
        }
    }

    // Show a dialog explaining the need for the permission
    private fun showPermissionDeniedDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Permission Denied")
        alertDialogBuilder.setMessage("The permission is required to access images, please enable it in the app settings.")
        alertDialogBuilder.setNegativeButton("Later") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialogBuilder.setPositiveButton("App Settings") { _, _ ->
            openAppSettings()
        }
        alertDialogBuilder.setCancelable(false)
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }

    // Open the app settings screen
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    // Open the full-size image activity
    private fun openFullImage(position: Int) {
        val intent = Intent(this, FullImageActivity::class.java)
        // Pass the image data to the FullImageActivity
        intent.putExtra("imageId", imageAdapter.getItemId(position))
        startActivity(intent)
    }
}