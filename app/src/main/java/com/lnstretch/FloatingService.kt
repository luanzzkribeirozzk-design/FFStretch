package com.lnstretch

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var root: View
    private lateinit var params: WindowManager.LayoutParams

    private var expanded = false
    private var isStretched = false
    private var modoLateral = true
    private var selectedPct = 15
    private var selectedDpi = 480

    // DPI range
    private val DPI_MIN = 370
    private val DPI_MAX = 1440
    private val DPI_RANGE = DPI_MAX - DPI_MIN  // 1070 steps

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            toast("Shizuku autorizado!")
            updateShizukuUI()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Shizuku.addRequestPermissionResultListener(permListener)
        startForegroundNotif()
        setupFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
        try { wm.removeView(root) } catch (_: Exception) {}
    }

    private fun startForegroundNotif() {
        val ch = "lnstretch_float"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(ch, "LN Stretch", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        startForeground(2, NotificationCompat.Builder(this, ch)
            .setContentTitle("LN Stretch")
            .setContentText("Painel flutuante ativo")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build())
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupFloat() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LayoutInflater.from(this).inflate(R.layout.layout_floating, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0; y = 0
        }

        wm.addView(root, params)

        val fab        = root.findViewById<FrameLayout>(R.id.fabMain)
        val panel      = root.findViewById<View>(R.id.floatPanel)
        val btnClose   = root.findViewById<View>(R.id.fabClose)
        val btnMin     = root.findViewById<View>(R.id.fabMinimize)
        val btnStr     = root.findViewById<Button>(R.id.fabStretch)
        val btnRst     = root.findViewById<Button>(R.id.fabRestore)
        val btnOpt     = root.findViewById<Button>(R.id.fabOptimize)
        val btnShi     = root.findViewById<Button>(R.id.fabShizuku)
        val btnLat     = root.findViewById<Button>(R.id.btnModoLateral)
        val btnVert    = root.findViewById<Button>(R.id.btnModoVertical)
        val btnDpi     = root.findViewById<Button>(R.id.fabApplyDpi)
        val btnRstDpi  = root.findViewById<Button>(R.id.fabResetDpi)
        val tvPct      = root.findViewById<TextView>(R.id.tvFloatPct)
        val tvDpi      = root.findViewById<TextView>(R.id.tvFloatDpi)
        val tvStatus   = root.findViewById<TextView>(R.id.tvFloatStatus)
        val sbStr      = root.findViewById<SeekBar>(R.id.seekbarFloat)
        val sbDpi      = root.findViewById<SeekBar>(R.id.seekbarDpi)

        // Init valores
        selectedPct = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).getInt("float_pct", 15)
        val m = getMetrics()
        selectedDpi = m.densityDpi.coerceIn(DPI_MIN, DPI_MAX)

        sbStr.max = 39  // 1..40
        sbStr.progress = (selectedPct - 1).coerceIn(0, 39)
        tvPct.text = "+${selectedPct}%"

        sbDpi.max = DPI_RANGE
        sbDpi.progress = (selectedDpi - DPI_MIN).coerceIn(0, DPI_RANGE)
        tvDpi.text = "$selectedDpi"

        updateStatusUI(tvStatus)
        updateShizukuUI()

        // Seekbar stretch
        sbStr.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedPct = p + 1
                tvPct.text = "+${selectedPct}%"
                getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("float_pct", selectedPct).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Seekbar DPI
        sbDpi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedDpi = DPI_MIN + p
                tvDpi.text = "$selectedDpi"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Minimizar (fecha painel, mostra só bolinha)
        btnMin.setOnClickListener {
            expanded = false
            panel.visibility = View.GONE
            setFocusable(false)
        }

        // Fechar completamente
        btnClose.setOnClickListener {
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            runCmd("wm density reset")
            stopSelf()
        }

        // Modo lateral/vertical
        btnLat.setOnClickListener {
            modoLateral = true
            btnLat.backgroundTintList = tint(0xFFFF4500.toInt())
            btnLat.setTextColor(0xFFFFFFFF.toInt())
            btnVert.backgroundTintList = tint(0xFF1A1A2E.toInt())
            btnVert.setTextColor(0xFFAAAACC.toInt())
        }
        btnVert.setOnClickListener {
            modoLateral = false
            btnVert.backgroundTintList = tint(0xFFFF4500.toInt())
            btnVert.setTextColor(0xFFFFFFFF.toInt())
            btnLat.backgroundTintList = tint(0xFF1A1A2E.toInt())
            btnLat.setTextColor(0xFFAAAACC.toInt())
        }

        // Esticar
        btnStr.setOnClickListener {
            if (!isGranted()) { toast("Autorize o Shizuku!"); return@setOnClickListener }
            val mx = getMetrics()
            val w = mx.widthPixels; val h = mx.heightPixels
            runCmd("wm size reset"); runCmd("wm overscan reset")
            val ok = if (modoLateral) {
                val sw = (w / (1.0 + selectedPct / 100.0)).toInt()
                runCmd("wm size ${sw}x${h}")
            } else {
                val sh = (h / (1.0 + selectedPct / 100.0)).toInt()
                runCmd("wm size ${w}x${sh}")
            }
            if (ok) {
                isStretched = true
                updateStatusUI(tvStatus)
                val modo = if (modoLateral) "Laterais" else "Cima/Baixo"
                toast("Esticado +${selectedPct}% $modo!")
            } else toast("Erro! Verifique Shizuku.")
        }

        // Restaurar stretch
        btnRst.setOnClickListener {
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            isStretched = false
            updateStatusUI(tvStatus)
            toast("Tela restaurada!")
        }

        // Aplicar DPI
        btnDpi.setOnClickListener {
            if (!isGranted()) { toast("Autorize o Shizuku!"); return@setOnClickListener }
            if (runCmd("wm density $selectedDpi")) {
                updateStatusUI(tvStatus)
                toast("DPI $selectedDpi aplicado!")
            } else toast("Erro ao aplicar DPI.")
        }

        // Restaurar DPI
        btnRstDpi.setOnClickListener {
            runCmd("wm density reset")
            val mx = getMetrics()
            selectedDpi = mx.densityDpi.coerceIn(DPI_MIN, DPI_MAX)
            tvDpi.text = "$selectedDpi"
            sbDpi.progress = (selectedDpi - DPI_MIN).coerceIn(0, DPI_RANGE)
            updateStatusUI(tvStatus)
            toast("DPI restaurado!")
        }

        // Otimizar
        btnOpt.setOnClickListener {
            toast("Otimizando...")
            Thread {
                if (isGranted()) runCmd("am kill-all")
                System.gc()
                val free = Runtime.getRuntime().freeMemory() / 1024 / 1024
                Handler(Looper.getMainLooper()).post { toast("Otimizado! RAM: ${free}MB livre") }
            }.start()
        }

        // Shizuku
        btnShi.setOnClickListener {
            try {
                if (!Shizuku.pingBinder()) { toast("Abra o Shizuku!"); return@setOnClickListener }
                if (isGranted()) { toast("Ja autorizado!"); return@setOnClickListener }
                Shizuku.requestPermission(1001)
            } catch (e: Exception) { toast("Erro: ${e.message}") }
        }

        // Drag liso com velocidade
        var startRawX = 0f; var startRawY = 0f
        var startPX = 0; var startPY = 0
        var lastX = 0f; var lastY = 0f
        var velX = 0f; var velY = 0f
        val handler = Handler(Looper.getMainLooper())
        var dragging = false

        val flingRunnable = object : Runnable {
            override fun run() {
                velX *= 0.88f; velY *= 0.88f
                if (kotlin.math.abs(velX) < 0.5f && kotlin.math.abs(velY) < 0.5f) return
                params.x -= velX.toInt()
                params.y += velY.toInt()
                try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                handler.postDelayed(this, 16)
            }
        }

        fab.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(flingRunnable)
                    startRawX = e.rawX; startRawY = e.rawY
                    startPX = params.x; startPY = params.y
                    lastX = e.rawX; lastY = e.rawY
                    velX = 0f; velY = 0f; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startRawX
                    val dy = e.rawY - startRawY
                    if (!dragging && (kotlin.math.abs(dx) > 6f || kotlin.math.abs(dy) > 6f)) dragging = true
                    if (dragging) {
                        velX = e.rawX - lastX
                        velY = e.rawY - lastY
                        lastX = e.rawX; lastY = e.rawY
                        params.x = startPX - dx.toInt()
                        params.y = startPY + dy.toInt()
                        try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        expanded = !expanded
                        panel.visibility = if (expanded) View.VISIBLE else View.GONE
                        setFocusable(expanded)
                        if (expanded) { updateStatusUI(tvStatus); updateShizukuUI() }
                    } else {
                        handler.postDelayed(flingRunnable, 16)
                    }
                }
            }
            true
        }
    }

    private fun setFocusable(focusable: Boolean) {
        params.flags = if (focusable)
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        else
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
    }

    private fun isGranted() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun runCmd(cmd: String): Boolean {
        return try {
            val p: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.waitFor(); true
        } catch (_: Exception) { false }
    }

    private fun getMetrics(): DisplayMetrics {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        return m
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatusUI(tv: TextView) {
        val m = getMetrics()
        val estado = if (isStretched) "ESTICADO" else "Normal"
        tv.text = "${m.widthPixels}x${m.heightPixels} | DPI: ${m.densityDpi} | $estado"
    }

    @SuppressLint("SetTextI18n")
    private fun updateShizukuUI() {
        val tv = root.findViewById<TextView>(R.id.tvFloatShizuku)
        val btn = root.findViewById<Button>(R.id.fabShizuku)
        try {
            val running = Shizuku.pingBinder()
            val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            tv.text = if (granted) "Shizuku: Autorizado" else if (running) "Shizuku: Clique Autorizar" else "Shizuku: Nao encontrado"
            btn.isEnabled = running && !granted
        } catch (_: Exception) { tv.text = "Shizuku: Nao instalado" }
    }

    private fun tint(color: Int) = android.content.res.ColorStateList.valueOf(color)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
