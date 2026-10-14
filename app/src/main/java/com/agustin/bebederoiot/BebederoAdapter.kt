package com.agustin.bebederoiot

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BebederoAdapter(
    private var bebederos: List<Bebedero>
) : RecyclerView.Adapter<BebederoAdapter.BebederoViewHolder>() {

    // Guarda las vistas de UNA tarjeta, para no buscarlas cada vez
    class BebederoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreBebedero)
        val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoConexion)
        val tvNivel: TextView = itemView.findViewById(R.id.tvNivelAgua)
        val llPill: View = itemView.findViewById(R.id.llEstadoPill)
        val puntoEstado: View = itemView.findViewById(R.id.vPuntoEstado)
    }

    // Crea una tarjeta nueva usando el molde item_bebedero.xml
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BebederoViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bebedero, parent, false)
        return BebederoViewHolder(vista)
    }

    // Llena una tarjeta con los datos del bebedero que le toca
    override fun onBindViewHolder(holder: BebederoViewHolder, position: Int) {
        val bebedero = bebederos[position]

        holder.tvNombre.text = bebedero.nombre
        holder.tvNivel.text = "${bebedero.nivel}%"

        if (bebedero.estaOnline(System.currentTimeMillis())) {
            holder.tvEstado.text = "Online"
            holder.tvEstado.setTextColor(Color.parseColor("#4CAF50"))
            holder.llPill.setBackgroundResource(R.drawable.bg_pill_online)
            holder.puntoEstado.setBackgroundResource(R.drawable.dot_online)
        } else {
            holder.tvEstado.text = "Offline"
            holder.tvEstado.setTextColor(Color.parseColor("#F44336"))
            holder.llPill.setBackgroundResource(R.drawable.bg_pill_offline)
            holder.puntoEstado.setBackgroundResource(R.drawable.dot_offline)
        }
    }

    // Cuántas tarjetas hay en total
    override fun getItemCount(): Int = bebederos.size

    // Para cambiar la lista más adelante (cuando llegue Firebase)
    fun actualizarLista(nuevaLista: List<Bebedero>) {
        bebederos = nuevaLista
        notifyDataSetChanged()
    }
}