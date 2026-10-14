package com.agustin.bebederoiot

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Un evento del historial.
data class EventoBebedero(
    val descripcion: String,
    val destacado: Boolean,   // true = punto turquesa, false = punto gris
    val timestamp: Long = 0L  // hora del evento, en milisegundos
)

class MetricasBebederoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_NOMBRE = "extra_nombre"
        const val EXTRA_NIVEL = "extra_nivel"
        const val EXTRA_ULTIMA_CONEXION = "extra_ultima_conexion"

        private const val REFRESCO_MS = 30_000L   // recalcula "hace X min" y Online/Offline
        private const val MAX_EVENTOS = 10        // cuántos eventos se muestran

        // Con false, la bomba no se puede controlar si el bebedero está offline.
        // Ponlo en true si quieres probar la bomba sin mantener la hora al día.
        private const val PERMITIR_BOMBA_OFFLINE = false
    }

    private lateinit var raiz: View
    private lateinit var pbNivel: ProgressBar
    private lateinit var tvNivel: TextView
    private lateinit var tvTitulo: TextView
    private lateinit var tvUltimaLectura: TextView
    private lateinit var swBomba: SwitchCompat

    private var bebederoId = ""
    private var refBebedero: DatabaseReference? = null
    private val handler = Handler(Looper.getMainLooper())

    // Datos actuales del bebedero (se actualizan con Firebase)
    private var nombre = "Bebedero"
    private var nivel = 0
    private var ultimaConexion = 0L
    private var bombaEncendida = false

    // Para animar el anillo
    private var nivelMostrado = 0
    private var nivelObjetivo = -1
    private var animador: ValueAnimator? = null

    // Cada 30 segundos vuelve a calcular los textos que dependen de la hora
    private val refrescar = object : Runnable {
        override fun run() {
            mostrarDatos()
            handler.postDelayed(this, REFRESCO_MS)
        }
    }

    // Qué pasa cuando el usuario toca el interruptor
    private val alCambiarBomba = CompoundButton.OnCheckedChangeListener { _, encendida ->
        cambiarBomba(encendida)
    }

    // El "oído" de Firebase: se ejecuta al iniciar y cada vez que cambia algo del bebedero
    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if (!snapshot.exists()) {
                Toast.makeText(
                    this@MetricasBebederoActivity,
                    "Este bebedero ya no existe",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }

            nombre = snapshot.child("nombre").getValue(String::class.java)
                ?.takeIf { it.isNotBlank() } ?: bebederoId
            nivel = aNumero(snapshot.child("nivel").value)
                ?.toInt()?.coerceIn(0, 100) ?: 0
            ultimaConexion = aNumero(snapshot.child("ultima_conexion").value) ?: 0L
            bombaEncendida = aBooleano(snapshot.child("bomba").value)

            mostrarEventos(leerEventos(snapshot.child("eventos")))
            mostrarDatos()
        }

        override fun onCancelled(error: DatabaseError) {
            mostrarMensaje("No se pudieron cargar los datos del bebedero")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metricas_bebedero)

        // Deja espacio para las barras del teléfono
        raiz = findViewById(R.id.rootMetricas)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        pbNivel = findViewById(R.id.pbNivel)
        tvNivel = findViewById(R.id.tvNivelPorcentaje)
        tvTitulo = findViewById(R.id.tvTituloBebedero)
        tvUltimaLectura = findViewById(R.id.tvUltimaLectura)
        swBomba = findViewById(R.id.swBomba)

        // Datos iniciales que manda la lista (se corrigen en cuanto llega Firebase)
        bebederoId = intent.getStringExtra(EXTRA_ID) ?: ""
        nombre = intent.getStringExtra(EXTRA_NOMBRE) ?: "Bebedero"
        nivel = intent.getIntExtra(EXTRA_NIVEL, 0).coerceIn(0, 100)
        ultimaConexion = intent.getLongExtra(EXTRA_ULTIMA_CONEXION, 0L)

        tvNivel.text = "0%"
        pbNivel.progress = 0
        swBomba.setOnCheckedChangeListener(alCambiarBomba)
        findViewById<ImageButton>(R.id.btnVolver).setOnClickListener { finish() }

        mostrarDatos()
    }

    // La pantalla se hace visible: revisa la sesión y empieza a escuchar a Firebase
    override fun onStart() {
        super.onStart()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || bebederoId.isEmpty() || !SesionManager.sesionVigente(this)) {
            // Al cerrar esta pantalla, la lista revisa la sesión y lleva al login si hace falta
            finish()
            return
        }
        SesionManager.registrarActividad(this)

        val ref = FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(uid)
            .child("bebederos")
            .child(bebederoId)
        refBebedero = ref
        ref.addValueEventListener(listener)
        handler.postDelayed(refrescar, REFRESCO_MS)
    }

    // La pantalla deja de verse: deja de escuchar
    override fun onStop() {
        super.onStop()
        refBebedero?.removeEventListener(listener)
        refBebedero = null
        handler.removeCallbacks(refrescar)
        animador?.cancel()
        nivelObjetivo = -1   // al volver, el anillo se anima hasta el valor actual
    }

    // ---------- Bomba ----------

    private fun cambiarBomba(encendida: Boolean) {
        val ref = refBebedero
        if (ref == null) {
            mostrarMensaje("No hay conexión con el bebedero")
            ponerSwitch(bombaEncendida)
            return
        }
        if (!PERMITIR_BOMBA_OFFLINE && !estaOnline()) {
            mostrarMensaje("El bebedero está offline. No se puede controlar la bomba")
            ponerSwitch(bombaEncendida)
            return
        }

        val claveEvento = ref.child("eventos").push().key
        if (claveEvento == null) {
            mostrarMensaje("No se pudo enviar la orden")
            ponerSwitch(bombaEncendida)
            return
        }

        // Una sola escritura guarda la orden y el evento a la vez:
        // o se guardan los dos, o ninguno.
        val cambios = mapOf<String, Any>(
            "bomba" to encendida,
            "eventos/$claveEvento" to mapOf(
                "descripcion" to if (encendida) "Bomba encendida" else "Bomba apagada",
                "timestamp" to ServerValue.TIMESTAMP,   // hora del servidor de Firebase
                "destacado" to encendida
            )
        )
        ref.updateChildren(cambios)
            .addOnFailureListener { mostrarMensaje("No se pudo enviar la orden a la bomba") }
    }

    // Mueve el interruptor sin que cuente como un toque del usuario
    private fun ponerSwitch(valor: Boolean) {
        swBomba.setOnCheckedChangeListener(null)
        swBomba.isChecked = valor
        swBomba.setOnCheckedChangeListener(alCambiarBomba)
    }

    // ---------- Mostrar datos ----------

    private fun mostrarDatos() {
        tvTitulo.text = nombre
        tvUltimaLectura.text = "Última lectura: ${textoUltimaLectura(ultimaConexion)}"

        // Nivel bajo: el porcentaje se pone rojo (solo si el bebedero está online)
        val nivelBajo = estaOnline() && nivel < Bebedero.NIVEL_BAJO
        tvNivel.setTextColor(Color.parseColor(if (nivelBajo) "#F44336" else "#FFFFFF"))

        animarNivel(nivel)
        ponerSwitch(bombaEncendida)
    }

    private fun estaOnline(): Boolean {
        return ultimaConexion > 0L &&
                (System.currentTimeMillis() - ultimaConexion) < Bebedero.UMBRAL_OFFLINE_MS
    }

    // Anima el anillo y el número desde el valor que se ve hasta el nuevo nivel
    private fun animarNivel(destino: Int) {
        if (destino == nivelObjetivo) return
        nivelObjetivo = destino

        animador?.cancel()
        animador = ValueAnimator.ofInt(nivelMostrado, destino).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                nivelMostrado = it.animatedValue as Int
                pbNivel.progress = nivelMostrado
                tvNivel.text = "$nivelMostrado%"
            }
            start()
        }
    }

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

    // ---------- Historial de eventos ----------

    // Convierte los eventos de Firebase en una lista: el más reciente primero
    private fun leerEventos(nodo: DataSnapshot): List<EventoBebedero> {
        return nodo.children
            .mapNotNull { e ->
                val descripcion = e.child("descripcion").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val timestamp = aNumero(e.child("timestamp").value) ?: 0L
                val destacado = e.child("destacado").value?.let { aBooleano(it) } ?: true
                EventoBebedero(descripcion, destacado, timestamp)
            }
            .sortedByDescending { it.timestamp }
            .take(MAX_EVENTOS)
    }

    // Crea una fila por cada evento, usando el molde item_evento.xml
    private fun mostrarEventos(eventos: List<EventoBebedero>) {
        val contenedor = findViewById<LinearLayout>(R.id.llEventos)
        val tvSinEventos = findViewById<TextView>(R.id.tvSinEventos)

        contenedor.removeAllViews()
        tvSinEventos.visibility = if (eventos.isEmpty()) View.VISIBLE else View.GONE

        val formato = SimpleDateFormat("dd-MMM HH:mm", Locale.forLanguageTag("es-CL"))

        for (evento in eventos) {
            val texto = if (evento.timestamp > 0L) {
                val hora = formato.format(Date(evento.timestamp)).replace(".", "")
                "$hora - ${evento.descripcion}"
            } else {
                evento.descripcion
            }

            val fila = LayoutInflater.from(this)
                .inflate(R.layout.item_evento, contenedor, false)
            fila.findViewById<TextView>(R.id.tvEvento).text = texto
            fila.findViewById<View>(R.id.vPuntoEvento).setBackgroundResource(
                if (evento.destacado) R.drawable.dot_turquesa else R.drawable.dot_gris
            )
            contenedor.addView(fila)
        }
    }

    // ---------- Utilidades ----------

    // Acepta números y también textos como "80"; si no se entiende, devuelve null
    private fun aNumero(valor: Any?): Long? = when (valor) {
        is Number -> valor.toLong()
        is String -> valor.trim().toLongOrNull()
        else -> null
    }

    // Acepta true/false y también los textos "true"/"false"
    private fun aBooleano(valor: Any?): Boolean = when (valor) {
        is Boolean -> valor
        is String -> valor.trim().equals("true", ignoreCase = true)
        else -> false
    }

    private fun mostrarMensaje(texto: String) {
        Snackbar.make(raiz, texto, Snackbar.LENGTH_LONG).show()
    }
}