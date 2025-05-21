package com.example.myapplication


import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DaftarKontakActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference

    private lateinit var rvContacts: RecyclerView
    private lateinit var fabAdd: FloatingActionButton

    private val contacts = mutableListOf<Contact>()
    private lateinit var adapter: ContactAdapter

    private lateinit var googleSignInClient: GoogleSignInClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daftar_kontak)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Get your web client ID from google-services.json
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // inisialisasi Firebase Auth & Database
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
            ?: run {
                // jika belum login, pindah ke LoginActivity
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }
        dbRef = FirebaseDatabase.getInstance()
            .getReference("contacts")
            .child(uid)

        // setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))

        // setup RecyclerView & Adapter
        rvContacts = findViewById(R.id.rvContacts)
        rvContacts.layoutManager = LinearLayoutManager(this)
        adapter = ContactAdapter(contacts)
        rvContacts.adapter = adapter

        // fab untuk tambah kontak
        fabAdd = findViewById(R.id.fabAddContact)
        fabAdd.setOnClickListener {
            startActivity(Intent(this, AddContactActivity::class.java))
        }

        // ambil data kontak dari Firebase
        fetchContacts()
    }

    private fun fetchContacts() {
        // dengarkan perubahan di node contacts/{uid}
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                contacts.clear()
                for (child in snapshot.children) {
                    // dataSnapshot -> Contact
                    val contact = child.getValue(Contact::class.java)
                    if (contact != null) {
                        contacts.add(contact)
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                // bisa tampilkan Toast atau log
            }
        })
    }

    // tambahkan opsi logout di toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            auth.signOut()
            startActivity(Intent(this,LoginActivity::class.java))

            googleSignInClient.signOut()

            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
