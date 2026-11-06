
package com.example.appgamerating

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.net.URL

class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var contenedorVideojuegos: LinearLayout
    private lateinit var btnConsultarResultados: Button

    private val db = FirebaseFirestore.getInstance()

    private lateinit var codigoEstudiante: String
    private var listenerPublicacion: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        contenedorVideojuegos =
            findViewById(R.id.contenedorVideojuegos)

        btnConsultarResultados =
            findViewById(R.id.btnConsultarResultados)

        val nombre = intent.getStringExtra("nombreEstudiante")
            ?: "Estudiante"

        codigoEstudiante =
            intent.getStringExtra("codigoEstudiante") ?: ""

        findViewById<TextView>(
            R.id.tvBienvenidaEstudiante
        ).text = "Bienvenido, $nombre"

        btnConsultarResultados.setOnClickListener {

            val intent = Intent(
                this,
                ResultsActivity::class.java
            )

            intent.putExtra("origenEstudiante", true)
            startActivity(intent)
        }

        cargarVideojuegos()
    }

    override fun onStart() {
        super.onStart()
        escucharPublicacionResultados()
    }

    override fun onStop() {
        super.onStop()

        listenerPublicacion?.remove()
        listenerPublicacion = null
    }

    // CONSULTAR SI EL ADMINISTRADOR PUBLICÓ LOS RESULTADOS
    private fun escucharPublicacionResultados() {

        listenerPublicacion?.remove()

        listenerPublicacion = db.collection("configuracion")
            .document("resultados")
            .addSnapshotListener { documento, error ->

                if (error != null) {
                    btnConsultarResultados.visibility = View.GONE
                    return@addSnapshotListener
                }

                val publicados =
                    documento?.getBoolean("publicados") ?: false

                btnConsultarResultados.visibility =
                    if (publicados) View.VISIBLE else View.GONE
            }
    }

    // CARGAR VIDEOJUEGOS
    private fun cargarVideojuegos() {

        contenedorVideojuegos.removeAllViews()
        mostrarMensaje("Cargando videojuegos...")

        db.collection("videojuegos")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { documentos ->

                contenedorVideojuegos.removeAllViews()

                if (documentos.isEmpty) {
                    mostrarMensaje("No hay videojuegos disponibles")
                    return@addOnSuccessListener
                }

                for (documento in documentos) {

                    crearTarjeta(
                        documento.id,
                        documento.getString("nombre") ?: "Sin nombre",
                        documento.getString("autores") ?: "Sin autores",
                        documento.getString("descripcion") ?: "",
                        documento.getString("imagenUrl")
                    )
                }
            }
            .addOnFailureListener { error ->

                contenedorVideojuegos.removeAllViews()
                mostrarMensaje("No fue posible cargar los videojuegos")

                Toast.makeText(
                    this,
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // CREAR TARJETA DE VIDEOJUEGO
    private fun crearTarjeta(
        juegoId: String,
        nombre: String,
        autores: String,
        descripcion: String,
        imagenUrl: String?
    ) {

        val tarjeta = LinearLayout(this).apply {

            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))

            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.LTGRAY)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }

            // ABRIR PANTALLA DE EVALUACIÓN
            setOnClickListener {

                val intent = Intent(
                    this@StudentDashboardActivity,
                    GameEvaluationActivity::class.java
                )

                intent.putExtra("juegoId", juegoId)
                intent.putExtra("nombreJuego", nombre)
                intent.putExtra("autoresJuego", autores)
                intent.putExtra("descripcionJuego", descripcion)
                intent.putExtra("imagenUrl", imagenUrl)
                intent.putExtra("codigoEstudiante", codigoEstudiante)

                startActivity(intent)
            }
        }

        val portada = ImageView(this).apply {

            layoutParams = LinearLayout.LayoutParams(
                dp(100),
                dp(125)
            )

            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.LTGRAY)
        }

        tarjeta.addView(portada)

        val informacion = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val tvNombre = TextView(this).apply {
            text = nombre
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }

        val tvAutores = TextView(this).apply {
            text = "Autores: $autores"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(8), 0, dp(12))
        }

        val tvAccion = TextView(this).apply {
            text = "Tocar para evaluar →"
            textSize = 14f
            setTextColor(Color.rgb(40, 90, 180))
            setTypeface(null, Typeface.BOLD)
        }

        informacion.addView(tvNombre)
        informacion.addView(tvAutores)
        informacion.addView(tvAccion)

        tarjeta.addView(informacion)

        contenedorVideojuegos.addView(tarjeta)

        // CARGAR PORTADA DESDE CLOUDINARY
        if (!imagenUrl.isNullOrEmpty()) {

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
                            portada.setImageBitmap(bitmap)
                        }
                    }

                } catch (e: Exception) {
                    // Mantener espacio de portada
                }
            }.start()
        }
    }

    private fun mostrarMensaje(mensaje: String) {

        val texto = TextView(this).apply {
            text = mensaje
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(dp(20), dp(25), dp(20), dp(25))
        }

        contenedorVideojuegos.addView(texto)
    }

    private fun dp(valor: Int): Int {
        return (valor * resources.displayMetrics.density).toInt()
    }
}
