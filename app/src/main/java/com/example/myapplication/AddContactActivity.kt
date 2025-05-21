package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class AddContactActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar

    private lateinit var imgPreview: ImageView
    private lateinit var btnChoosePhoto: Button
    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilPhone: TextInputLayout
    private lateinit var etPhone: TextInputEditText
    private lateinit var btnSave: Button

    private var selectedImageUri: Uri? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: com.google.firebase.database.DatabaseReference
    private lateinit var storageRef: com.google.firebase.storage.StorageReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contact)

        // Inisialisasi Firebase
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
            ?: run {
                Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        dbRef = FirebaseDatabase.getInstance()
            .getReference("contacts")
            .child(uid)
        storageRef = FirebaseStorage.getInstance()
            .getReference("photos")
            .child(uid)

        // Binding view
        imgPreview     = findViewById(R.id.imgPreview)
        btnChoosePhoto = findViewById(R.id.btnChoosePhoto)
        tilName        = findViewById(R.id.tilName)
        etName         = findViewById(R.id.etName)
        tilPhone       = findViewById(R.id.tilPhone)
        etPhone        = findViewById(R.id.etPhone)
        btnSave        = findViewById(R.id.btnSaveContact)
        progressBar     = findViewById(R.id.progressBar)


        // Siapkan launcher untuk memilih gambar dari Gallery
        pickImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                imgPreview.setImageURI(it)
            }
        }

        // Tombol pilih foto
        btnChoosePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Tombol simpan kontak
        btnSave.setOnClickListener {
            tilName.error = null
            tilPhone.error = null

            val name  = etName.text?.toString()?.trim().orEmpty()
            val phone = etPhone.text?.toString()?.trim().orEmpty()
            val imageUri = selectedImageUri

            if (name.isEmpty()) {
                tilName.error = "Nama tidak boleh kosong"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                tilPhone.error = "Nomor HP tidak boleh kosong"
                return@setOnClickListener
            }
            if (imageUri == null) {
                Toast.makeText(this, "Silakan pilih foto terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            btnSave.text = "Menyimpan..."
            progressBar.visibility = View.VISIBLE


            // Generate key untuk kontak baru
            val contactId = dbRef.push().key
            if (contactId == null) {
                Toast.makeText(this, "Gagal membuat ID kontak", Toast.LENGTH_SHORT).show()
                resetSaveButton()
                return@setOnClickListener
            }

            // Upload foto ke Firebase Storage
            val imageRef = storageRef.child("$contactId.jpg")
            imageRef.putFile(imageUri)
                .addOnSuccessListener {
                    // Ambil URL download
                    imageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            val contact = Contact(
                                id       = contactId,
                                name     = name,
                                photoUrl = downloadUri.toString(),
                                phone    = phone
                            )
                            // Simpan data kontak ke Realtime Database
                            dbRef.child(contactId).setValue(contact)
                                .addOnSuccessListener {
                                    Toast.makeText(this,
                                        "Kontak berhasil disimpan",
                                        Toast.LENGTH_SHORT).show()
                                    progressBar.visibility = View.GONE
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this,
                                        "Gagal simpan kontak: ${e.message}",
                                        Toast.LENGTH_SHORT).show()
                                    progressBar.visibility = View.GONE
                                    resetSaveButton()
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this,
                                "Gagal ambil URL: ${e.message}",
                                Toast.LENGTH_SHORT).show()
                            resetSaveButton()
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this,
                        "Gagal upload gambar: ${e.message}",
                        Toast.LENGTH_SHORT).show()
                    resetSaveButton()
                }
        }
    }

    private fun resetSaveButton() {
        btnSave.isEnabled = true
        btnSave.text = "Simpan"
    }
}


////rule firebase storage
//rules_version = '2';
//service firebase.storage {
//    match /b/{bucket}/o {
//        // Hanya izinkan akses ke path /photos/{userId}/...
//        match /photos/{userId}/{allPaths=**} {
//            allow read, write: if request.auth != null
//            && request.auth.uid == userId;
//        }
//
//        // Default: tolak semua akses lainnya
//        match /{allPaths=**} {
//            allow read, write: if false;
//        }
//    }
//}
