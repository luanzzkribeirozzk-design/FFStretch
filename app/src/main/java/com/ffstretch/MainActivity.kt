package com.ffstretch

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ffstretch.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val ffPackages = listOf(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    )

    private var selectedPercent: Int = 15

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            showToast("✅ Shizuku autorizado!")
            updateShizukuStatus()
        } else {
            showToast("❌ Permissão negada pelo Shizuku")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        setupSlider()
        setupButtons()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    private fun setupSlider() {
        // 0 = 10%, step 1% cada, max 30 = 40%
        binding.seekbarStretch.max = 30
        binding.seekbarStretch.progress = 5 // 15%
        updateSliderLabel(15)

        binding.seekbarStretch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedPercent = 10 + progress
                updateSliderLabel(selectedPercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @SuppressLint("SetTextI18n")
    private fun updateSliderLabel(percent: Int) {
        val metrics = getRealMetrics()
        val newWidth = (metrics.widthPixels * (1 + percent / 100.0)).toInt()
        binding.tvSliderLabel.text = "+${percent}%   ${metrics.widthPixels}px → ${newWidth}px"
    }

    private fun setupButtons() {
        binding.btnStretch.setOnClickListener { applyStretch() }
        binding.btnUnstretch.setOnClickListener { removeStretch() }
        binding.btnOptimize.setOnClickListener { optimizeFF() }
        binding.btnOpenFF.setOnClickListener { openFreefire() }
        binding.btnShizuku.setOnClickListener { requestShizukuPermission() }
    }

    @SuppressLint("SetTextI18n")
    private fun applyStretch() {
        if (!isShizukuReady()) {
            binding.tvStatus.text = "⚠️ Shizuku não autorizado!\nClique em AUTORIZAR SHIZUKU primeiro."
            showToast("Autorize o Shizuku primeiro!")
            return
        }
        val metrics = getRealMetrics()
        val origWidth = metrics.widthPixels
        val origHeight = metrics.heightPixels
        val newWidth = (origWidth * (1 + selectedPercent / 100.0)).toInt()
        val success = runShizukuCommand("wm size ${newWidth}x${origHeight}")
        if (success) {
            saveBackup(origWidth, origHeight, metrics.densityDpi)
            binding.tvStatus.text = "✅ Tela esticada +${selectedPercent}%!\n${origWidth}x${origHeight}  →  ${newWidth}x${origHeight}\n\n🎮 Abra o Free Fire agora!"
            showToast("Esticado +${selectedPercent}%! 🔥")
        } else {
            binding.tvStatus.text = "❌ Erro ao esticar. Verifique o Shizuku."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun removeStretch() {
        if (!isShizukuReady()) {
            showToast("Autorize o Shizuku primeiro!")
            return
        }
        val prefs = getSharedPreferences("ffstretch_prefs", Context.MODE_PRIVATE)
        val origWidth = prefs.getInt("orig_width", 0)
        val origHeight = prefs.getInt("orig_height", 0)
        val cmd = if (origWidth > 0) "wm size ${origWidth}x${origHeight}" else "wm size reset"
        val success = runShizukuCommand(cmd)
        if (success) {
            clearBackup()
            binding.tvStatus.text = "✅ Tela restaurada!\n${origWidth}x${origHeight}"
            showToast("Tela restaurada!")
        } else {
            binding.tvStatus.text = "❌ Erro ao restaurar.\nTente: wm size reset via Shizuku."
        }
    }

    @SuppressLint("SetTextI18n")
    private fun optimizeFF() {
        binding.tvStatus.text = "⚡ Otimizando..."
        binding.btnOptimize.isEnabled = false
        Thread {
            var ffFound = false
            if (isShizukuReady()) {
                runShizukuCommand("am kill-all")
                ffPackages.forEach { pkg ->
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        ffFound = true
                        runShizukuCommand("am send-trim-memory $pkg COMPLETE")
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
            }
            System.gc()
            Runtime.getRuntime().gc()
            val free = Runtime.getRuntime().freeMemory() / 1024 / 1024
            val total = Runtime.getRuntime().totalMemory() / 1024 / 1024
            runOnUiThread {
                binding.btnOptimize.isEnabled = true
                val ffStatus = if (ffFound) "✅ Free Fire otimizado" else "ℹ️ FF não encontrado"
                binding.tvStatus.text = "⚡ Pronto!\n$ffStatus\n• RAM livre: ${free}MB / ${total}MB\n• Apps background encerrados"
                showToast("FF otimizado! 🔥")
            }
        }.start()
    }

    @SuppressLint("SetTextI18n")
    private fun openFreefire() {
        var launched = false
        for (pkg in ffPackages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    launched = true
                    binding.tvStatus.text = "🎮 Abrindo Free Fire...\n✅ Tela +${selectedPercent}% ativa"
                    break
                }
            } catch (_: Exception) {}
        }
        if (!launched) {
            binding.tvStatus.text = "❌ Free Fire não encontrado!\nInstale o jogo primeiro."
            showToast("Free Fire não instalado!")
        }
    }

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                showToast("Shizuku não está rodando!\nAbra o app Shizuku primeiro.")
                binding.tvStatus.text = "❌ Shizuku não encontrado!\nInstale e inicie o app Shizuku."
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                showToast("Shizuku já está autorizado ✅")
                updateShizukuStatus()
                return
            }
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            binding.tvStatus.text = "❌ Erro: ${e.message}\nInstale o app Shizuku."
        }
    }

    private fun runShizukuCommand(command: String): Boolean {
        return try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", command), null, null
            )
            process.waitFor()
            true
        } catch (_: Exception) { false }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatus() {
        val metrics = getRealMetrics()
        val ffInstalled = ffPackages.any { pkg ->
            try { packageManager.getPackageInfo(pkg, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }
        val ffStatus = if (ffInstalled) "✅ Free Fire detectado" else "❌ Free Fire não instalado"
        binding.tvStatus.text = "📱 Resolução: ${metrics.widthPixels}x${metrics.heightPixels}\n$ffStatus"
        updateShizukuStatus()
    }

    @SuppressLint("SetTextI18n")
    private fun updateShizukuStatus() {
        try {
            val running = Shizuku.pingBinder()
            val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            binding.tvShizukuStatus.text = when {
                granted -> "🟢 Shizuku: Autorizado"
                running -> "🟡 Shizuku: Ativo (clique Autorizar)"
                else    -> "🔴 Shizuku: Não encontrado"
            }
            binding.btnShizuku.isEnabled = running && !granted
        } catch (_: Exception) {
            binding.tvShizukuStatus.text = "🔴 Shizuku: Não instalado"
        }
    }

    private fun getRealMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun saveBackup(width: Int, height: Int, dpi: Int) {
        getSharedPreferences("ffstretch_prefs", Context.MODE_PRIVATE).edit()
            .putInt("orig_width", width)
            .putInt("orig_height", height)
            .putInt("orig_dpi", dpi)
            .apply()
    }

    private fun clearBackup() {
        getSharedPreferences("ffstretch_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
