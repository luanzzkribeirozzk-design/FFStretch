package com.lnstretch

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
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
        } else {
            showToast("Permissao negada")
        }
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
        btnStretch.setOnClickListener   { esticar() }
        btnUnstretch.setOnClickListener { restaurar() }
        btnOptimize.setOnClickListener  { otimizar() }
        btnOpenFF.setOnClickListener    { showToast("Abra o FF manualmente!") }
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
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateSliderLabel(pct: Int) {
        val m = realMetrics()
        val expand = (m.widthPixels * pct / 100.0 / 2).toInt()
        tvSliderLabel.text = "+${pct}%  (+${expand}px cada lado)"
    }

    @SuppressLint("SetTextI18n")
    private fun esticar() {
        if (!isGranted()) {
            tvStatus.text = "Autorize o Shizuku primeiro!\nClique em AUTORIZAR SHIZUKU."
            showToast("Autorize o Shizuku!")
            return
        }
        val m = realMetrics()
        val expand = (m.widthPixels * selectedPercent / 100.0 / 2).toInt()

        // wm overscan estica a imagem nas laterais (efeito cachoeira)
        val ok1 = runCmd("wm overscan -${expand},0,-${expand},0")
        // wm size aumenta a largura logica para preencher o overscan
        val newW = m.widthPixels + (expand * 2)
        val ok2 = runCmd("wm size ${newW}x${m.heightPixels}")

        if (ok1 || ok2) {
            saveBackup(m.widthPixels, m.heightPixels, m.densityDpi, expand)
            tvStatus.text = "Tela esticada +${selectedPercent}%!\nAgora abra o Free Fire."
            showToast("Esticado! Abra o FF agora.")
        } else {
            tvStatus.text = "Erro! Shizuku nao esta funcionando.\nAbra o Shizuku e autorize."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun restaurar() {
        runCmd("wm overscan reset")
        runCmd("wm size reset")
        clearBackup()
        tvStatus.text = "Tela restaurada ao normal!"
        showToast("Restaurado!")
    }

    @SuppressLint("SetTextI18n")
    private fun otimizar() {
        tvStatus.text = "Otimizando memoria..."
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

    private fun isGranted() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun requestShizukuPerm() {
        try {
            if (!Shizuku.pingBinder()) {
                showToast("Abra o app Shizuku primeiro!")
                tvStatus.text = "Shizuku nao encontrado!\nInstale e abra o Shizuku."
                return
            }
            if (isGranted()) {
                showToast("Shizuku ja autorizado!")
                return
            }
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            showToast("Erro: ${e.message}")
        }
    }

    private fun runCmd(cmd: String): Boolean {
        return try {
            val p: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.waitFor()
            true
        } catch (_: Exception) { false }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatus() {
        val m = realMetrics()
        tvStatus.text = "Resolucao atual: ${m.widthPixels}x${m.heightPixels}\nDPI: ${m.densityDpi}"
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

    private fun saveBackup(w: Int, h: Int, dpi: Int, expand: Int) {
        getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit()
            .putInt("orig_width", w).putInt("orig_height", h)
            .putInt("orig_dpi", dpi).putInt("expand", expand).apply()
    }

    private fun clearBackup() {
        getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
