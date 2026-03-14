package com.lnstretch

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import android.widget.SeekBar
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
    private var modoLateral = true   // true = laterais, false = cima/baixo
    private var expanded = false

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            toast("Shizuku autorizado!")
            updateShizukuStatus()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Shizuku.addRequestPermissionResultListener(permListener)
        startForegroundNotif()
        setupFloating()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
        if (::floatView.isInitialized) windowManager.removeView(floatView)
    }

    private fun startForegroundNotif() {
        val ch = "lnstretch_float"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ch, "LN Stretch", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(this, ch)
            .setContentTitle("LN Stretch")
            .setContentText("Painel flutuante ativo")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .build()
        startForeground(2, notif)
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupFloating() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.layout_floating, null)

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

        // Views
        val fabMain      = floatView.findViewById<View>(R.id.fabMain)
        val floatPanel   = floatView.findViewById<View>(R.id.floatPanel)
        val fabClose     = floatView.findViewById<View>(R.id.fabClose)
        val fabStretch   = floatView.findViewById<Button>(R.id.fabStretch)
        val fabRestore   = floatView.findViewById<Button>(R.id.fabRestore)
        val fabOptimize  = floatView.findViewById<Button>(R.id.fabOptimize)
        val fabShizuku   = floatView.findViewById<Button>(R.id.fabShizuku)
        val btnLateral   = floatView.findViewById<Button>(R.id.btnModoLateral)
        val btnVertical  = floatView.findViewById<Button>(R.id.btnModoVertical)
        val tvPct        = floatView.findViewById<TextView>(R.id.tvFloatPct)
        val tvStatus     = floatView.findViewById<TextView>(R.id.tvFloatStatus)
        val seekbar      = floatView.findViewById<SeekBar>(R.id.seekbarFloat)

        // Pega pct salvo
        selectedPercent = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
            .getInt("float_pct", 15)
        seekbar.max = 30
        seekbar.progress = selectedPercent - 10
        tvPct.text = "+${selectedPercent}%"
        updateStatusText(tvStatus)
        updateShizukuStatus()

        // Seekbar
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedPercent = 10 + p
                tvPct.text = "+${selectedPercent}%"
                getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("float_pct", selectedPercent).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Toggle painel
        fabMain.setOnClickListener {
            expanded = !expanded
            floatPanel.visibility = if (expanded) View.VISIBLE else View.GONE
            // Quando expande, permite foco para interagir com seekbar
            params.flags = if (expanded)
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            else
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(floatView, params)
            updateStatusText(tvStatus)
            updateShizukuStatus()
        }

        // Fechar
        fabClose.setOnClickListener {
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            runCmd("wm density reset")
            stopSelf()
        }

        // Modo lateral
        btnLateral.setOnClickListener {
            modoLateral = true
            btnLateral.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4500.toInt())
            btnLateral.setTextColor(0xFFFFFFFF.toInt())
            btnVertical.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
            btnVertical.setTextColor(0xFFAAAACC.toInt())
        }

        // Modo cima/baixo
        btnVertical.setOnClickListener {
            modoLateral = false
            btnVertical.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4500.toInt())
            btnVertical.setTextColor(0xFFFFFFFF.toInt())
            btnLateral.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
            btnLateral.setTextColor(0xFFAAAACC.toInt())
        }

        // Esticar
        fabStretch.setOnClickListener {
            if (!isGranted()) { toast("Autorize o Shizuku!"); return@setOnClickListener }
            val m = getMetrics()
            val w = m.widthPixels
            val h = m.heightPixels
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            val ok = if (modoLateral) {
                // Laterais: reduz largura logica -> Android escala -> imagem mais larga
                val sw = (w / (1.0 + selectedPercent / 100.0)).toInt()
                runCmd("wm size ${sw}x${h}")
            } else {
                // Cima/baixo: reduz altura logica -> Android escala -> imagem mais alta
                val sh = (h / (1.0 + selectedPercent / 100.0)).toInt()
                runCmd("wm size ${w}x${sh}")
            }
            if (ok) {
                isStretched = true
                val modo = if (modoLateral) "Laterais" else "Cima/Baixo"
                toast("Esticado +${selectedPercent}% ($modo)!")
                updateStatusText(tvStatus)
            } else toast("Erro! Verifique Shizuku.")
        }

        // Restaurar
        fabRestore.setOnClickListener {
            runCmd("wm size reset")
            runCmd("wm overscan reset")
            runCmd("wm density reset")
            isStretched = false
            toast("Tela restaurada!")
            updateStatusText(tvStatus)
        }

        // Otimizar
        fabOptimize.setOnClickListener {
            toast("Otimizando...")
            Thread {
                if (isGranted()) runCmd("am kill-all")
                System.gc()
                val free = Runtime.getRuntime().freeMemory() / 1024 / 1024
                android.os.Handler(mainLooper).post { toast("Otimizado! RAM: ${free}MB livre") }
            }.start()
        }

        // Shizuku
        fabShizuku.setOnClickListener {
            try {
                if (!Shizuku.pingBinder()) { toast("Abra o Shizuku!"); return@setOnClickListener }
                if (isGranted()) { toast("Ja autorizado!"); return@setOnClickListener }
                Shizuku.requestPermission(1001)
            } catch (e: Exception) { toast("Erro: ${e.message}") }
        }

        // Drag pelo header
        var ix = 0f; var iy = 0f; var lx = 0; var ly = 0
        var dragging = false
        fabMain.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = e.rawX; iy = e.rawY
                    lx = params.x; ly = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - ix
                    val dy = e.rawY - iy
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragging = true
                    if (dragging) {
                        params.x = lx - dx.toInt()
                        params.y = ly + dy.toInt()
                        windowManager.updateViewLayout(floatView, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        expanded = !expanded
                        floatPanel.visibility = if (expanded) View.VISIBLE else View.GONE
                        params.flags = if (expanded)
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        else
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        windowManager.updateViewLayout(floatView, params)
                        if (expanded) { updateStatusText(tvStatus); updateShizukuStatus() }
                    }
                }
            }
            true
        }
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
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatusText(tv: TextView) {
        val m = getMetrics()
        val estado = if (isStretched) "ESTICADO" else "Normal"
        tv.text = "${m.widthPixels}x${m.heightPixels} | $estado"
    }

    @SuppressLint("SetTextI18n")
    private fun updateShizukuStatus() {
        val tv = floatView.findViewById<TextView>(R.id.tvFloatShizuku)
        val btn = floatView.findViewById<Button>(R.id.fabShizuku)
        try {
            val running = Shizuku.pingBinder()
            val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            tv.text = when {
                granted -> "Shizuku: Autorizado"
                running -> "Shizuku: Clique Autorizar"
                else    -> "Shizuku: Nao encontrado"
            }
            btn.isEnabled = running && !granted
        } catch (_: Exception) {
            tv.text = "Shizuku: Nao instalado"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
