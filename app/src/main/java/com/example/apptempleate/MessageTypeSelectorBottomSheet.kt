package com.example.apptempleate

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MessageTypeSelectorBottomSheet(
    private val currentlySelected: MessageType? = null,
    private val onTypeSelected: (MessageType) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentBottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_message_type_selector, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background =
            ColorDrawable(Color.TRANSPARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnClose: ImageButton = view.findViewById(R.id.btnCloseTypeSelector)
        btnClose.setOnClickListener { dismiss() }

        val llContainer: LinearLayout = view.findViewById(R.id.llTypesListContainer)
        llContainer.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        for (type in MessageType.values()) {
            val itemView = inflater.inflate(R.layout.item_message_type_choice, llContainer, false)

            val tvEmoji: TextView = itemView.findViewById(R.id.tvTypeEmoji)
            val tvName: TextView = itemView.findViewById(R.id.tvTypeName)
            val tvDesc: TextView = itemView.findViewById(R.id.tvTypeDescription)
            val ivCheck: ImageView = itemView.findViewById(R.id.ivTypeSelectedCheck)

            tvEmoji.text = type.iconEmoji
            tvName.text = type.displayName
            tvDesc.text = type.description

            if (currentlySelected == type) {
                ivCheck.visibility = View.VISIBLE
            } else {
                ivCheck.visibility = View.GONE
            }

            itemView.setOnClickListener {
                dismiss()
                onTypeSelected(type)
            }

            llContainer.addView(itemView)
        }
    }
}
