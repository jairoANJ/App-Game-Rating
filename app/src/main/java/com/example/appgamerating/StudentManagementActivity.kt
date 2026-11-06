
package com.example.appgamerating

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class StudentManagementActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var contenedorEstudiantes: LinearLayout
    private lateinit var tvResumen: TextView
    private lateinit var btnImportar: Button

    private val selectorCSV = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) importarCSV(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_management)

        contenedorEstudiantes =
            findViewById(R.id.contenedorEstudiantes)

        tvResumen =
            findViewById(R.id.tvResumenEstudiantes)

        btnImportar =
            findViewById(R.id.btnImportarEstudiantes)

        findViewById<Button>(
            R.id.btnVolverEstudiantes
        ).setOnClickListener {
            finish()
        }

        btnImportar.setOnClickListener {
            selectorCSV.launch(
                arrayOf("text/*", "application/octet-stream")
            )
        }

        cargarEstudiantes()
    }

    // CARGAR ESTUDIANTES
    private fun cargarEstudiantes() {

        contenedorEstudiantes.removeAllViews()
        tvResumen.text = "Cargando estudiantes..."

        db.collection("estudiantes")
            .get()
            .addOnSuccessListener { documentos ->

                contenedorEstudiantes.removeAllViews()

                var habilitados = 0
                var deshabilitados = 0

                for (documento in documentos) {

                    val codigo = documento.id
                    val nombre =
                        documento.getString("nombre") ?: "Sin nombre"

                    val activo =
                        documento.getBoolean("activo") ?: false

                    if (activo) habilitados++ else deshabilitados++

                    crearTarjeta(codigo, nombre, activo)
                }

                tvResumen.text =
                    "Total: ${documentos.size()} | " +
                            "Habilitados: $habilitados | " +
                            "Deshabilitados: $deshabilitados"
            }
            .addOnFailureListener { error ->
                tvResumen.text = "Error al cargar estudiantes: ${error.message}"
            }
    }

    // IMPORTAR ARCHIVO CSV
    private fun importarCSV(uri: Uri) {

        btnImportar.isEnabled = false
        tvResumen.text = "Leyendo archivo..."

        try {

            val contenido = contentResolver
                .openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw Exception("No fue posible leer el archivo")

            val lineas = contenido
                .removePrefix("\uFEFF")
                .lines()
                .filter { it.isNotBlank() }

            if (lineas.size < 2) {
                throw Exception("El archivo no contiene estudiantes")
            }

            val estudiantes = mutableListOf<Triple<String, String, Boolean>>()

            for (i in 1 until lineas.size) {

                val columnas = lineas[i].split(";")

                if (columnas.size < 3) {
                    throw Exception("Error en la fila ${i + 1}")
                }

                val codigo = columnas[0].trim()
                val nombre = columnas[1].trim()
                val activo = columnas[2].trim().lowercase() == "true"

                if (codigo.isEmpty() || nombre.isEmpty()) {
                    throw Exception("Datos incompletos en la fila ${i + 1}")
                }

                estudiantes.add(Triple(codigo, nombre, activo))
            }

            registrarEstudiantes(estudiantes)

        } catch (error: Exception) {

            btnImportar.isEnabled = true

            Toast.makeText(
                this,
                "Error al importar: ${error.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // REGISTRAR SOLAMENTE LOS ESTUDIANTES NUEVOS
    private fun registrarEstudiantes(
        estudiantes: List<Triple<String, String, Boolean>>
    ) {

        db.collection("estudiantes")
            .get()
            .addOnSuccessListener { existentes ->

                val codigosExistentes = existentes.documents
                    .map { it.id }
                    .toSet()

                val nuevos = estudiantes.filter {
                    it.first !in codigosExistentes
                }

                if (nuevos.isEmpty()) {

                    btnImportar.isEnabled = true

                    Toast.makeText(
                        this,
                        "Todos los estudiantes ya estaban registrados",
                        Toast.LENGTH_LONG
                    ).show()

                    cargarEstudiantes()
                    return@addOnSuccessListener
                }

                val lotes = nuevos.chunked(400)
                guardarLote(lotes, 0, nuevos.size)
            }
            .addOnFailureListener { error ->

                btnImportar.isEnabled = true

                Toast.makeText(
                    this,
                    "Error consultando Firestore: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // GUARDAR POR LOTES
    private fun guardarLote(
        lotes: List<List<Triple<String, String, Boolean>>>,
        indice: Int,
        totalNuevos: Int
    ) {

        if (indice >= lotes.size) {

            btnImportar.isEnabled = true

            Toast.makeText(
                this,
                "$totalNuevos estudiantes importados correctamente",
                Toast.LENGTH_LONG
            ).show()

            cargarEstudiantes()
            return
        }

        val lote = db.batch()

        for ((codigo, nombre, activo) in lotes[indice]) {

            val referencia = db.collection("estudiantes")
                .document(codigo)

            lote.set(
                referencia,
                mapOf(
                    "nombre" to nombre,
                    "activo" to activo
                )
            )
        }

        tvResumen.text = "Importando estudiantes..."

        lote.commit()
            .addOnSuccessListener {
                guardarLote(lotes, indice + 1, totalNuevos)
            }
            .addOnFailureListener { error ->

                btnImportar.isEnabled = true

                Toast.makeText(
                    this,
                    "Error guardando estudiantes: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()

                cargarEstudiantes()
            }
    }

    // CREAR TARJETA
    private fun crearTarjeta(
        codigo: String,
        nombre: String,
        activo: Boolean
    ) {

        val tarjeta = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)

            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 18f
                setStroke(1, Color.LTGRAY)
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        }

        val tvNombre = TextView(this).apply {
            text = nombre
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }

        val tvCodigo = TextView(this).apply {
            text = "Código: $codigo"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 5, 0, 5)
        }

        val tvEstado = TextView(this).apply {
            text = if (activo) "Estado: Habilitado"
            else "Estado: Deshabilitado"

            textSize = 14f
            setTextColor(
                if (activo) Color.rgb(40, 130, 60)
                else Color.rgb(190, 45, 45)
            )
        }

        val btnEstado = Button(this).apply {

            text = if (activo) "Deshabilitar" else "Habilitar"
            isAllCaps = false

            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    if (activo) Color.rgb(190, 45, 45)
                    else Color.rgb(50, 135, 75)
                )
            )

            setOnClickListener {
                cambiarEstado(codigo, !activo)
            }
        }

        tarjeta.addView(tvNombre)
        tarjeta.addView(tvCodigo)
        tarjeta.addView(tvEstado)
        tarjeta.addView(btnEstado)

        contenedorEstudiantes.addView(tarjeta)
    }

    // CAMBIAR ESTADO
    private fun cambiarEstado(
        codigo: String,
        nuevoEstado: Boolean
    ) {

        db.collection("estudiantes")
            .document(codigo)
            .update("activo", nuevoEstado)
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    if (nuevoEstado) "Estudiante habilitado"
                    else "Estudiante deshabilitado",
                    Toast.LENGTH_SHORT
                ).show()

                cargarEstudiantes()
            }
            .addOnFailureListener { error ->

                Toast.makeText(
                    this,
                    "Error: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}
