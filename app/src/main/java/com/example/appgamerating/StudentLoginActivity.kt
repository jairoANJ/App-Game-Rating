
package com.example.appgamerating

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class StudentLoginActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_login)

        val etCodigo = findViewById<EditText>(R.id.etCodigoEstudiante)
        val btnIngresar = findViewById<Button>(R.id.btnIngresar)

        btnIngresar.setOnClickListener {

            val codigo = etCodigo.text.toString().trim()

            if (codigo.isEmpty()) {
                etCodigo.error = "Ingresa tu código"
                return@setOnClickListener
            }

            btnIngresar.isEnabled = false
            btnIngresar.text = "Validando..."

            val usuario = auth.currentUser

            if (usuario != null) {
                validarEstudiante(codigo, btnIngresar)
            } else {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        validarEstudiante(codigo, btnIngresar)
                    }
                    .addOnFailureListener { error ->
                        restaurarBoton(btnIngresar)
                        mostrarError("Error de autenticación: ${error.message}")
                    }
            }
        }
    }

    private fun validarEstudiante(codigo: String, boton: Button) {

        db.collection("estudiantes")
            .document(codigo)
            .get()
            .addOnSuccessListener { documento ->

                if (documento.exists()) {

                    val activo = documento.getBoolean("activo") ?: false
                    val nombre = documento.getString("nombre") ?: "Estudiante"

                    if (activo) {
                        val intent = Intent(
                            this,
                            StudentDashboardActivity::class.java
                        )

                        intent.putExtra("nombreEstudiante", nombre)
                        intent.putExtra("codigoEstudiante", codigo)

                        startActivity(intent)
                        finish()
                    } else {
                        mostrarError("Este código está inactivo")
                    }

                } else {
                    mostrarError("El código ingresado no existe")
                }

                restaurarBoton(boton)
            }
            .addOnFailureListener { error ->
                restaurarBoton(boton)
                mostrarError("Error de conexión: ${error.message}")
            }
    }

    private fun restaurarBoton(boton: Button) {
        boton.isEnabled = true
        boton.text = "Ingresar"
    }

    private fun mostrarError(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}
