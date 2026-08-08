package com.example.apptempleate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WordsSynonymsAdapter(private val items: List<WordSynonymItem>) :
    RecyclerView.Adapter<WordsSynonymsAdapter.WordViewHolder>() {

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvItemWord: TextView = itemView.findViewById(R.id.tvItemWord)
        val tvItemSynonymCount: TextView = itemView.findViewById(R.id.tvItemSynonymCount)
        val tvItemSynonymsList: TextView = itemView.findViewById(R.id.tvItemSynonymsList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word_synonym, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val item = items[position]
        holder.tvItemWord.text = item.word

        if (item.synonyms.isEmpty()) {
            holder.tvItemSynonymCount.text = "• 0 synonyms"
            holder.tvItemSynonymsList.text = "No direct synonyms found"
        } else {
            val countText = if (item.synonyms.size == 1) "• 1 synonym" else "• ${item.synonyms.size} synonyms"
            holder.tvItemSynonymCount.text = countText
            holder.tvItemSynonymsList.text = item.synonyms.joinToString(separator = ", ")
        }
    }

    override fun getItemCount(): Int = items.size
}
