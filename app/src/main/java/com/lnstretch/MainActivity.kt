package com.lnstretch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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

    private val ffPackages = listOf(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    )
    private var selectedPercent = 15
    private var isStretched = false

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
        if (isStretched) {
            removeStretch()
            isStretched = false
        }
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
        tvSliderLabel.text = "+${pct}% lateral   +${expand}px cada lado"
    }

    private fun setupButtons() {
        btnStretch.setOnClickListener   { stretchAndOpen() }
        btnUnstretch.setOnClickListener { removeStretch() }
        btnOptimize.setOnClickListener  { optimizeFF() }
        btnOpenFF.setOnClickListener    { openFreefire() }
        btnShizuku.setOnClickListener   { requestShizukuPerm() }
    }

    @SuppressLint("SetTextI18n")
    private fun stretchAndOpen() {
        if (!isGranted()) {
            tvStatus.text = "Clique em AUTORIZAR SHIZUKU primeiro!"
            return
        }
        val m = realMetrics()
        val w = m.widthPixels
        // overscan negativo expande a imagem pras bordas laterais
        // -valor nos lados esquerdo e direito = estica horizontalmente
        val expand = (w * selectedPercent / 100.0 / 2).toInt()
        tvStatus.text = "Esticando laterais..."
        if (runCmd("wm overscan -${expand},0,-${expand},0")) {
            isStretched = true
            tvStatus.text = "Esticado +${selectedPercent}%!\nLaterais expandidas ${expand}px cada lado\nAbrindo Free Fire..."
            showToast("Esticado! Abrindo FF...")
            openFreefire()
        } else {
            tvStatus.text = "Erro. Verifique o Shizuku."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun removeStretch() {
        if (runCmd("wm overscan reset")) {
            isStretched = false
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
            val free = Runtime.getRuntime().freeMemory() / 1024 / 1024
            runOnUiThread {
                btnOptimize.isEnabled = true
                tvStatus.text = if (found) "Otimizado!\nRAM livre: ${free}MB"
                                else "Otimizado!\nRAM livre: ${free}MB"
                showToast("Pronto!")
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun openFreefire() {
        // Tenta pelos pacotes conhecidos
        for (pkg in ffPackages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        // Varre todos os apps instalados
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val pkg = app.packageName.lowercase()
            if (pkg.contains("freefire") || pkg.contains("freefir")) {
                try {
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        tvStatus.text = "FF aberto!\nTela esticada +${selectedPercent}%"
                        return
                    }
                } catch (_: Exception) {}
            }
        }
        tvStatus.text = "Free Fire nao encontrado!\nVerifique se esta instalado."
        showToast("FF nao encontrado!")
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
        val foundPkg = ffPackages.firstOrNull { pkg ->
            try { packageManager.getPackageInfo(pkg, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }
        tvStatus.text = "Resolucao: ${m.widthPixels}x${m.heightPixels}\n" +
            if (foundPkg != null) "FF detectado: $foundPkg" else "Free Fire nao encontrado"
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

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
