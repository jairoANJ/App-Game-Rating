
package com.example.appgamerating

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ResultsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var contenedorRanking: LinearLayout
    private lateinit var contenedorResultados: LinearLayout

    private val criterios = listOf(
        "Jugabilidad",
        "Gráficos y diseño visual",
        "Sonido y música",
        "Creatividad y originalidad",
        "Objetivo pedagógico"
    )

    private data class ResultadoJuego(
        val nombre: String,
        var cantidad: Int = 0,
        val sumas: DoubleArray = DoubleArray(5)
    ) {
        fun promedio(): Double {
            return if (cantidad > 0) {
                sumas.sum() / (cantidad * 5)
            } else {
                0.0
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        contenedorRanking = findViewById(R.id.contenedorRanking)
        contenedorResultados = findViewById(R.id.contenedorResultados)

        findViewById<Button>(R.id.btnVolver).setOnClickListener {
            finish()
        }

        verificarPublicacion()
    }

    private fun cargarResultados() {

        contenedorRanking.removeAllViews()
        contenedorResultados.removeAllViews()

        mostrarTexto(contenedorResultados, "Consultando evaluaciones...")

        db.collection("evaluaciones")
            .get()
            .addOnSuccessListener { documentos ->

                contenedorRanking.removeAllViews()
                contenedorResultados.removeAllViews()

                if (documentos.isEmpty) {
                    mostrarTexto(
                        contenedorResultados,
                        "Todavía no hay evaluaciones registradas."
                    )
                    return@addOnSuccessListener
                }

                val resultados = mutableMapOf<String, ResultadoJuego>()

                for (documento in documentos) {

                    val juegoId = documento.getString("juegoId") ?: continue
                    val nombre = documento.getString("nombreJuego") ?: "Videojuego"

                    val resultado = resultados.getOrPut(juegoId) {
                        ResultadoJuego(nombre)
                    }

                    val valores = listOf(
                        "jugabilidad",
                        "graficos",
                        "sonido",
                        "creatividad",
                        "objetivoPedagogico"
                    )

                    resultado.cantidad++

                    for (i in valores.indices) {
                        resultado.sumas[i] +=
                            (documento.get(valores[i]) as? Number)?.toDouble() ?: 0.0
                    }
                }

                val lista = resultados.values
                    .filter { it.cantidad > 0 }
                    .sortedByDescending { it.promedio() }

                if (lista.isEmpty()) {
                    mostrarTexto(
                        contenedorResultados,
                        "No se encontraron evaluaciones válidas."
                    )
                    return@addOnSuccessListener
                }

                mostrarRanking(lista)
                mostrarDetalle(lista)
            }
            .addOnFailureListener { error ->

                contenedorResultados.removeAllViews()

                mostrarTexto(
                    contenedorResultados,
                    "No fue posible cargar los resultados."
                )

                Toast.makeText(
                    this,
                    error.message,
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun mostrarRanking(lista: List<ResultadoJuego>) {

        val primeros = lista.take(3)

        for ((indice, resultado) in primeros.withIndex()) {

            val posicion = when (indice) {
                0 -> "🥇 1.er lugar"
                1 -> "🥈 2.º lugar"
                else -> "🥉 3.er lugar"
            }

            val texto = """
                $posicion
                ${resultado.nombre}
                Promedio: ${formato(resultado.promedio())} / 5
                Evaluaciones: ${resultado.cantidad}
            """.trimIndent()

            agregarTarjeta(contenedorRanking, texto)
        }
    }

    private fun mostrarDetalle(lista: List<ResultadoJuego>) {

        for (resultado in lista) {

            val texto = StringBuilder()

            texto.append("${resultado.nombre}\n")
            texto.append("Promedio general: ${formato(resultado.promedio())} / 5\n")
            texto.append("Evaluaciones: ${resultado.cantidad}\n\n")

            for (i in criterios.indices) {

                val promedioCriterio =
                    resultado.sumas[i] / resultado.cantidad

                texto.append(
                    "${criterios[i]}: ${formato(promedioCriterio)} / 5\n"
                )
            }

            agregarTarjeta(contenedorResultados, texto.toString())
        }
    }

    private fun agregarTarjeta(
        contenedor: LinearLayout,
        contenido: String
    ) {

        val tarjeta = TextView(this).apply {

            text = contenido
            textSize = 15f
            setTextColor(Color.rgb(35, 35, 35))
            setTypeface(null, Typeface.NORMAL)
            setPadding(18, 18, 18, 18)

            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18f
                setStroke(1, Color.LTGRAY)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        contenedor.addView(tarjeta)
    }

    private fun mostrarTexto(
        contenedor: LinearLayout,
        mensaje: String
    ) {

        val texto = TextView(this).apply {
            text = mensaje
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(16, 24, 16, 24)
        }

        contenedor.addView(texto)
    }

    private fun formato(valor: Double): String {
        return String.format(Locale.getDefault(), "%.2f", valor)
    }

    private fun verificarPublicacion() {

        val esEstudiante = intent.getBooleanExtra(
            "origenEstudiante",
            false
        )

        // El administrador puede consultar siempre
        if (!esEstudiante) {
            cargarResultados()
            return
        }

        // Verificar publicación en Firebase
        db.collection("configuracion")
            .document("resultados")
            .get()
            .addOnSuccessListener { documento ->

                val publicados =
                    documento.getBoolean("publicados") ?: false

                if (publicados) {
                    cargarResultados()
                } else {
                    Toast.makeText(
                        this,
                        "Los resultados todavía no están disponibles",
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                }
            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "No fue posible verificar los resultados",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
    }
}
