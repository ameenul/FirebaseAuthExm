package com.example.myapplication

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
// Remove FileProvider import if not used elsewhere for this specific flow
// import androidx.core.content.FileProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
// import java.io.File // Only needed if you still want to handle temp files for some reason
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.format

class AddContactActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var imgPreview: ImageView
    private lateinit var btnChoosePhoto: Button
    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var btnSave: Button

    // This will now hold the URI from MediaStore for the captured image
    private var capturedImageUri: Uri? = null
    // pickImageLauncher remains for gallery selection
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>


    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: com.google.firebase.database.DatabaseReference
    private lateinit var storageRef: com.google.firebase.storage.StorageReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contact)

        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
            ?: run {
                Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        dbRef = FirebaseDatabase.getInstance().getReference("contacts").child(uid)
        storageRef = FirebaseStorage.getInstance().getReference("photos").child(uid)

        imgPreview = findViewById(R.id.imgPreview)
        btnChoosePhoto = findViewById(R.id.btnChoosePhoto)
        tilName = findViewById(R.id.tilName)
        etName = findViewById(R.id.etName)
        tilPhone = findViewById(R.id.tilPhone)
        etPhone = findViewById(R.id.etPhone)
        btnSave = findViewById(R.id.btnSaveContact)
        progressBar = findViewById(R.id.progressBar)

        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                capturedImageUri = it // Update this to use capturedImageUri consistently
                imgPreview.setImageURI(it)
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                // The image is already saved to the 'capturedImageUri' by the camera app
                capturedImageUri?.let { uri ->
                    imgPreview.setImageURI(uri)
                    Toast.makeText(this, "Image saved to MediaStore: $uri", Toast.LENGTH_SHORT).show()
                    // You can now use this 'capturedImageUri' for Firebase upload
                }
            } else {
                Toast.makeText(this, "Image capture failed", Toast.LENGTH_SHORT).show()
                // Optionally, if capture failed, you might want to delete the pending MediaStore entry
                // if created with IS_PENDING, though TakePicture contract handles this well.
                capturedImageUri = null
            }
        }

        btnChoosePhoto.setOnClickListener {
            // Let's add a dialog to choose between Camera and Gallery
            showPhotoSourceDialog()
        }

        btnSave.setOnClickListener {
            tilName.error = null
            tilPhone.error = null

            val name = etName.text?.toString()?.trim().orEmpty()
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            // Use capturedImageUri for both camera and gallery
            val imageToUploadUri = capturedImageUri

            if (name.isEmpty()) {
                tilName.error = "Nama tidak boleh kosong"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                tilPhone.error = "Nomor HP tidak boleh kosong"
                return@setOnClickListener
            }
            if (imageToUploadUri == null) {
                Toast.makeText(this, "Silakan pilih foto terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Menyimpan..."
            progressBar.visibility = View.VISIBLE

            val contactId = dbRef.push().key
            if (contactId == null) {
                Toast.makeText(this, "Gagal membuat ID kontak", Toast.LENGTH_SHORT).show()
                resetSaveButton()
                return@setOnClickListener
            }

            val imageRef = storageRef.child("$contactId.jpg")
            imageRef.putFile(imageToUploadUri) // Use the URI from MediaStore
                .addOnSuccessListener {
                    imageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            val contact = Contact(
                                id = contactId,
                                name = name,
                                photoUrl = downloadUri.toString(),
                                phone = phone
                            )
                            dbRef.child(contactId).setValue(contact)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Kontak berhasil disimpan", Toast.LENGTH_SHORT).show()
                                    progressBar.visibility = View.GONE
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Gagal simpan kontak: ${e.message}", Toast.LENGTH_SHORT).show()
                                    progressBar.visibility = View.GONE
                                    resetSaveButton()
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Gagal ambil URL: ${e.message}", Toast.LENGTH_SHORT).show()
                            resetSaveButton()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal upload gambar: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
        }
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf<CharSequence>("Take Photo", "Choose from Gallery", "Cancel")
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Choose Photo Source")
        builder.setItems(options) { dialog, item ->
            when {
                options[item] == "Take Photo" -> {
                    launchCameraWithMediaStore()
                }
                options[item] == "Choose from Gallery" -> {
                    pickImageLauncher.launch("image/*")
                }
                options[item] == "Cancel" -> {
                    dialog.dismiss()
                }
            }
        }
        builder.show()
    }


    private fun launchCameraWithMediaStore() {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val displayName = "IMG_${timeStamp}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage: Add to Pictures directory, specific to your app or general
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/myapplication")
                // put(MediaStore.Images.Media.IS_PENDING, 1) // TakePicture contract handles pending state well
            }
        }

        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val imageUriFromMediaStore = contentResolver.insert(imageCollection, contentValues)
            if (imageUriFromMediaStore == null) {
                Toast.makeText(this, "Failed to create MediaStore entry.", Toast.LENGTH_SHORT).show()
                return
            }
            else{
                capturedImageUri = imageUriFromMediaStore // Store this URI
                capturedImageUri?.let { it -> takePictureLauncher.launch(it) }
            }


        } catch (e: IOException) {
            Toast.makeText(this, "Error creating MediaStore entry: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }


    private fun resetSaveButton() {
        btnSave.isEnabled = true
        btnSave.text = "Simpan"
        progressBar.visibility = View.GONE // Ensure progress bar is hidden
    }

    // You might not need getTmpFileUri() anymore if always using MediaStore for camera
    // private fun getTmpFileUri(): Uri { ... }
}

// Dummy Contact data class for context
