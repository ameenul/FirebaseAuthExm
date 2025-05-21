package com.example.myapplication

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class ContactAdapter(
    private val contacts: MutableList<Contact>
) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    // Ambil UID user yang sedang login
    private val uid: String = FirebaseAuth.getInstance().currentUser
        ?.uid
        ?: throw IllegalStateException("User belum login")

    // Referensi ke node contacts/{uid}
    private val dbRef = FirebaseDatabase.getInstance()
        .getReference("contacts")
        .child(uid)

    // Referensi ke folder photos/{uid}
    private val storageRef = FirebaseStorage.getInstance()
        .getReference("photos")
        .child(uid)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]

        // Tampilkan nama dan nomor HP
        holder.tvName.text = contact.name
        holder.tvPhone.text = contact.phone

        // Load gambar bundar dengan Coil + circleCrop
        holder.imgPhoto.load(contact.photoUrl) {
            placeholder(android.R.drawable.ic_menu_report_image)
            error(android.R.drawable.ic_menu_report_image)
            transformations(CircleCropTransformation())
        }

        // Gunakan icon delete bawaan Android
        holder.imgDelete.setImageResource(android.R.drawable.ic_menu_delete)

        // Handle klik delete
        holder.imgDelete.setOnClickListener { view ->
            MaterialAlertDialogBuilder(view.context)
                .setTitle("Hapus kontak")
                .setMessage("Yakin ingin menghapus “${contact.name}”?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ ->
                    // 1) Hapus data di Realtime Database
                    dbRef.child(contact.id)
                        .removeValue()
                        .addOnSuccessListener {
                            // 2) Hapus file gambar di Storage
                            val filename = Uri.parse(contact.photoUrl).lastPathSegment
                            if (!filename.isNullOrEmpty()) {
                                storageRef.child(filename).delete()
                            }

                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                view.context,
                                "Gagal menghapus: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .show()
        }
    }

    override fun getItemCount(): Int = contacts.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgPhoto: ImageView   = itemView.findViewById(R.id.imgPhoto)
        val tvName:   TextView    = itemView.findViewById(R.id.tvName)
        val tvPhone:  TextView    = itemView.findViewById(R.id.tvPhone)
        val imgDelete: ImageView  = itemView.findViewById(R.id.imgDelete)
    }
}
