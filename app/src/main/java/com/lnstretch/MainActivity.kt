package com.lnstretch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val ffPackages = listOf("com.dts.freefireth", "com.dts.freefiremax")
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
        val newW = (m.widthPixels * (1 + pct / 100.0)).toInt()
        tvSliderLabel.text = "+${pct}%   ${m.widthPixels}px > ${newW}px"
    }

    private fun setupButtons() {
        btnStretch.setOnClickListener   { applyStretch() }
        btnUnstretch.setOnClickListener { removeStretch() }
        btnOptimize.setOnClickListener  { optimizeFF() }
        btnOpenFF.setOnClickListener    { openFreefire() }
        btnShizuku.setOnClickListener   { requestShizukuPerm() }
    }

    @SuppressLint("SetTextI18n")
    private fun applyStretch() {
        if (!isGranted()) {
            tvStatus.text = "Shizuku nao autorizado!\nClique em AUTORIZAR SHIZUKU."
            return
        }
        val m     = realMetrics()
        val origW = m.widthPixels
        val origH = m.heightPixels
        val newW  = (origW * (1 + selectedPercent / 100.0)).toInt()
        if (runCmd("wm size ${newW}x${origH}")) {
            saveBackup(origW, origH, m.densityDpi)
            tvStatus.text = "Tela esticada +${selectedPercent}%!\n${origW}x${origH} > ${newW}x${origH}\n\nAbra o Free Fire agora!"
            showToast("Esticado +${selectedPercent}%!")
        } else {
            tvStatus.text = "Erro ao esticar."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun removeStretch() {
        if (!isGranted()) { showToast("Autorize o Shizuku!"); return }
        val prefs = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
        val origW = prefs.getInt("orig_width", 0)
        val origH = prefs.getInt("orig_height", 0)
        val cmd = if (origW > 0) "wm size ${origW}x${origH}" else "wm size reset"
        if (runCmd(cmd)) {
            clearBackup()
            tvStatus.text = "Tela restaurada!"
            showToast("Restaurado!")
        } else {
            tvStatus.text = "Erro ao restaurar."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun optimizeFF() {
        tvStatus.text = "Otimizando..."
        btnOptimize.isEnabled = false
        Thread {
            if (isGranted()) runCmd("am kill-all")
            var found = false
            ffPackages.forEach { pkg ->
                try {
                    packageManager.getPackageInfo(pkg, 0)
                    found = true
                    if (isGranted()) runCmd("am send-trim-memory $pkg COMPLETE")
                } catch (_: PackageManager.NameNotFoundException) {}
            }
            System.gc()
            val free  = Runtime.getRuntime().freeMemory() / 1024 / 1024
            val total = Runtime.getRuntime().totalMemory() / 1024 / 1024
            runOnUiThread {
                btnOptimize.isEnabled = true
                tvStatus.text = if (found)
                    "Otimizado!\nFree Fire otimizado\nRAM: ${free}MB livre"
                else
                    "Otimizado!\nFF nao encontrado\nRAM: ${free}MB livre"
                showToast("Pronto!")
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun openFreefire() {
        for (pkg in ffPackages) {
            val intent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            tvStatus.text = "Abrindo Free Fire...\nTela +${selectedPercent}% ativa"
            return
        }
        tvStatus.text = "Free Fire nao encontrado!"
        showToast("Instale o Free Fire!")
    }

    private fun isGranted() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun requestShizukuPerm() {
        try {
            if (!Shizuku.pingBinder()) {
                showToast("Abra o app Shizuku primeiro!")
                tvStatus.text = "Shizuku nao encontrado!\nInstale e abra o app Shizuku."
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
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", cmd), null, null
            )
            process.waitFor()
            true
        } catch (_: Exception) { false }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatus() {
        val m = realMetrics()
        val ff = ffPackages.any { pkg ->
            try { packageManager.getPackageInfo(pkg, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }
        tvStatus.text = "Resolucao: ${m.widthPixels}x${m.heightPixels}\n" +
            if (ff) "Free Fire detectado" else "Free Fire nao instalado"
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
