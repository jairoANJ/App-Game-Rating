package com.example.appgamerating

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        auth = FirebaseAuth.getInstance()

        val etUsuario = findViewById<EditText>(R.id.etUsuario)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnIngresar = findViewById<Button>(R.id.btnAdminIngresar)

        btnIngresar.setOnClickListener {
            val correo = etUsuario.text.toString().trim()
            val password = etPassword.text.toString()

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(correo, password)
                .addOnSuccessListener {
                    startActivity(
                        Intent(this, AdminDashboardActivity::class.java)
                    )
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "Credenciales incorrectas",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}