package com.agustin.bebederoiot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Un evento del historial. Más adelante vendrá de Firebase.
data class EventoBebedero(
    val descripcion: String,
    val destacado: Boolean   // true = punto turquesa, false = punto gris
)

class MetricasBebederoActivity : AppCompatActivity() {

    companion object {
        // Nombres con los que la lista "empaqueta" los datos para esta pantalla
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NOMBRE = "extra_nombre"
        const val EXTRA_NIVEL = "extra_nivel"
        const val EXTRA_ULTIMA_CONEXION = "extra_ultima_conexion"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metricas_bebedero)

        // Deja espacio para las barras del teléfono
        val raiz = findViewById<View>(R.id.rootMetricas)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        // Recibe los datos del bebedero que se tocó en la lista
        val nombre = intent.getStringExtra(EXTRA_NOMBRE) ?: "Bebedero"
        val nivel = intent.getIntExtra(EXTRA_NIVEL, 0).coerceIn(0, 100)
        val ultimaConexion = intent.getLongExtra(EXTRA_ULTIMA_CONEXION, 0L)

        // Muestra los datos en pantalla
        findViewById<TextView>(R.id.tvTituloBebedero).text = nombre
        findViewById<ProgressBar>(R.id.pbNivel).progress = nivel
        findViewById<TextView>(R.id.tvNivelPorcentaje).text = "$nivel%"
        findViewById<TextView>(R.id.tvUltimaLectura).text =
            "Última lectura: ${textoUltimaLectura(ultimaConexion)}"

        // Botón de volver (la flecha de arriba)
        findViewById<ImageButton>(R.id.btnVolver).setOnClickListener { finish() }

        // Interruptor de la bomba. Por ahora solo cambia visualmente.
        // Más adelante enviará la orden a Firebase.
        val swBomba = findViewById<SwitchCompat>(R.id.swBomba)
        swBomba.setOnCheckedChangeListener { _, _ -> }

        mostrarEventos(eventosDePrueba(ultimaConexion))
    }

    // Convierte la hora de la última conexión en un texto legible
    private fun textoUltimaLectura(ultimaConexion: Long): String {
        if (ultimaConexion <= 0L) return "sin lecturas"
        val diferencia = System.currentTimeMillis() - ultimaConexion
        val minuto = 60 * 1000L
        val hora = 60 * minuto
        val dia = 24 * hora
        return when {
            diferencia < minuto -> "hace un momento"
            diferencia < hora -> "hace ${diferencia / minuto} min"
            diferencia < dia -> "hace ${diferencia / hora} h"
            else -> "hace ${diferencia / dia} días"
        }
    }

    // Crea una fila por cada evento, usando el molde item_evento.xml
    private fun mostrarEventos(eventos: List<EventoBebedero>) {
        val contenedor = findViewById<LinearLayout>(R.id.llEventos)
        val tvSinEventos = findViewById<TextView>(R.id.tvSinEventos)

        contenedor.removeAllViews()
        tvSinEventos.visibility = if (eventos.isEmpty()) View.VISIBLE else View.GONE

        for (evento in eventos) {
            val fila = LayoutInflater.from(this)
                .inflate(R.layout.item_evento, contenedor, false)
            fila.findViewById<TextView>(R.id.tvEvento).text = evento.descripcion
            fila.findViewById<View>(R.id.vPuntoEvento).setBackgroundResource(
                if (evento.destacado) R.drawable.dot_turquesa else R.drawable.dot_gris
            )
            contenedor.addView(fila)
        }
    }

    // DATOS TEMPORALES: se borrarán cuando conectemos Firebase
    private fun eventosDePrueba(ultimaConexion: Long): List<EventoBebedero> {
        if (ultimaConexion <= 0L) return emptyList()
        val formato = SimpleDateFormat("dd-MMM HH:mm", Locale("es", "CL"))
        fun hora(milisegundos: Long) = formato.format(Date(milisegundos))
        val minuto = 60 * 1000L
        return listOf(
            EventoBebedero("${hora(ultimaConexion)} - Lectura recibida", true),
            EventoBebedero("${hora(ultimaConexion - 5 * minuto)} - Bomba encendida", true),
            EventoBebedero("${hora(ultimaConexion - 90 * minuto)} - Nivel bajó", false)
        )
    }
}