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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private var stretchTabActive = true

    private val DPI_MIN = 370
    private val DPI_MAX = 1440

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
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 0; y = 0 }

        wm.addView(root, params)

        val fab            = root.findViewById<FrameLayout>(R.id.fabMain)
        val panel          = root.findViewById<View>(R.id.floatPanel)
        val btnClose       = root.findViewById<TextView>(R.id.fabClose)
        val btnMin         = root.findViewById<TextView>(R.id.fabMinimize)
        val btnStr         = root.findViewById<Button>(R.id.fabStretch)
        val btnRst         = root.findViewById<Button>(R.id.fabRestore)
        val btnOpt         = root.findViewById<Button>(R.id.fabOptimize)
        val btnShi         = root.findViewById<Button>(R.id.fabShizuku)
        val btnLat         = root.findViewById<Button>(R.id.btnModoLateral)
        val btnVert        = root.findViewById<Button>(R.id.btnModoVertical)
        val btnDpi         = root.findViewById<Button>(R.id.fabApplyDpi)
        val btnRstDpi      = root.findViewById<Button>(R.id.fabResetDpi)
        val tabStretch     = root.findViewById<TextView>(R.id.tabStretch)
        val tabDpi         = root.findViewById<TextView>(R.id.tabDpi)
        val contentStretch = root.findViewById<LinearLayout>(R.id.tabContentStretch)
        val contentDpi     = root.findViewById<LinearLayout>(R.id.tabContentDpi)
        val tvPct          = root.findViewById<TextView>(R.id.tvFloatPct)
        val tvDpi          = root.findViewById<TextView>(R.id.tvFloatDpi)
        val tvStatus       = root.findViewById<TextView>(R.id.tvFloatStatus)
        val sbStr          = root.findViewById<SeekBar>(R.id.seekbarFloat)
        val sbDpi          = root.findViewById<SeekBar>(R.id.seekbarDpi)

        // Init
        selectedPct = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).getInt("float_pct", 15)
        val m = getMetrics()
        selectedDpi = m.densityDpi.coerceIn(DPI_MIN, DPI_MAX)
        sbStr.max = 39; sbStr.progress = (selectedPct - 1).coerceIn(0, 39)
        tvPct.text = "+${selectedPct}%"
        sbDpi.max = DPI_MAX - DPI_MIN
        sbDpi.progress = (selectedDpi - DPI_MIN).coerceIn(0, DPI_MAX - DPI_MIN)
        tvDpi.text = "$selectedDpi"
        updateStatusUI(tvStatus)
        updateShizukuUI()

        // Abas
        tabStretch.setOnClickListener {
            stretchTabActive = true
            tabStretch.setBackgroundColor(0xFFFF4500.toInt()); tabStretch.setTextColor(0xFFFFFFFF.toInt())
            tabDpi.setBackgroundColor(0xFF0D0D1E.toInt()); tabDpi.setTextColor(0xFF888899.toInt())
            contentStretch.visibility = View.VISIBLE; contentDpi.visibility = View.GONE
        }
        tabDpi.setOnClickListener {
            stretchTabActive = false
            tabDpi.setBackgroundColor(0xFF7C00FF.toInt()); tabDpi.setTextColor(0xFFFFFFFF.toInt())
            tabStretch.setBackgroundColor(0xFF0D0D1E.toInt()); tabStretch.setTextColor(0xFF888899.toInt())
            contentDpi.visibility = View.VISIBLE; contentStretch.visibility = View.GONE
        }

        // Sliders
        sbStr.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedPct = p + 1; tvPct.text = "+${selectedPct}%"
                getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit().putInt("float_pct", selectedPct).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        sbDpi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { selectedDpi = DPI_MIN + p; tvDpi.text = "$selectedDpi" }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Minimizar
        btnMin.setOnClickListener { expanded = false; panel.visibility = View.GONE; setFocusable(false) }

        // Fechar completamente
        btnClose.setOnClickListener {
            runCmd("wm size reset"); runCmd("wm overscan reset"); runCmd("wm density reset")
            stopSelf()
        }

        // Modo
        btnLat.setOnClickListener {
            modoLateral = true
            btnLat.backgroundTintList = tint(0xFFFF4500.toInt()); btnLat.setTextColor(0xFFFFFFFF.toInt())
            btnVert.backgroundTintList = tint(0xFF1A1A2E.toInt()); btnVert.setTextColor(0xFFAAAACC.toInt())
        }
        btnVert.setOnClickListener {
            modoLateral = false
            btnVert.backgroundTintList = tint(0xFFFF4500.toInt()); btnVert.setTextColor(0xFFFFFFFF.toInt())
            btnLat.backgroundTintList = tint(0xFF1A1A2E.toInt()); btnLat.setTextColor(0xFFAAAACC.toInt())
        }

        // Esticar
        btnStr.setOnClickListener {
            if (!isGranted()) { toast("Autorize o Shizuku!"); return@setOnClickListener }
            val mx = getMetrics(); val w = mx.widthPixels; val h = mx.heightPixels
            runCmd("wm size reset"); runCmd("wm overscan reset")
            val ok = if (modoLateral) runCmd("wm size ${(w/(1.0+selectedPct/100.0)).toInt()}x${h}")
                     else runCmd("wm size ${w}x${(h/(1.0+selectedPct/100.0)).toInt()}")
            if (ok) { isStretched = true; updateStatusUI(tvStatus); toast("Esticado +${selectedPct}%!") }
            else toast("Erro! Verifique Shizuku.")
        }

        // Restaurar
        btnRst.setOnClickListener {
            runCmd("wm size reset"); runCmd("wm overscan reset")
            isStretched = false; updateStatusUI(tvStatus); toast("Tela restaurada!")
        }

        // Otimizar
        btnOpt.setOnClickListener {
            toast("Otimizando...")
            Thread {
                if (isGranted()) runCmd("am kill-all"); System.gc()
                val free = Runtime.getRuntime().freeMemory() / 1024 / 1024
                Handler(Looper.getMainLooper()).post { toast("Otimizado! RAM: ${free}MB livre") }
            }.start()
        }

        // DPI
        btnDpi.setOnClickListener {
            if (!isGranted()) { toast("Autorize o Shizuku!"); return@setOnClickListener }
            if (runCmd("wm density $selectedDpi")) { updateStatusUI(tvStatus); toast("DPI $selectedDpi!") }
            else toast("Erro ao aplicar DPI.")
        }
        btnRstDpi.setOnClickListener {
            runCmd("wm density reset")
            val mx = getMetrics(); selectedDpi = mx.densityDpi.coerceIn(DPI_MIN, DPI_MAX)
            tvDpi.text = "$selectedDpi"; sbDpi.progress = (selectedDpi - DPI_MIN).coerceIn(0, DPI_MAX - DPI_MIN)
            updateStatusUI(tvStatus); toast("DPI restaurado!")
        }

        // Shizuku
        btnShi.setOnClickListener {
            try {
                if (!Shizuku.pingBinder()) { toast("Abra o Shizuku!"); return@setOnClickListener }
                if (isGranted()) { toast("Ja autorizado!"); return@setOnClickListener }
                Shizuku.requestPermission(1001)
            } catch (e: Exception) { toast("Erro: ${e.message}") }
        }

        // Drag liso com inercial
        var sRawX = 0f; var sRawY = 0f; var sPX = 0; var sPY = 0
        var lastX = 0f; var lastY = 0f; var velX = 0f; var velY = 0f
        var dragging = false
        val handler = Handler(Looper.getMainLooper())

        val fling = object : Runnable {
            override fun run() {
                velX *= 0.85f; velY *= 0.85f
                if (kotlin.math.abs(velX) < 0.5f && kotlin.math.abs(velY) < 0.5f) return
                params.x -= velX.toInt(); params.y += velY.toInt()
                try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                handler.postDelayed(this, 16)
            }
        }

        fab.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(fling)
                    sRawX = e.rawX; sRawY = e.rawY; sPX = params.x; sPY = params.y
                    lastX = e.rawX; lastY = e.rawY; velX = 0f; velY = 0f; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sRawX; val dy = e.rawY - sRawY
                    if (!dragging && (kotlin.math.abs(dx) > 8f || kotlin.math.abs(dy) > 8f)) dragging = true
                    if (dragging) {
                        velX = e.rawX - lastX; velY = e.rawY - lastY
                        lastX = e.rawX; lastY = e.rawY
                        params.x = sPX - dx.toInt(); params.y = sPY + dy.toInt()
                        try { wm.updateViewLayout(root, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        expanded = !expanded
                        panel.visibility = if (expanded) View.VISIBLE else View.GONE
                        setFocusable(expanded)
                        if (expanded) { updateStatusUI(tvStatus); updateShizukuUI() }
                    } else handler.postDelayed(fling, 16)
                }
            }
            true
        }
    }

    private fun setFocusable(f: Boolean) {
        params.flags = if (f) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                       else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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
        tv.text = "${m.widthPixels}x${m.heightPixels}  DPI:${m.densityDpi}  ${if (isStretched) "ESTICADO" else "Normal"}"
    }

    @SuppressLint("SetTextI18n")
    private fun updateShizukuUI() {
        val tv  = root.findViewById<TextView>(R.id.tvFloatShizuku)
        val btn = root.findViewById<Button>(R.id.fabShizuku)
        try {
            val r = Shizuku.pingBinder()
            val g = r && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            tv.text = if (g) "Shizuku: Autorizado" else if (r) "Clique Autorizar" else "Shizuku nao encontrado"
            btn.isEnabled = r && !g
        } catch (_: Exception) { tv.text = "Shizuku: Nao instalado" }
    }

    private fun tint(c: Int) = android.content.res.ColorStateList.valueOf(c)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
