package com.lnstretch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class MainActivity : AppCompatActivity() {

    private var selectedPercent = 15
    private var modoLateral = true

    private lateinit var tvStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvSliderLabel: TextView
    private lateinit var seekbarStretch: SeekBar
    private lateinit var btnStretch: Button
    private lateinit var btnUnstretch: Button
    private lateinit var btnOptimize: Button
    private lateinit var btnOpenFF: Button
    private lateinit var btnShizuku: Button

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == PackageManager.PERMISSION_GRANTED) {
            showToast("Shizuku autorizado!")
            updateShizukuStatus()
        } else showToast("Permissao negada")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus        = findViewById(R.id.tvStatus)
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus)
        tvSliderLabel   = findViewById(R.id.tvSliderLabel)
        seekbarStretch  = findViewById(R.id.seekbarStretch)
        btnStretch      = findViewById(R.id.btnStretch)
        btnUnstretch    = findViewById(R.id.btnUnstretch)
        btnOptimize     = findViewById(R.id.btnOptimize)
        btnOpenFF       = findViewById(R.id.btnOpenFF)
        btnShizuku      = findViewById(R.id.btnShizuku)
        Shizuku.addRequestPermissionResultListener(permListener)
        setupSlider()
        val btnModoLateral  = findViewById<Button>(R.id.btnModoLateral)
        val btnModoVertical = findViewById<Button>(R.id.btnModoVertical)
        btnModoLateral.setOnClickListener {
            modoLateral = true
            btnModoLateral.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4500.toInt())
            btnModoLateral.setTextColor(0xFFFFFFFF.toInt())
            btnModoVertical.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
            btnModoVertical.setTextColor(0xFFAAAACC.toInt())
        }
        btnModoVertical.setOnClickListener {
            modoLateral = false
            btnModoVertical.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFFF4500.toInt())
            btnModoVertical.setTextColor(0xFFFFFFFF.toInt())
            btnModoLateral.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A1A2E.toInt())
            btnModoLateral.setTextColor(0xFFAAAACC.toInt())
        }
        btnStretch.setOnClickListener   { esticar() }
        btnUnstretch.setOnClickListener { restaurar() }
        btnOptimize.setOnClickListener  { otimizar() }
        btnOpenFF.setOnClickListener    { toggleFlutuante() }
        btnShizuku.setOnClickListener   { requestShizukuPerm() }
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    private fun setupSlider() {
        seekbarStretch.max = 30
        seekbarStretch.progress = 5
        updateSliderLabel(15)
        seekbarStretch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedPercent = 10 + p
                updateSliderLabel(selectedPercent)
                getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("float_pct", selectedPercent).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateSliderLabel(pct: Int) {
        val m = realMetrics()
        val stretchedW = (m.widthPixels / (1.0 + pct / 100.0)).toInt()
        tvSliderLabel.text = "+${pct}%"
    }

    @SuppressLint("SetTextI18n")
    private fun esticar() {
        if (!isGranted()) {
            tvStatus.text = "Autorize o Shizuku primeiro!"
            showToast("Autorize o Shizuku!")
            return
        }
        val m = realMetrics()
        val w = m.widthPixels
        val h = m.heightPixels
        runCmd("wm size reset")
        runCmd("wm overscan reset")
        val ok = if (modoLateral) {
            val sw = (w / (1.0 + selectedPercent / 100.0)).toInt()
            runCmd("wm size ${sw}x${h}")
        } else {
            val sh = (h / (1.0 + selectedPercent / 100.0)).toInt()
            runCmd("wm size ${w}x${sh}")
        }
        if (ok) {
            saveBackup(w, h, m.densityDpi)
            val modo = if (modoLateral) "Laterais" else "Cima/Baixo"
            tvStatus.text = "Esticado +${selectedPercent}% ($modo)!\nAbra o Free Fire agora."
            showToast("Esticado! Abra o FF agora.")
        } else {
            tvStatus.text = "Erro! Verifique o Shizuku."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun restaurar() {
        runCmd("wm size reset")
        runCmd("wm overscan reset")
        runCmd("wm density reset")
        clearBackup()
        tvStatus.text = "Tela restaurada ao normal!"
        showToast("Restaurado!")
    }

    @SuppressLint("SetTextI18n")
    private fun otimizar() {
        tvStatus.text = "Otimizando..."
        btnOptimize.isEnabled = false
        Thread {
            if (isGranted()) runCmd("am kill-all")
            System.gc()
            val free = Runtime.getRuntime().freeMemory() / 1024 / 1024
            runOnUiThread {
                btnOptimize.isEnabled = true
                tvStatus.text = "Otimizado! RAM livre: ${free}MB"
                showToast("Pronto!")
            }
        }.start()
    }

    private fun toggleFlutuante() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            showToast("Ative a permissao de sobreposicao!")
            return
        }
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        showToast("Flutuante ativado! Pode minimizar o app.")
    }

    private fun isGranted() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun requestShizukuPerm() {
        try {
            if (!Shizuku.pingBinder()) {
                showToast("Abra o app Shizuku primeiro!")
                return
            }
            if (isGranted()) { showToast("Shizuku ja autorizado!"); return }
            Shizuku.requestPermission(1001)
        } catch (e: Exception) { showToast("Erro: ${e.message}") }
    }

    private fun runCmd(cmd: String): Boolean {
        return try {
            val p: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.waitFor(); true
        } catch (_: Exception) { false }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatus() {
        val m = realMetrics()
        tvStatus.text = "Resolucao: ${m.widthPixels}x${m.heightPixels}  DPI: ${m.densityDpi}"
        updateShizukuStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun updateShizukuStatus() {
        try {
            val running = Shizuku.pingBinder()
            val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            tvShizukuStatus.text = when {
                granted -> "Shizuku: Autorizado"
                running -> "Shizuku: Ativo - clique Autorizar"
                else    -> "Shizuku: Nao encontrado"
            }
            btnShizuku.isEnabled = running && !granted
        } catch (_: Exception) {
            tvShizukuStatus.text = "Shizuku: Nao instalado"
        }
    }

    private fun realMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m  = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    private fun saveBackup(w: Int, h: Int, dpi: Int) {
        getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit()
            .putInt("orig_width", w).putInt("orig_height", h).putInt("orig_dpi", dpi).apply()
    }

    private fun clearBackup() {
        getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
