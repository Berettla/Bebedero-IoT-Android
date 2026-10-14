package com.agustin.bebederoiot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
class ListaBebederosActivity : AppCompatActivity() {

    private lateinit var adapter: BebederoAdapter
    private lateinit var rvBebederos: RecyclerView

    // La lista "viva" de bebederos. Aquí se agregan los nuevos.
    private val bebederos = mutableListOf<Bebedero>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_bebederos)

        // Deja espacio para las barras del teléfono
        val raiz = findViewById<View>(R.id.rootLista)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        // Carga los datos de prueba y conecta la lista con el adapter
        bebederos.addAll(datosDePrueba())
        rvBebederos = findViewById(R.id.rvBebederos)
        adapter = BebederoAdapter(bebederos.toList()) { bebedero -> abrirMetricas(bebedero) }
        rvBebederos.adapter = adapter

        // Botón "+": abre el modal de vinculación
        val btnAddDevice = findViewById<ImageButton>(R.id.btnAddDevice)
        btnAddDevice.setOnClickListener { mostrarDialogoVincular() }
    }

    private fun abrirMetricas(bebedero: Bebedero) {
        val intent = Intent(this, MetricasBebederoActivity::class.java)
        intent.putExtra(MetricasBebederoActivity.EXTRA_ID, bebedero.id)
        intent.putExtra(MetricasBebederoActivity.EXTRA_NOMBRE, bebedero.nombre)
        intent.putExtra(MetricasBebederoActivity.EXTRA_NIVEL, bebedero.nivel)
        intent.putExtra(MetricasBebederoActivity.EXTRA_ULTIMA_CONEXION, bebedero.ultimaConexion)
        startActivity(intent)
    }
    private fun mostrarDialogoVincular() {
        // Convierte el XML del modal en vistas que podemos usar
        val vista = LayoutInflater.from(this)
            .inflate(R.layout.dialog_vincular_equipo, null)

        val etId = vista.findViewById<EditText>(R.id.etIdDispositivo)
        val etNombre = vista.findViewById<EditText>(R.id.etNombreBebedero)
        val btnCancelar = vista.findViewById<View>(R.id.btnCancelar)
        val btnConectar = vista.findViewById<View>(R.id.btnConectar)

        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .create()

        // Fondo transparente para que se vean las esquinas redondeadas del modal
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener { dialogo.dismiss() }

        btnConectar.setOnClickListener {
            val id = etId.text.toString().trim()
            val nombre = etNombre.text.toString().trim()

            // Validaciones: ningún campo vacío y ID no repetido
            if (id.isEmpty()) {
                etId.error = "Ingresa el ID del dispositivo"
                return@setOnClickListener
            }
            if (nombre.isEmpty()) {
                etNombre.error = "Ingresa un nombre para el bebedero"
                return@setOnClickListener
            }
            if (bebederos.any { it.id.equals(id, ignoreCase = true) }) {
                etId.error = "Ya existe un bebedero con ese ID"
                return@setOnClickListener
            }

            // TEMPORAL: agrega el bebedero a la lista de prueba.
            // Cuando conectemos Firebase, aquí se guardará en la base de datos.
            bebederos.add(
                Bebedero(
                    id = id,
                    nombre = nombre,
                    nivel = 0,
                    ultimaConexion = System.currentTimeMillis()
                )
            )
            adapter.actualizarLista(bebederos.toList())
            rvBebederos.scrollToPosition(bebederos.size - 1)
            dialogo.dismiss()
        }

        dialogo.show()
    }

    // DATOS TEMPORALES: se borrarán cuando conectemos Firebase
    private fun datosDePrueba(): List<Bebedero> {
        val ahora = System.currentTimeMillis()
        val minuto = 60 * 1000L
        return listOf(
            Bebedero("BEB-01", "Bebedero Principal", 80, ahora - 1 * minuto),   // online
            Bebedero("BEB-02", "Bebedero Patio", 35, ahora - 30 * minuto),      // offline
            Bebedero("BEB-03", "Bebedero Jardín", 15, ahora - 2 * minuto)       // online, nivel bajo
        )
    }
}