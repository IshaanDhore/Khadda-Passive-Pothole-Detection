package com.pothole.khadda.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pothole.khadda.R
import java.io.File

class DebugGalleryActivity : AppCompatActivity() {

    private lateinit var imageContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_gallery)

        imageContainer = findViewById(R.id.imageContainer)
        
        findViewById<Button>(R.id.btnShowConfirmed).setOnClickListener {
            loadImages("pothole_")
        }
        
        findViewById<Button>(R.id.btnShowRejected).setOnClickListener {
            loadImages("rejected_pothole_")
        }

        // Load rejected by default to help user debug
        loadImages("rejected_pothole_")
    }

    private fun loadImages(prefix: String) {
        imageContainer.removeAllViews()
        
        val filesDir = applicationContext.filesDir
        val images = filesDir.listFiles { file -> 
            file.name.startsWith(prefix) && file.name.endsWith(".jpg") 
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        if (images.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No images found with prefix: $prefix"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                setPadding(0, 32, 0, 0)
            }
            imageContainer.addView(tv)
            return
        }

        for (file in images) {
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    800 // Fixed height for display
                ).apply {
                    setMargins(0, 0, 0, 32) // Bottom margin
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                
                // Allow user to delete this specific image!
                setOnLongClickListener {
                    androidx.appcompat.app.AlertDialog.Builder(this@DebugGalleryActivity)
                        .setTitle("Delete Image")
                        .setMessage("Are you sure you want to delete ${file.name}?")
                        .setPositiveButton("Delete") { _, _ ->
                            if (file.delete()) {
                                android.widget.Toast.makeText(this@DebugGalleryActivity, "Deleted", android.widget.Toast.LENGTH_SHORT).show()
                                loadImages(prefix) // Refresh the gallery
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
            
            val label = TextView(this).apply {
                text = file.name
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 14f
                setPadding(0, 0, 0, 8)
            }
            
            imageContainer.addView(label)
            imageContainer.addView(imageView)
        }
    }
}
