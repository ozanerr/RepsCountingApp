package com.example.repscountingapp.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.example.repscountingapp.R
import com.example.repscountingapp.database.LatihanHistory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryListAdapter(
    context: Context,
    private val data: List<LatihanHistory>,
    private val onDeleteClick: (LatihanHistory) -> Unit
) : ArrayAdapter<LatihanHistory>(context, 0, data) {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val dateFormatter = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault())

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.list_item_history, parent, false)

        // ambil datanya
        val item = data[position]

        // cari komponen ui-nya
        val title = view.findViewById<TextView>(R.id.history_title)
        val reps = view.findViewById<TextView>(R.id.history_reps)
        val date = view.findViewById<TextView>(R.id.history_date)
        val image = view.findViewById<ImageView>(R.id.history_image)
        val deleteButton = view.findViewById<ImageButton>(R.id.history_delete_button)

        // isi datanya ke ui
        title.text = item.namaLatihan
        reps.text = "${item.jumlahRepetisi} Repetisi"
        date.text = dateFormatter.format(Date(item.tanggal))

        // coba muat gambarnya dari file
        val imgFile = File(item.fotoPath)
        if (imgFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
            image.setImageBitmap(bitmap)
        } else {
            // kalau nggak ada, pakai placeholder
            image.setImageResource(R.drawable.ic_launcher_background)
        }

        deleteButton.setOnClickListener {
            onDeleteClick(item)
        }

        return view
    }
}