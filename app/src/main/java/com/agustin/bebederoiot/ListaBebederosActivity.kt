package com.agustin.bebederoiot

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

class ListaBebederosActivity : AppCompatActivity() {

    companion object {
        // Cada cuánto se vuelve a calcular Online/Offline (30 segundos)
        private const val REFRESCO_MS = 30_000L
    }

    private lateinit var adapter: BebederoAdapter
    private lateinit var rvBebederos: RecyclerView
    private lateinit var llListaVacia: View
    private lateinit var raiz: View

    // La lista de bebederos que se muestra. Firebase la llena.
    private val bebederos = mutableListOf<Bebedero>()

    // Evita mostrar "Aún no tienes bebederos" antes de que lleguen los datos
    private var datosCargados = false

    private var refBebederos: DatabaseReference? = null
    private val handler = Handler(Looper.getMainLooper())

    // Cada 30 segundos vuelve a dibujar las tarjetas para recalcular Online/Offline
    private val refrescar = object : Runnable {
        override fun run() {
            adapter.actualizarLista(bebederos.toList())
            handler.postDelayed(this, REFRESCO_MS)
        }
    }

    // El "oído" de Firebase: se ejecuta al iniciar y cada vez que cambia algo
    private val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val nueva = snapshot.children
                .mapNotNull { leerBebedero(it) }
                .sortedBy { it.nombre.lowercase() }

            bebederos.clear()
            bebederos.addAll(nueva)
            adapter.actualizarLista(bebederos.toList())

            datosCargados = true
            actualizarEstadoVacio()
        }

        override fun onCancelled(error: DatabaseError) {
            mostrarMensaje("No se pudieron cargar los bebederos")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_bebederos)

        // Deja espacio para las barras del teléfono
        raiz = findViewById(R.id.rootLista)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            insets
        }

        llListaVacia = findViewById(R.id.llListaVacia)
        rvBebederos = findViewById(R.id.rvBebederos)

        adapter = BebederoAdapter(bebederos.toList()) { bebedero -> abrirMetricas(bebedero) }
        rvBebederos.adapter = adapter

        findViewById<ImageButton>(R.id.btnAddDevice).setOnClickListener {
            mostrarDialogoVincular()
        }

        // Engranaje: abre el modal de configuración
        findViewById<ImageButton>(R.id.btnConfig).setOnClickListener {
            mostrarDialogoConfiguracion()
        }
    }

    // La pantalla se hace visible: revisa la sesión y empieza a escuchar a Firebase
    override fun onStart() {
        super.onStart()

        if (!SesionManager.sesionVigente(this)) {
            volverAlLogin()
            return
        }
        SesionManager.registrarActividad(this)

        val ref = referenciaBebederos() ?: run {
            volverAlLogin()
            return
        }
        refBebederos = ref
        ref.addValueEventListener(listener)
        handler.postDelayed(refrescar, REFRESCO_MS)
    }

    // La pantalla deja de verse: deja de escuchar (ahorra batería y datos)
    override fun onStop() {
        super.onStop()
        detenerEscucha()
    }

    private fun detenerEscucha() {
        refBebederos?.removeEventListener(listener)
        refBebederos = null
        handler.removeCallbacks(refrescar)
    }

    // Dirección de los bebederos de ESTE usuario: usuarios/{uid}/bebederos
    private fun referenciaBebederos(): DatabaseReference? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return FirebaseDatabase.getInstance().reference
            .child("usuarios")
            .child(uid)
            .child("bebederos")
    }

    // Convierte un nodo de Firebase en un Bebedero. Tolera datos mal escritos.
    private fun leerBebedero(nodo: DataSnapshot): Bebedero? {
        val id = nodo.key ?: return null

        val nombre = nodo.child("nombre").getValue(String::class.java)
            ?.takeIf { it.isNotBlank() } ?: id

        val nivel = aNumero(nodo.child("nivel").value)
            ?.toInt()?.coerceIn(0, 100) ?: 0

        val ultimaConexion = aNumero(nodo.child("ultima_conexion").value) ?: 0L

        return Bebedero(id, nombre, nivel, ultimaConexion)
    }

    // Acepta números y también textos como "80"; si no se entiende, devuelve null
    private fun aNumero(valor: Any?): Long? = when (valor) {
        is Number -> valor.toLong()
        is String -> valor.trim().toLongOrNull()
        else -> null
    }

    // Cierra la sesión y vuelve al login, borrando las pantallas anteriores
    private fun volverAlLogin() {
        detenerEscucha()   // antes de cerrar sesión, para evitar errores de permisos
        SesionManager.cerrarSesion(this)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Muestra "lista vacía" solo cuando ya llegaron los datos y no hay ninguno
    private fun actualizarEstadoVacio() {
        llListaVacia.visibility =
            if (datosCargados && bebederos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun mostrarMensaje(texto: String) {
        Snackbar.make(raiz, texto, Snackbar.LENGTH_LONG).show()
    }

    private fun abrirMetricas(bebedero: Bebedero) {
        val intent = Intent(this, MetricasBebederoActivity::class.java)
        intent.putExtra(MetricasBebederoActivity.EXTRA_ID, bebedero.id)
        intent.putExtra(MetricasBebederoActivity.EXTRA_NOMBRE, bebedero.nombre)
        intent.putExtra(MetricasBebederoActivity.EXTRA_NIVEL, bebedero.nivel)
        intent.putExtra(MetricasBebederoActivity.EXTRA_ULTIMA_CONEXION, bebedero.ultimaConexion)
        startActivity(intent)
    }

    // ---------- Modal de configuración (engranaje) ----------

    private fun mostrarDialogoConfiguracion() {
        val vista = LayoutInflater.from(this)
            .inflate(R.layout.dialog_configuracion, null)

        // Muestra el correo de la cuenta con la que se inició sesión
        val correo = FirebaseAuth.getInstance().currentUser?.email
        vista.findViewById<TextView>(R.id.tvCorreoCuenta).text =
            if (correo.isNullOrBlank()) "Cuenta sin correo" else correo

        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        vista.findViewById<View>(R.id.btnCerrarConfig).setOnClickListener {
            dialogo.dismiss()
        }
        vista.findViewById<View>(R.id.btnCerrarSesion).setOnClickListener {
            dialogo.dismiss()
            mostrarConfirmacionCierre()
        }

        dialogo.show()
    }

    // Pregunta antes de cerrar la sesión, para evitar toques accidentales
    private fun mostrarConfirmacionCierre() {
        val vista = LayoutInflater.from(this)
            .inflate(R.layout.dialog_confirmar_cierre, null)

        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        vista.findViewById<View>(R.id.btnCancelarCierre).setOnClickListener {
            dialogo.dismiss()
        }
        vista.findViewById<View>(R.id.btnConfirmarCierre).setOnClickListener {
            dialogo.dismiss()
            volverAlLogin()
        }

        dialogo.show()
    }

    // ---------- Modal de vinculación ("+") ----------

    private fun mostrarDialogoVincular() {
        val vista = LayoutInflater.from(this)
            .inflate(R.layout.dialog_vincular_equipo, null)

        val etId = vista.findViewById<EditText>(R.id.etIdDispositivo)
        val etNombre = vista.findViewById<EditText>(R.id.etNombreBebedero)
        val btnCancelar = vista.findViewById<View>(R.id.btnCancelar)
        val btnConectar = vista.findViewById<View>(R.id.btnConectar)

        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .create()

        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener { dialogo.dismiss() }

        btnConectar.setOnClickListener {
            val id = etId.text.toString().trim().uppercase()
            val nombre = etNombre.text.toString().trim()

            if (id.isEmpty()) {
                etId.error = "Ingresa el ID del dispositivo"
                return@setOnClickListener
            }
            // Firebase no admite símbolos como . $ # [ ] / en los identificadores
            if (!id.matches(Regex("^[A-Z0-9_-]+$"))) {
                etId.error = "Usa solo letras, números, guion (-) o guion bajo (_)"
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

            val ref = referenciaBebederos()
            if (ref == null) {
                mostrarMensaje("Tu sesión no es válida. Inicia sesión de nuevo")
                return@setOnClickListener
            }

            // Guarda el bebedero en Firebase. El listener lo hará aparecer en la lista.
            val datos = mapOf(
                "nombre" to nombre,
                "nivel" to 0,
                "ultima_conexion" to ServerValue.TIMESTAMP   // hora del servidor de Firebase
            )
            ref.child(id).setValue(datos)
                .addOnFailureListener { mostrarMensaje("No se pudo guardar el bebedero") }

            dialogo.dismiss()
        }

        dialogo.show()
    }
}