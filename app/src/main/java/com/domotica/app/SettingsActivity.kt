package com.domotica.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.domotica.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        loadExistingSettings()

        binding.btnTailscaleLogin.setOnClickListener {
            handleTailscaleLogin()
        }

        binding.btnSave.setOnClickListener {
            saveAndProceed()
        }
    }

    private fun loadExistingSettings() {
        binding.etLocalUrl.setText(prefsManager.localUrl)
        binding.etVpnUrl.setText(prefsManager.vpnUrl)
        binding.etLocalSsid.setText(prefsManager.localSsid)
        binding.etDeviceName.setText(prefsManager.deviceName)

        binding.etGotifyUrl.setText(prefsManager.gotifyUrl)
        binding.etGotifyUser.setText(prefsManager.gotifyUsername)
        binding.etGotifyPass.setText(prefsManager.gotifyPassword)
        binding.etTailscaleAuthKey.setText(prefsManager.tailscaleAuthKey)
    }

    private fun handleTailscaleLogin() {
        val authKey = binding.etTailscaleAuthKey.text.toString().trim()
        if (authKey.isNotBlank()) {
            TailscaleEmbeddedEngine.registerAuthKey(this, authKey)
            Toast.makeText(this, "Tailscale vinculado con la Auth Key ingresada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Abriendo página de Tailscale para obtener clave o iniciar sesión...", Toast.LENGTH_LONG).show()
            TailscaleEmbeddedEngine.openWebLogin(this)
        }
    }

    private fun saveAndProceed() {
        val localUrl = binding.etLocalUrl.text.toString().trim()
        val vpnUrl = binding.etVpnUrl.text.toString().trim()
        val localSsid = binding.etLocalSsid.text.toString().trim()
        val deviceName = binding.etDeviceName.text.toString().trim()

        val gotifyUrl = binding.etGotifyUrl.text.toString().trim()
        val gotifyUser = binding.etGotifyUser.text.toString().trim()
        val gotifyPass = binding.etGotifyPass.text.toString()

        if (localUrl.isEmpty()) {
            binding.tilLocalUrl.error = getString(R.string.error_empty_field)
            return
        } else {
            binding.tilLocalUrl.error = null
        }

        if (vpnUrl.isEmpty()) {
            binding.tilVpnUrl.error = getString(R.string.error_empty_field)
            return
        } else {
            binding.tilVpnUrl.error = null
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
            // If Gotify user & password are provided, perform authentication to fetch client token
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

            val tailscaleAuthKey = binding.etTailscaleAuthKey.text.toString().trim()

            prefsManager.saveSettings(
                localUrl = localUrl,
                vpnUrl = vpnUrl,
                localSsid = localSsid,
                deviceName = deviceName,
                gotifyUrl = gotifyUrl,
                gotifyUser = gotifyUser,
                gotifyPass = gotifyPass,
                tailscaleAuthKey = tailscaleAuthKey
            )

            // Start Gotify notification service if client token is present
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
