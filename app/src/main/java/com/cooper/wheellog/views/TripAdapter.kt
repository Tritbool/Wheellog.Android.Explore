package com.cooper.wheellog.views

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.cooper.wheellog.R
import com.cooper.wheellog.data.TripItemState
import com.cooper.wheellog.databinding.ListTripItemBinding
import com.cooper.wheellog.utils.ThemeIconEnum
import com.cooper.wheellog.utils.ThemeManager

class TripAdapter(private val delete: (TripItemState) -> Unit) : RecyclerView.Adapter<TripAdapter.ViewHolder>() {
    private var items: List<TripItemState> = emptyList()
    private var theme = R.style.OriginalTheme

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun updateTrips(items: List<TripItemState>, theme: Int) {
        this.items = items
        this.theme = theme
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ListTripItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trip = items[position]
        holder.binding.apply {
            val font = ResourcesCompat.getFont(root.context, if (theme == R.style.AJDMTheme) R.font.ajdm else R.font.prime)
            name.text = trip.title
            description.text = trip.description
            description2.text = trip.description2
            name.typeface = font
            description.typeface = font
            description2.typeface = font
            popupButton.setImageResource(ThemeManager.getId(ThemeIconEnum.TripsPopupButton, theme))
            popupButton.setOnClickListener { TripActions.menu(popupButton, trip, delete, theme) }
            root.setOnLongClickListener { TripActions.menu(popupButton, trip, delete, theme); true }
        }
    }

    class ViewHolder(val binding: ListTripItemBinding) : RecyclerView.ViewHolder(binding.root)
}
