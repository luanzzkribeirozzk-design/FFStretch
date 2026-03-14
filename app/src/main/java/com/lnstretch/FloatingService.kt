package com.lnstretch

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatView: View
    private var isStretched = false
    private var selectedPercent = 15
    private var expanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground()
        setupFloating()
    }

    private fun startForeground() {
        val channelId = "lnstretch_float"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "LN Stretch Flutuante", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LN Stretch ativo")
            .setContentText("Botao flutuante ativo")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        startForeground(2, notif)
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupFloating() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatView = inflater.inflate(R.layout.layout_floating, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0; y = 0
        }

        windowManager.addView(floatView, params)

        val btnMain    = floatView.findViewById<View>(R.id.fabMain)
        val btnStretch = floatView.findViewById<View>(R.id.fabStretch)
        val btnRestore = floatView.findViewById<View>(R.id.fabRestore)
        val btnClose   = floatView.findViewById<View>(R.id.fabClose)
        val tvPct      = floatView.findViewById<TextView>(R.id.tvFloatPct)
        val panel      = floatView.findViewById<View>(R.id.floatPanel)

        btnMain.setOnClickListener {
            expanded = !expanded
            panel.visibility = if (expanded) View.VISIBLE else View.GONE
        }

        btnStretch.setOnClickListener {
            if (isStretched) { toast("Ja esticado!"); return@setOnClickListener }
            val m = getMetrics()
            val origW = m.widthPixels
            val origH = m.heightPixels
            val stretchedW = (origW / (1.0 + selectedPercent / 100.0)).toInt()
            runCmd("wm overscan reset")
            if (runCmd("wm size ${stretchedW}x${origH}")) {
                isStretched = true
                tvPct.text = "+${selectedPercent}%"
                toast("Esticado +${selectedPercent}%!")
            } else toast("Erro! Verifique Shizuku.")
        }

        btnRestore.setOnClickListener {
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            runCmd("wm density reset")
            isStretched = false
            tvPct.text = ""
            toast("Tela restaurada!")
        }

        btnClose.setOnClickListener {
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            stopSelf()
        }

        // Drag
        var ix = 0f; var iy = 0f; var lx = 0; var ly = 0
        btnMain.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = e.rawX; iy = e.rawY; lx = params.x; ly = params.y }
                MotionEvent.ACTION_MOVE -> {
                    params.x = lx - (e.rawX - ix).toInt()
                    params.y = ly + (e.rawY - iy).toInt()
                    windowManager.updateViewLayout(floatView, params)
                }
            }
            false
        }

        // Recebe percentual
        selectedPercent = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
            .getInt("float_pct", 15)
    }

    private fun runCmd(cmd: String): Boolean {
        return try {
            val p: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.waitFor(); true
        } catch (_: Exception) { false }
    }

    private fun getMetrics(): DisplayMetrics {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        if (::floatView.isInitialized) windowManager.removeView(floatView)
    }
}
