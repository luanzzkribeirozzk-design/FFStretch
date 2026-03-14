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

    private var selectedPct = 15
    private var selectedDpi = 480
    private var modoLateral = true
    private val DPI_MIN = 370
    private val DPI_MAX = 1440

    private lateinit var tvStatus: TextView
    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvSliderLabel: TextView
    private lateinit var tvDpiLabel: TextView
    private lateinit var seekbarStretch: SeekBar
    private lateinit var seekbarDpi: SeekBar
    private lateinit var btnStretch: Button
    private lateinit var btnUnstretch: Button
    private lateinit var btnOptimize: Button
    private lateinit var btnOpenFF: Button
    private lateinit var btnShizuku: Button
    private lateinit var btnApplyDpi: Button
    private lateinit var btnResetDpi: Button
    private lateinit var btnModoLateral: Button
    private lateinit var btnSensi: Button
    private lateinit var btnModoVertical: Button

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
        tvDpiLabel      = findViewById(R.id.tvDpiLabel)
        seekbarStretch  = findViewById(R.id.seekbarStretch)
        seekbarDpi      = findViewById(R.id.seekbarDpi)
        btnStretch      = findViewById(R.id.btnStretch)
        btnUnstretch    = findViewById(R.id.btnUnstretch)
        btnOptimize     = findViewById(R.id.btnOptimize)
        btnOpenFF       = findViewById(R.id.btnOpenFF)
        btnShizuku      = findViewById(R.id.btnShizuku)
        btnApplyDpi     = findViewById(R.id.btnApplyDpi)
        btnResetDpi     = findViewById(R.id.btnResetDpi)
        btnModoLateral  = findViewById(R.id.btnModoLateral)
        btnSensi        = findViewById(R.id.btnSensi)
        btnModoVertical = findViewById(R.id.btnModoVertical)

        Shizuku.addRequestPermissionResultListener(permListener)

        val m = realMetrics()
        selectedDpi = m.densityDpi.coerceIn(DPI_MIN, DPI_MAX)

        setupSliders()
        setupButtons()
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

    private fun setupSliders() {
        // Stretch 1-40
        seekbarStretch.max = 39
        seekbarStretch.progress = (selectedPct - 1).coerceIn(0, 39)
        tvSliderLabel.text = "+${selectedPct}%"
        seekbarStretch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedPct = p + 1
                tvSliderLabel.text = "+${selectedPct}%"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // DPI 370-1440
        seekbarDpi.max = DPI_MAX - DPI_MIN
        seekbarDpi.progress = (selectedDpi - DPI_MIN).coerceIn(0, DPI_MAX - DPI_MIN)
        tvDpiLabel.text = "$selectedDpi"
        seekbarDpi.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                selectedDpi = DPI_MIN + p
                tvDpiLabel.text = "$selectedDpi"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setupButtons() {
        btnModoLateral.setOnClickListener {
            modoLateral = true
            btnModoLateral.backgroundTintList = tint(0xFFFF4500.toInt())
            btnModoLateral.setTextColor(0xFFFFFFFF.toInt())
            btnModoVertical.backgroundTintList = tint(0xFF1A1A2E.toInt())
            btnModoVertical.setTextColor(0xFFAAAACC.toInt())
        }
        btnModoVertical.setOnClickListener {
            modoLateral = false
            btnModoVertical.backgroundTintList = tint(0xFFFF4500.toInt())
            btnModoVertical.setTextColor(0xFFFFFFFF.toInt())
            btnModoLateral.backgroundTintList = tint(0xFF1A1A2E.toInt())
            btnModoLateral.setTextColor(0xFFAAAACC.toInt())
        }
        btnStretch.setOnClickListener   { esticar() }
        btnUnstretch.setOnClickListener { restaurar() }
        btnOptimize.setOnClickListener  { otimizar() }
        btnOpenFF.setOnClickListener    { toggleFlutuante() }
        btnSensi.setOnClickListener     { startActivity(android.content.Intent(this, SensiActivity::class.java)) }
        btnShizuku.setOnClickListener   { requestShizukuPerm() }
        btnApplyDpi.setOnClickListener  { aplicarDpi() }
        btnResetDpi.setOnClickListener  { resetarDpi() }
    }

    @SuppressLint("SetTextI18n")
    private fun esticar() {
        if (!isGranted()) { tvStatus.text = "Autorize o Shizuku primeiro!"; return }
        val m = realMetrics()
        val w = m.widthPixels; val h = m.heightPixels
        runCmd("wm size reset"); runCmd("wm overscan reset")
        val ok = if (modoLateral) {
            val sw = (w / (1.0 + selectedPct / 100.0)).toInt()
            runCmd("wm size ${sw}x${h}")
        } else {
            val sh = (h / (1.0 + selectedPct / 100.0)).toInt()
            runCmd("wm size ${w}x${sh}")
        }
        if (ok) {
            val modo = if (modoLateral) "Laterais" else "Cima/Baixo"
            tvStatus.text = "Esticado +${selectedPct}% ($modo)!\nAbra o Free Fire agora."
            showToast("Esticado! Abra o FF.")
        } else tvStatus.text = "Erro! Verifique o Shizuku."
    }

    @SuppressLint("SetTextI18n")
    private fun restaurar() {
        runCmd("wm size reset"); runCmd("wm overscan reset"); runCmd("wm density reset")
        tvStatus.text = "Tela restaurada ao normal!"
        showToast("Restaurado!")
    }

    @SuppressLint("SetTextI18n")
    private fun aplicarDpi() {
        if (!isGranted()) { showToast("Autorize o Shizuku!"); return }
        if (runCmd("wm density $selectedDpi")) {
            tvStatus.text = "DPI $selectedDpi aplicado!"
            showToast("DPI $selectedDpi!")
        } else tvStatus.text = "Erro ao aplicar DPI."
    }

    @SuppressLint("SetTextI18n")
    private fun resetarDpi() {
        runCmd("wm density reset")
        val m = realMetrics()
        selectedDpi = m.densityDpi.coerceIn(DPI_MIN, DPI_MAX)
        tvDpiLabel.text = "$selectedDpi"
        seekbarDpi.progress = (selectedDpi - DPI_MIN).coerceIn(0, DPI_MAX - DPI_MIN)
        tvStatus.text = "DPI restaurado!"
        showToast("DPI resetado!")
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
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            showToast("Ative sobreposicao de apps!")
            return
        }
        val i = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        showToast("Flutuante ativado!")
    }

    private fun isGranted() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun requestShizukuPerm() {
        try {
            if (!Shizuku.pingBinder()) { showToast("Abra o Shizuku!"); return }
            if (isGranted()) { showToast("Ja autorizado!"); return }
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
            val r = Shizuku.pingBinder()
            val g = r && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            tvShizukuStatus.text = when { g -> "Shizuku: Autorizado"; r -> "Shizuku: Ativo - clique Autorizar"; else -> "Shizuku: Nao encontrado" }
            btnShizuku.isEnabled = r && !g
        } catch (_: Exception) { tvShizukuStatus.text = "Shizuku: Nao instalado" }
    }

    private fun realMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    private fun tint(c: Int) = android.content.res.ColorStateList.valueOf(c)
    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
