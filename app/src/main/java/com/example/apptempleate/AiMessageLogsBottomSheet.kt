package com.example.apptempleate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AiMessageLogsBottomSheet(
    private val logText: String
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
        return inflater.inflate(R.layout.dialog_ai_message_logs, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = ColorDrawable(Color.TRANSPARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val btnCloseLogs: ImageButton = view.findViewById(R.id.btnCloseLogs)
        val tvLogContent: TextView = view.findViewById(R.id.tvLogContent)
        val btnCopyLog: TextView = view.findViewById(R.id.btnCopyLog)

        tvLogContent.text = logText

        btnCloseLogs.setOnClickListener { dismiss() }

        btnCopyLog.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Memossist LLM & DAG Logs", logText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied LLM & DAG diagnostic logs to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}
