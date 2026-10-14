package com.agustin.bebederoiot

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

object SesionManager {

    private const val PREFS = "sesion_prefs"
    private const val KEY_ULTIMA_ACTIVIDAD = "ultima_actividad"

    // Días sin usar la app antes de cerrar la sesión
    private const val DIAS_MAXIMOS_INACTIVIDAD = 7L

    // Para la demostración puedes poner 2 * 60 * 1000L (2 minutos).
    // Después vuelve a dejar la línea de los días.
    private const val LIMITE_MS = DIAS_MAXIMOS_INACTIVIDAD * 24L * 60L * 60L * 1000L

    // Guarda "ahora" como la última vez que se usó la app
    fun registrarActividad(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ULTIMA_ACTIVIDAD, System.currentTimeMillis())
            .apply()
    }

    // true si hay un usuario con sesión iniciada y no pasó el límite de inactividad
    fun sesionVigente(context: Context): Boolean {
        if (FirebaseAuth.getInstance().currentUser == null) return false

        val ultima = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_ULTIMA_ACTIVIDAD, 0L)
        if (ultima == 0L) return false   // sin registro: se pide iniciar sesión

        return (System.currentTimeMillis() - ultima) < LIMITE_MS
    }

    // Cierra la sesión en Firebase y borra el registro local
    fun cerrarSesion(context: Context) {
        FirebaseAuth.getInstance().signOut()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ULTIMA_ACTIVIDAD)
            .apply()
    }
}