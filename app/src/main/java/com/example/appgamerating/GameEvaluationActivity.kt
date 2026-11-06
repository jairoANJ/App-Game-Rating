
package com.example.appgamerating

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.net.URL
import android.content.res.ColorStateList
import android.graphics.Color

class GameEvaluationActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var ratingJugabilidad: RatingBar
    private lateinit var ratingGraficos: RatingBar
    private lateinit var ratingSonido: RatingBar
    private lateinit var ratingCreatividad: RatingBar
    private lateinit var ratingPedagogico: RatingBar
    private lateinit var btnEnviar: Button

    private var verificacionCompleta = false
    private var guardando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_evaluation)

        val imgJuego = findViewById<ImageView>(R.id.imgJuegoEvaluacion)
        val tvNombre = findViewById<TextView>(R.id.tvNombreJuego)
        val tvDescripcion = findViewById<TextView>(R.id.tvDescripcionJuego)

        btnEnviar = findViewById(R.id.btnEnviarEvaluacion)
        val btnVolver = findViewById<Button>(R.id.btnVolverCatalogo)

        ratingJugabilidad = findViewById(R.id.ratingJugabilidad)
        ratingGraficos = findViewById(R.id.ratingGraficos)
        ratingSonido = findViewById(R.id.ratingSonido)
        ratingCreatividad = findViewById(R.id.ratingCreatividad)
        ratingPedagogico = findViewById(R.id.ratingExperiencia)

        val amarillo = ColorStateList.valueOf(Color.parseColor("#FFC107"))
        val gris = ColorStateList.valueOf(Color.parseColor("#D6D6D6"))

        listOf(
            ratingJugabilidad,
            ratingGraficos,
            ratingSonido,
            ratingCreatividad,
            ratingPedagogico
        ).forEach {
            it.progressTintList = amarillo
            it.progressBackgroundTintList = gris
        }

        val juegoId = intent.getStringExtra("juegoId") ?: ""
        val codigoEstudiante = intent.getStringExtra("codigoEstudiante") ?: ""

        val nombre = intent.getStringExtra("nombreJuego") ?: ""
        val autores = intent.getStringExtra("autoresJuego") ?: ""
        val descripcion = intent.getStringExtra("descripcionJuego") ?: ""
        val imagenUrl = intent.getStringExtra("imagenUrl") ?: ""

        tvNombre.text = nombre
        tvDescripcion.text = "$descripcion\n\nAutores: $autores"

        if (juegoId.isEmpty() || codigoEstudiante.isEmpty()) {
            Toast.makeText(
                this,
                "Faltan datos del estudiante o del videojuego",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val usuario = auth.currentUser

        if (usuario == null) {
            Toast.makeText(
                this,
                "No hay una sesión de estudiante activa",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        val uid = usuario.uid
        val evaluacionId = "${codigoEstudiante}_${juegoId}"

        // Cargar portada
        if (imagenUrl.isNotEmpty()) {
            Thread {
                try {
                    val conexion = URL(imagenUrl).openConnection()
                    conexion.connectTimeout = 15000
                    conexion.readTimeout = 15000

                    val bitmap = conexion.getInputStream().use {
                        BitmapFactory.decodeStream(it)
                    }

                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && bitmap != null) {
                            imgJuego.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    // Mantener imagen predeterminada
                }
            }.start()
        }

        // Consultar la evaluación propia
        btnEnviar.isEnabled = false
        btnEnviar.text = "Verificando evaluación..."

        db.collection("evaluaciones")
            .document(evaluacionId)
            .get()
            .addOnSuccessListener { documento ->

                verificacionCompleta = true

                if (documento.exists()) {

                    // Comprobar que pertenece a esta sesión
                    val uidGuardado = documento.getString("uid")

                    if (uidGuardado != uid) {
                        verificacionCompleta = false
                        btnEnviar.isEnabled = false
                        btnEnviar.text = "No disponible"

                        Toast.makeText(
                            this,
                            "No tienes acceso a esta evaluación",
                            Toast.LENGTH_LONG
                        ).show()

                        return@addOnSuccessListener
                    }

                    ratingJugabilidad.rating =
                        (documento.getLong("jugabilidad") ?: 0L).toFloat()

                    ratingGraficos.rating =
                        (documento.getLong("graficos") ?: 0L).toFloat()

                    ratingSonido.rating =
                        (documento.getLong("sonido") ?: 0L).toFloat()

                    ratingCreatividad.rating =
                        (documento.getLong("creatividad") ?: 0L).toFloat()

                    ratingPedagogico.rating =
                        (documento.getLong("objetivoPedagogico") ?: 0L).toFloat()

                    bloquearEvaluacion()

                    btnEnviar.text = "Evaluación realizada"
                    btnEnviar.isEnabled = false

                    Toast.makeText(
                        this,
                        "Ya realizaste la evaluación de este videojuego",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    btnEnviar.text = "Enviar evaluación"
                    btnEnviar.isEnabled = true
                }
            }
            .addOnFailureListener { error ->

                verificacionCompleta = false
                btnEnviar.isEnabled = false
                btnEnviar.text = "No se pudo verificar"

                Toast.makeText(
                    this,
                    "Error al consultar evaluación: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        // Enviar evaluación
        btnEnviar.setOnClickListener {

            if (!verificacionCompleta || guardando) {
                return@setOnClickListener
            }

            val calificaciones = listOf(
                ratingJugabilidad,
                ratingGraficos,
                ratingSonido,
                ratingCreatividad,
                ratingPedagogico
            )

            if (calificaciones.any { it.rating == 0f }) {
                Toast.makeText(
                    this,
                    "Debes calificar los cinco criterios",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            guardando = true
            btnEnviar.isEnabled = false
            btnEnviar.text = "Guardando..."

            val evaluacion = hashMapOf(
                "uid" to uid,
                "codigoEstudiante" to codigoEstudiante,
                "juegoId" to juegoId,
                "nombreJuego" to nombre,
                "jugabilidad" to ratingJugabilidad.rating.toLong(),
                "graficos" to ratingGraficos.rating.toLong(),
                "sonido" to ratingSonido.rating.toLong(),
                "creatividad" to ratingCreatividad.rating.toLong(),
                "objetivoPedagogico" to ratingPedagogico.rating.toLong(),
                "fechaEvaluacion" to FieldValue.serverTimestamp()
            )

            db.collection("evaluaciones")
                .document(evaluacionId)
                .set(evaluacion)
                .addOnSuccessListener {

                    guardando = false
                    bloquearEvaluacion()

                    btnEnviar.text = "Evaluación realizada"
                    btnEnviar.isEnabled = false

                    Toast.makeText(
                        this,
                        "¡Evaluación guardada correctamente!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { error ->

                    guardando = false
                    btnEnviar.isEnabled = false
                    btnEnviar.text = "Evaluación no guardada"

                    Toast.makeText(
                        this,
                        "No se pudo guardar. Puede que ya exista una evaluación. ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        btnVolver.setOnClickListener {
            finish()
        }
    }

    private fun bloquearEvaluacion() {
        ratingJugabilidad.isEnabled = false
        ratingGraficos.isEnabled = false
        ratingSonido.isEnabled = false
        ratingCreatividad.isEnabled = false
        ratingPedagogico.isEnabled = false
    }
}
