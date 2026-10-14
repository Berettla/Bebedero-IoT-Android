package com.agustin.bebederoiot

// Guarda los datos de UN bebedero. Más adelante, Firebase llenará esto.
data class Bebedero(
    val id: String,               // identificador único, ej: "BEB-01"
    val nombre: String,
    val nivel: Int,               // de 0 a 100
    val ultimaConexion: Long      // hora del último aviso del bebedero, en milisegundos
) {
    // Si el bebedero avisó hace menos de 5 minutos, está online
    fun estaOnline(ahora: Long): Boolean {
        return (ahora - ultimaConexion) < UMBRAL_OFFLINE_MS
    }

    companion object {
        const val UMBRAL_OFFLINE_MS = 5 * 60 * 1000L // 5 minutos
        const val NIVEL_BAJO = 20                    // menos de 20% se destaca
    }
}