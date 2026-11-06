
package com.example.appgamerating

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AddGameActivity : AppCompatActivity() {

    private lateinit var imgPortada: ImageView

    private var imagenSeleccionada: Uri? = null
    private var imagenUrlActual: String? = null
    private var juegoId: String? = null

    private val cloudName = "yrsaucqf"
    private val uploadPreset = "gamerating_upload"

    private val seleccionarImagen =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            if (uri != null) {
                imagenSeleccionada = uri
                imgPortada.setImageURI(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_game)

        val db = FirebaseFirestore.getInstance()

        imgPortada = findViewById(R.id.imgPortada)

        val btnSeleccionar =
            findViewById<Button>(R.id.btnSeleccionarImagen)

        val etNombre =
            findViewById<EditText>(R.id.etNombreJuego)

        val etAutores =
            findViewById<EditText>(R.id.etAutores)

        val etDescripcion =
            findViewById<EditText>(R.id.etDescripcion)

        val btnGuardar =
            findViewById<Button>(R.id.btnGuardarJuego)

        val btnCancelar =
            findViewById<Button>(R.id.btnCancelar)

        // Identificar si estamos editando
        juegoId = intent.getStringExtra("juegoId")

        if (juegoId != null) {

            btnGuardar.text = "Actualizar videojuego"

            db.collection("videojuegos")
                .document(juegoId!!)
                .get()
                .addOnSuccessListener { documento ->

                    if (documento.exists()) {

                        etNombre.setText(
                            documento.getString("nombre") ?: ""
                        )

                        etAutores.setText(
                            documento.getString("autores") ?: ""
                        )

                        etDescripcion.setText(
                            documento.getString("descripcion") ?: ""
                        )

                        imagenUrlActual =
                            documento.getString("imagenUrl")

                        cargarPortadaActual(imagenUrlActual)

                    } else {
                        Toast.makeText(
                            this,
                            "No se encontró el videojuego",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "Error al cargar videojuego: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
        }

        btnSeleccionar.setOnClickListener {
            seleccionarImagen.launch("image/*")
        }

        btnCancelar.setOnClickListener {
            finish()
        }

        btnGuardar.setOnClickListener {

            val nombre = etNombre.text.toString().trim()
            val autores = etAutores.text.toString().trim()
            val descripcion = etDescripcion.text.toString().trim()

            if (nombre.isEmpty() ||
                autores.isEmpty() ||
                descripcion.isEmpty()
            ) {
                Toast.makeText(
                    this,
                    "Completa todos los campos",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            btnGuardar.isEnabled = false
            btnGuardar.text = "Guardando..."

            val imagen = imagenSeleccionada

            if (imagen != null) {

                subirImagenCloudinary(imagen) { url, error ->

                    if (url != null) {

                        guardarVideojuego(
                            db,
                            nombre,
                            autores,
                            descripcion,
                            url,
                            btnGuardar
                        )

                    } else {

                        btnGuardar.isEnabled = true
                        btnGuardar.text =
                            if (juegoId != null) "Actualizar videojuego"
                            else "Guardar videojuego"

                        Toast.makeText(
                            this,
                            "Error al subir imagen: $error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } else {

                guardarVideojuego(
                    db,
                    nombre,
                    autores,
                    descripcion,
                    imagenUrlActual,
                    btnGuardar
                )
            }
        }
    }

    // CARGAR PORTADA ACTUAL
    private fun cargarPortadaActual(url: String?) {

        if (url.isNullOrEmpty()) return

        Thread {
            try {
                val conexion = URL(url).openConnection()
                conexion.connectTimeout = 15000
                conexion.readTimeout = 15000

                val bitmap = conexion.getInputStream().use {
                    BitmapFactory.decodeStream(it)
                }

                runOnUiThread {
                    if (!isFinishing && !isDestroyed && bitmap != null) {
                        imgPortada.setImageBitmap(bitmap)
                    }
                }

            } catch (e: Exception) {
                // Mantener imagen predeterminada
            }
        }.start()
    }

    // SUBIR IMAGEN A CLOUDINARY
    private fun subirImagenCloudinary(
        uri: Uri,
        callback: (String?, String?) -> Unit
    ) {

        Thread {

            var connection: HttpURLConnection? = null

            try {

                val boundary =
                    "----GameRating${System.currentTimeMillis()}"

                val url = URL(
                    "https://api.cloudinary.com/v1_1/$cloudName/image/upload"
                )

                connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 30000
                connection.readTimeout = 30000

                connection.setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=$boundary"
                )

                connection.setChunkedStreamingMode(0)

                val output = connection.outputStream

                output.write("--$boundary\r\n".toByteArray())

                output.write(
                    "Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n"
                        .toByteArray()
                )

                output.write("$uploadPreset\r\n".toByteArray())

                val mimeType =
                    contentResolver.getType(uri) ?: "image/jpeg"

                output.write("--$boundary\r\n".toByteArray())

                output.write(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"portada\"\r\n"
                        .toByteArray()
                )

                output.write(
                    "Content-Type: $mimeType\r\n\r\n".toByteArray()
                )

                contentResolver.openInputStream(uri)?.use {
                    it.copyTo(output)
                } ?: throw Exception("No se pudo leer la imagen")

                output.write("\r\n".toByteArray())
                output.write("--$boundary--\r\n".toByteArray())

                output.flush()
                output.close()

                val responseCode = connection.responseCode

                val responseStream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response = responseStream
                    ?.bufferedReader()
                    ?.use { it.readText() } ?: ""

                if (responseCode in 200..299) {

                    val json = JSONObject(response)
                    val imageUrl = json.getString("secure_url")

                    runOnUiThread {
                        callback(imageUrl, null)
                    }

                } else {
                    throw Exception(
                        "Cloudinary HTTP $responseCode: $response"
                    )
                }

            } catch (e: Exception) {

                runOnUiThread {
                    callback(null, e.message)
                }

            } finally {
                connection?.disconnect()
            }

        }.start()
    }

    // REGISTRAR O ACTUALIZAR EN FIRESTORE
    private fun guardarVideojuego(
        db: FirebaseFirestore,
        nombre: String,
        autores: String,
        descripcion: String,
        imagenUrl: String?,
        btnGuardar: Button
    ) {

        val juego = hashMapOf<String, Any>(
            "nombre" to nombre,
            "autores" to autores,
            "descripcion" to descripcion,
            "activo" to true
        )

        if (imagenUrl != null) {
            juego["imagenUrl"] = imagenUrl
        }

        val id = juegoId

        if (id != null) {

            // ACTUALIZAR DOCUMENTO EXISTENTE
            db.collection("videojuegos")
                .document(id)
                .update(juego)
                .addOnSuccessListener {

                    Toast.makeText(
                        this,
                        "Videojuego actualizado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
                .addOnFailureListener { error ->

                    btnGuardar.isEnabled = true
                    btnGuardar.text = "Actualizar videojuego"

                    Toast.makeText(
                        this,
                        "Error al actualizar: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

        } else {

            // REGISTRAR NUEVO DOCUMENTO
            juego["fechaRegistro"] =
                FieldValue.serverTimestamp()

            db.collection("videojuegos")
                .add(juego)
                .addOnSuccessListener {

                    Toast.makeText(
                        this,
                        "Videojuego registrado correctamente",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
                .addOnFailureListener { error ->

                    btnGuardar.isEnabled = true
                    btnGuardar.text = "Guardar videojuego"

                    Toast.makeText(
                        this,
                        "Error al guardar: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }
}
