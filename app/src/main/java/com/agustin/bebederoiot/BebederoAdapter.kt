package com.agustin.bebederoiot

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BebederoAdapter(
    private var bebederos: List<Bebedero>,
    private val onItemClick: (Bebedero) -> Unit
) : RecyclerView.Adapter<BebederoAdapter.BebederoViewHolder>() {

    class BebederoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreBebedero)
        val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoConexion)
        val tvNivel: TextView = itemView.findViewById(R.id.tvNivelAgua)
        val llPill: View = itemView.findViewById(R.id.llEstadoPill)
        val puntoEstado: View = itemView.findViewById(R.id.vPuntoEstado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BebederoViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bebedero, parent, false)
        return BebederoViewHolder(vista)
    }

    override fun onBindViewHolder(holder: BebederoViewHolder, position: Int) {
        val bebedero = bebederos[position]
        val online = bebedero.estaOnline(System.currentTimeMillis())

        holder.tvNombre.text = bebedero.nombre
        holder.tvNivel.text = "${bebedero.nivel}%"

        if (online) {
            holder.tvEstado.text = "Online"
            holder.tvEstado.setTextColor(Color.parseColor("#4CAF50"))
            holder.llPill.setBackgroundResource(R.drawable.bg_pill_online)
            holder.puntoEstado.setBackgroundResource(R.drawable.dot_online)
            holder.itemView.alpha = 1f
        } else {
            holder.tvEstado.text = "Offline"
            holder.tvEstado.setTextColor(Color.parseColor("#F44336"))
            holder.llPill.setBackgroundResource(R.drawable.bg_pill_offline)
            holder.puntoEstado.setBackgroundResource(R.drawable.dot_offline)
            holder.itemView.alpha = 0.6f   // tarjeta atenuada
        }

        // Nivel bajo: el porcentaje se pone rojo. Solo si está online,
        // porque un equipo offline tiene un dato que puede estar desactualizado.
        val nivelBajo = online && bebedero.nivel < Bebedero.NIVEL_BAJO
        holder.tvNivel.setTextColor(Color.parseColor(if (nivelBajo) "#F44336" else "#FFFFFF"))

        holder.itemView.setOnClickListener { onItemClick(bebedero) }
    }

    override fun getItemCount(): Int = bebederos.size

    fun actualizarLista(nuevaLista: List<Bebedero>) {
        bebederos = nuevaLista
        notifyDataSetChanged()
    }
}