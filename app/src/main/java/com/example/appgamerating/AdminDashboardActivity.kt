
package com.example.appgamerating

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.net.URL

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var contenedorJuegos: LinearLayout
    private lateinit var tvTotalEvaluaciones: TextView
    private lateinit var tvEstadoResultados: TextView
    private lateinit var btnPublicarResultados: Button

    private val db = FirebaseFirestore.getInstance()

    private var listenerEvaluaciones: ListenerRegistration? = null
    private var listenerConfiguracion: ListenerRegistration? = null

    private var resultadosPublicados = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, AdminLoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_admin_dashboard)

        contenedorJuegos = findViewById(R.id.contenedorJuegos)

        tvTotalEvaluaciones = findViewById(R.id.tvTotalEvaluaciones)
        tvEstadoResultados = findViewById(R.id.tvEstadoResultados)
        btnPublicarResultados = findViewById(R.id.btnPublicarResultados)

        val btnAgregarJuego = findViewById<Button>(R.id.btnAgregarJuego)
        val btnVerResultados = findViewById<Button>(R.id.btnVerResultados)

        val btnGestionarEstudiantes =
            findViewById<Button>(R.id.btnGestionarEstudiantes)

        btnGestionarEstudiantes.setOnClickListener {
            startActivity(
                Intent(this, StudentManagementActivity::class.java)
            )
        }

        btnAgregarJuego.setOnClickListener {
            startActivity(Intent(this, AddGameActivity::class.java))
        }

        btnPublicarResultados.setOnClickListener {
            cambiarEstadoPublicacion()
        }

        btnVerResultados.setOnClickListener {
            startActivity(
                Intent(this, ResultsActivity::class.java)
            )
        }

        // Botón para reiniciar las evaluaciones
        val btnReiniciar = Button(this).apply {
            text = "Reiniciar evaluaciones"
            isAllCaps = false
            textSize = 15f
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(180, 45, 45)
                )
            )
        }

        val layoutPrincipal = findViewById<LinearLayout>(
            R.id.contenedorJuegos
        ).parent as LinearLayout

        layoutPrincipal.addView(
            btnReiniciar,
            layoutPrincipal.indexOfChild(contenedorJuegos) + 1
        )

        btnReiniciar.setOnClickListener {
            confirmarReinicio()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::contenedorJuegos.isInitialized) {
            cargarVideojuegos()
            escucharEvaluaciones()
            escucharConfiguracion()
        }
    }

    override fun onPause() {
        super.onPause()

        listenerEvaluaciones?.remove()
        listenerConfiguracion?.remove()

        listenerEvaluaciones = null
        listenerConfiguracion = null
    }

    // CARGAR VIDEOJUEGOS
    private fun cargarVideojuegos() {

        contenedorJuegos.removeAllViews()
        mostrarMensaje("Cargando videojuegos...")

        db.collection("videojuegos")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { documentos ->

                contenedorJuegos.removeAllViews()

                if (documentos.isEmpty) {
                    mostrarMensaje("Todavía no hay videojuegos registrados")
                    return@addOnSuccessListener
                }

                for (documento in documentos) {

                    crearTarjetaJuego(
                        documento.id,
                        documento.getString("nombre") ?: "Sin nombre",
                        documento.getString("autores") ?: "Sin autores",
                        documento.getString("imagenUrl")
                    )
                }
            }
            .addOnFailureListener { error ->

                contenedorJuegos.removeAllViews()
                mostrarMensaje("Error al cargar videojuegos")

                Toast.makeText(
                    this,
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ESCUCHAR EVALUACIONES EN TIEMPO REAL
    private fun escucharEvaluaciones() {

        listenerEvaluaciones?.remove()

        listenerEvaluaciones = db.collection("evaluaciones")
            .addSnapshotListener { documentos, error ->

                if (error != null) {
                    tvTotalEvaluaciones.text =
                        "No fue posible consultar las evaluaciones"
                    return@addSnapshotListener
                }

                val total = documentos?.size() ?: 0

                tvTotalEvaluaciones.text =
                    "Evaluaciones recibidas: $total"
            }
    }

    // ESCUCHAR ESTADO DE PUBLICACIÓN
    private fun escucharConfiguracion() {

        listenerConfiguracion?.remove()

        listenerConfiguracion = db.collection("configuracion")
            .document("resultados")
            .addSnapshotListener { documento, error ->

                if (error != null) return@addSnapshotListener

                resultadosPublicados =
                    documento?.getBoolean("publicados") ?: false

                actualizarEstadoPublicacion()
            }
    }

    private fun actualizarEstadoPublicacion() {

        if (resultadosPublicados) {

            tvEstadoResultados.text =
                "Resultados publicados para los estudiantes"

            btnPublicarResultados.text = "Ocultar resultados"

        } else {

            tvEstadoResultados.text =
                "Resultados ocultos para los estudiantes"

            btnPublicarResultados.text = "Publicar resultados"
        }
    }

    // PUBLICAR U OCULTAR RESULTADOS
    private fun cambiarEstadoPublicacion() {

        val nuevoEstado = !resultadosPublicados

        val mensaje = if (nuevoEstado) {
            "¿Deseas publicar los resultados para todos los estudiantes?"
        } else {
            "¿Deseas ocultar los resultados?"
        }

        AlertDialog.Builder(this)
            .setTitle("Resultados")
            .setMessage(mensaje)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->

                btnPublicarResultados.isEnabled = false

                db.collection("configuracion")
                    .document("resultados")
                    .set(
                        mapOf("publicados" to nuevoEstado)
                    )
                    .addOnSuccessListener {

                        btnPublicarResultados.isEnabled = true

                        Toast.makeText(
                            this,
                            if (nuevoEstado)
                                "Resultados publicados"
                            else
                                "Resultados ocultos",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { error ->

                        btnPublicarResultados.isEnabled = true

                        Toast.makeText(
                            this,
                            "Error: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    // REINICIAR EVALUACIONES
    private fun confirmarReinicio() {

        AlertDialog.Builder(this)
            .setTitle("Reiniciar evaluaciones")
            .setMessage(
                "Se eliminarán todas las calificaciones de prueba.\n\n" +
                        "Los videojuegos, estudiantes y portadas se conservarán.\n\n" +
                        "¿Deseas continuar?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar evaluaciones") { _, _ ->

                confirmarEliminacionDefinitiva()
            }
            .show()
    }

    private fun confirmarEliminacionDefinitiva() {

        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage(
                "Esta acción eliminará definitivamente todas las evaluaciones. ¿Estás seguro?"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, reiniciar") { _, _ ->

                reiniciarEvaluaciones()
            }
            .show()
    }

    private fun reiniciarEvaluaciones() {

        btnPublicarResultados.isEnabled = false

        db.collection("evaluaciones")
            .get()
            .addOnSuccessListener { documentos ->

                val lotes = documentos.documents.chunked(400)

                eliminarLote(lotes, 0)
            }
            .addOnFailureListener { error ->

                btnPublicarResultados.isEnabled = true

                Toast.makeText(
                    this,
                    "Error al consultar evaluaciones: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun eliminarLote(
        lotes: List<List<com.google.firebase.firestore.DocumentSnapshot>>,
        indice: Int
    ) {

        if (indice >= lotes.size) {

            db.collection("configuracion")
                .document("resultados")
                .set(mapOf("publicados" to false))
                .addOnSuccessListener {

                    btnPublicarResultados.isEnabled = true

                    Toast.makeText(
                        this,
                        "Evaluaciones eliminadas. Puedes comenzar de cero.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener { error ->

                    btnPublicarResultados.isEnabled = true

                    Toast.makeText(
                        this,
                        "Evaluaciones eliminadas, pero hubo un error al ocultar resultados: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            return
        }

        val lote = db.batch()

        for (documento in lotes[indice]) {
            lote.delete(documento.reference)
        }

        lote.commit()
            .addOnSuccessListener {
                eliminarLote(lotes, indice + 1)
            }
            .addOnFailureListener { error ->

                btnPublicarResultados.isEnabled = true

                Toast.makeText(
                    this,
                    "Error al eliminar evaluaciones: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // TARJETAS DE VIDEOJUEGOS
    private fun crearTarjetaJuego(
        juegoId: String,
        nombre: String,
        autores: String,
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
                bottomMargin = dp(12)
            }
        }

        val portada = ImageView(this).apply {

            layoutParams = LinearLayout.LayoutParams(
                dp(90),
                dp(115)
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
            textSize = 17f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }

        val tvAutores = TextView(this).apply {
            text = "Autores: $autores"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(8), 0, dp(5))
        }

        val btnEditar = Button(this).apply {

            text = "Editar"
            textSize = 12f
            isAllCaps = false
            setPadding(dp(10), 0, dp(10), 0)

            setOnClickListener {

                val intent = Intent(
                    this@AdminDashboardActivity,
                    AddGameActivity::class.java
                )

                intent.putExtra("juegoId", juegoId)

                startActivity(intent)
            }
        }

        val btnEliminar = Button(this).apply {

            text = "Eliminar"
            textSize = 12f
            isAllCaps = false

            setOnClickListener {

                AlertDialog.Builder(this@AdminDashboardActivity)
                    .setTitle("Eliminar videojuego")
                    .setMessage("¿Deseas retirar '$nombre' del listado?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ ->

                        db.collection("videojuegos")
                            .document(juegoId)
                            .update("activo", false)
                            .addOnSuccessListener {

                                Toast.makeText(
                                    this@AdminDashboardActivity,
                                    "Videojuego eliminado",
                                    Toast.LENGTH_SHORT
                                ).show()

                                cargarVideojuegos()
                            }
                            .addOnFailureListener { error ->

                                Toast.makeText(
                                    this@AdminDashboardActivity,
                                    "Error: ${error.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .show()
            }
        }

        informacion.addView(tvNombre)
        informacion.addView(tvAutores)
        informacion.addView(btnEditar)
        informacion.addView(btnEliminar)

        tarjeta.addView(informacion)

        contenedorJuegos.addView(tarjeta)

        // CARGAR PORTADA
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
                    // Mantener portada vacía
                }
            }.start()
        }
    }

    private fun mostrarMensaje(mensaje: String) {

        val texto = TextView(this).apply {
            text = mensaje
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(dp(20), dp(25), dp(20), dp(25))
        }

        contenedorJuegos.addView(texto)
    }

    private fun dp(valor: Int): Int {
        return (valor * resources.displayMetrics.density).toInt()
    }
}
