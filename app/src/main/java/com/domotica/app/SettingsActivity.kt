package com.domotica.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.domotica.app.databinding.ActivitySettingsBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager

    // QR Code scanner launcher for WireGuard Wg-Easy QR codes
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val qrText = result.contents
            binding.etWireGuardConfig.setText(qrText)
            binding.tvWireGuardStatus.text = "✅ Configuración WireGuard cargada desde QR"
            Toast.makeText(this, "¡Código QR de WireGuard escaneado con éxito!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Escaneo de QR cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        loadExistingSettings()

        binding.btnScanQr.setOnClickListener {
            startQrScanner()
        }

        binding.btnSave.setOnClickListener {
            saveAndProceed()
        }
    }

    private fun startQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Escanea el código QR de WireGuard (Wg-Easy)")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }
        qrScanLauncher.launch(options)
    }

    private fun loadExistingSettings() {
        binding.etLocalUrl.setText(prefsManager.localUrl)
        binding.etVpnUrl.setText(prefsManager.vpnUrl)
        binding.etLocalSsid.setText(prefsManager.localSsid)
        binding.etDeviceName.setText(prefsManager.deviceName)

        binding.etGotifyUrl.setText(prefsManager.gotifyUrl)
        binding.etGotifyUser.setText(prefsManager.gotifyUsername)
        binding.etGotifyPass.setText(prefsManager.gotifyPassword)

        val currentWgConfig = prefsManager.wireguardConfig
        binding.etWireGuardConfig.setText(currentWgConfig)
        if (currentWgConfig.isNotBlank()) {
            binding.tvWireGuardStatus.text = "✅ Configuración WireGuard cargada y lista"
        } else {
            binding.tvWireGuardStatus.text = "⚠️ Sin configuración WireGuard. Escanea el QR de Wg-Easy."
        }
    }

    private fun saveAndProceed() {
        val localUrl = binding.etLocalUrl.text.toString().trim()
        val localSsid = binding.etLocalSsid.text.toString().trim()
        val deviceName = binding.etDeviceName.text.toString().trim()

        val gotifyUrl = binding.etGotifyUrl.text.toString().trim()
        val gotifyUser = binding.etGotifyUser.text.toString().trim()
        val gotifyPass = binding.etGotifyPass.toString()
        val wireguardConfig = binding.etWireGuardConfig.text.toString().trim()

        if (localUrl.isEmpty()) {
            binding.tilLocalUrl.error = getString(R.string.error_empty_field)
            return
        } else {
            binding.tilLocalUrl.error = null
        }

        if (localSsid.isEmpty()) {
            binding.tilLocalSsid.error = getString(R.string.error_empty_field)
            return
        } else {
            binding.tilLocalSsid.error = null
        }

        if (deviceName.isEmpty()) {
            binding.tilDeviceName.error = getString(R.string.error_empty_field)
            return
        } else {
            binding.tilDeviceName.error = null
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.text = "Guardando..."

        lifecycleScope.launch {
            if (gotifyUrl.isNotBlank() && gotifyUser.isNotBlank() && gotifyPass.isNotBlank()) {
                val fetchedToken = withContext(Dispatchers.IO) {
                    GotifyClientHelper.authenticateAndGetClientToken(gotifyUrl, gotifyUser, gotifyPass, deviceName)
                }

                if (!fetchedToken.isNullOrBlank()) {
                    prefsManager.gotifyClientToken = fetchedToken
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@SettingsActivity,
                            "Aviso: No se pudo conectar a Gotify. Comprueba usuario/contraseña.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            prefsManager.saveSettings(
                localUrl = localUrl,
                vpnUrl = localUrl,
                localSsid = localSsid,
                deviceName = deviceName,
                gotifyUrl = gotifyUrl,
                gotifyUser = gotifyUser,
                gotifyPass = gotifyPass,
                wireguardConfig = wireguardConfig
            )

            if (prefsManager.gotifyClientToken.isNotBlank()) {
                GotifyNotificationService.startService(this@SettingsActivity)
            }

            Toast.makeText(this@SettingsActivity, "Configuración guardada", Toast.LENGTH_SHORT).show()

            val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
