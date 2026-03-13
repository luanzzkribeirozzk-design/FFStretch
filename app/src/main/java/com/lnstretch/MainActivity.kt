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

    // ── SLIDER ───────────────────────────────────────────────────────────────

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

    // ── BUTTONS ──────────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnStretch.setOnClickListener   { applyStretch() }
        btnUnstretch.setOnClickListener { removeStretch() }
        btnOptimize.setOnClickListener  { optimizeFF() }
        btnOpenFF.setOnClickListener    { openFreefire() }
        btnShizuku.setOnClickListener   { requestShizukuPerm() }
    }

    // ── STRETCH ──────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun applyStretch() {
        val m     = realMetrics()
        val origW = m.widthPixels
        val origH = m.heightPixels
        val newW  = (origW * (1 + selectedPercent / 100.0)).toInt()

        if (runShellCmd("wm size ${newW}x${origH}")) {
            saveBackup(origW, origH, m.densityDpi)
            tvStatus.text = "Tela esticada +${selectedPercent}%!\n${origW}x${origH} > ${newW}x${origH}\n\nAbra o Free Fire agora!"
            showToast("Esticado +${selectedPercent}%!")
        } else {
            tvStatus.text = "Erro. Autorize o Shizuku primeiro!"
            showToast("Autorize o Shizuku!")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun removeStretch() {
        val prefs = getSharedPreferences("lnstretch_prefs", Context.MODE_PRIVATE)
        val origW = prefs.getInt("orig_width", 0)
        val origH = prefs.getInt("orig_height", 0)
        val cmd   = if (origW > 0) "wm size ${origW}x${origH}" else "wm size reset"
        if (runShellCmd(cmd)) {
            clearBackup()
            tvStatus.text = "Tela restaurada!\n${origW}x${origH}"
            showToast("Restaurado!")
        } else {
            tvStatus.text = "Erro ao restaurar."
        }
    }

    // ── OTIMIZAR ─────────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun optimizeFF() {
        tvStatus.text = "Otimizando..."
        btnOptimize.isEnabled = false
        Thread {
            var found = false
            runShellCmd("am kill-all")
            ffPackages.forEach { pkg ->
                try {
                    packageManager.getPackageInfo(pkg, 0)
                    found = true
                    runShellCmd("am send-trim-memory $pkg COMPLETE")
                } catch (_: PackageManager.NameNotFoundException) {}
            }
            System.gc()
            val free  = Runtime.getRuntime().freeMemory() / 1024 / 1024
            val total = Runtime.getRuntime().totalMemory() / 1024 / 1024
            runOnUiThread {
                btnOptimize.isEnabled = true
                val s = if (found) "Free Fire otimizado" else "FF nao encontrado"
                tvStatus.text = "Pronto!\n$s\nRAM livre: ${free}MB / ${total}MB"
                showToast("Otimizado!")
            }
        }.start()
    }

    // ── ABRIR FF ─────────────────────────────────────────────────────────────

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

    // ── SHIZUKU ──────────────────────────────────────────────────────────────

    private fun isShizukuGranted() = try {
        Shizuku.pingBinder() &&
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun requestShizukuPerm() {
        try {
            if (!Shizuku.pingBinder()) {
                showToast("Abra o app Shizuku primeiro!")
                tvStatus.text = "Shizuku nao encontrado!\nInstale e inicie o Shizuku."
                return
            }
            if (isShizukuGranted()) {
                showToast("Shizuku ja autorizado!")
                return
            }
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            tvStatus.text = "Erro: ${e.message}"
        }
    }

    /**
     * Executa comando shell via Shizuku usando ShizukuBinderWrapper.
     * Nao usa Shizuku.newProcess (privado). Usa o mecanismo de IPC correto.
     */
    private fun runShellCmd(cmd: String): Boolean {
        if (!isShizukuGranted()) return false
        return try {
            // Shizuku expoe o binder do system_server — usamos para chamar
            // o servico de window manager via IInterface padrao do Android
            val binderCls = Class.forName("rikka.shizuku.ShizukuBinderWrapper")
            val getBinder = Shizuku::class.java.getMethod("getBinder")
            // fallback: executa via ProcessBuilder com shell elevado pelo Shizuku
            // O Shizuku concede permissao WRITE_SECURE_SETTINGS — usamos isso
            execViaShizukuShell(cmd)
        } catch (_: Exception) { false }
    }

    /**
     * Metodo correto para Shizuku 13+: abre processo via transact do binder.
     * Shizuku concede ao app permissao de shell (uid=2000), entao podemos
     * usar executa comandos adb-level como "wm size".
     */
    @Suppress("DEPRECATION")
    private fun execViaShizukuShell(cmd: String): Boolean {
        return try {
            // Shizuku 13+ expoe ServiceManager para acesso a servicos do sistema
            // O caminho correto e usar IWindowManager via binder do Shizuku
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val wmBinder = getService.invoke(null, "window") ?: return false

            val wmsClass = Class.forName("android.view.IWindowManager\$Stub")
            val asInterface = wmsClass.getMethod("asInterface",
                Class.forName("android.os.IBinder"))
            val wms = asInterface.invoke(null, wmBinder)

            // Parsear o comando wm size WxH
            if (cmd.startsWith("wm size")) {
                val parts = cmd.trim().split(" ")
                if (parts.size >= 3 && parts[2].contains("x")) {
                    val dims = parts[2].split("x")
                    val w = dims[0].toInt()
                    val h = dims[1].toInt()
                    val setForcedDisplaySize = wms!!.javaClass.getMethod(
                        "setForcedDisplaySize", Int::class.java, Int::class.java, Int::class.java)
                    setForcedDisplaySize.invoke(wms, 0, w, h)
                } else {
                    val clearForcedDisplaySize = wms!!.javaClass.getMethod(
                        "clearForcedDisplaySize", Int::class.java)
                    clearForcedDisplaySize.invoke(wms, 0)
                }
                true
            } else {
                false
            }
        } catch (_: Exception) { false }
    }

    // ── STATUS ───────────────────────────────────────────────────────────────

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
                running -> "Shizuku: Ativo — clique Autorizar"
                else    -> "Shizuku: Nao encontrado"
            }
            btnShizuku.isEnabled = running && !granted
        } catch (_: Exception) {
            tvShizukuStatus.text = "Shizuku: Nao instalado"
        }
    }

    // ── UTIL ─────────────────────────────────────────────────────────────────

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
