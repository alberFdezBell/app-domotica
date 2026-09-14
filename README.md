# Domótica - Aplicación Android para Home Assistant

Aplicación móvil nativa en Kotlin para Android que permite acceder a **Home Assistant** de forma transparente y optimizada, gestionando automáticamente la conexión en tiempo real entre la red local Wi-Fi y la VPN de **Tailscale**.

---

## 📱 Características Principales

1. **Configuración Inicial (First Run / Settings)**:
   - Pantalla de bienvenida y configuración accesible la primera vez que se abre la app o desde el icono de ajustes en la pantalla principal.
   - Permite personalizar los siguientes valores con persistencia local (`SharedPreferences`):
     - **Dirección Local**: (Por defecto: `http://192.168.0.24:8123`)
     - **Dirección VPN**: (Por defecto: `http://100.96.82.4:8123`)
     - **SSID del Wi-Fi Local**: (Por defecto: `Livebox6-0F37`)
     - Botón *"Guardar y Continuar"*.

2. **Panel WebView Fullscreen**:
   - Carga la interfaz completa de Home Assistant con soporte para JavaScript, almacenamiento DOM, cookies persistentes y gesto *Swipe-to-Refresh*.

3. **Lógica de Red y VPN (Tailscale Intents)**:
   - Monitoreo en tiempo real mediante `ConnectivityManager` y `NetworkCallback`.
   - **En Red Local**: Si el SSID del Wi-Fi coincide con el guardado, se desconecta la VPN de Tailscale enviando el Broadcast Intent `com.tailscale.ipn.DISCONNECT_VPN` y se carga la **Dirección Local**.
   - **En Datos Móviles / Red Externa**: Si el SSID no coincide o no hay Wi-Fi local, la app lanza en segundo plano el Broadcast Intent `com.tailscale.ipn.CONNECT_VPN` a `com.tailscale.ipn` y conmuta la URL a la **Dirección VPN**.
   - **Optimización de Batería**: Al pausar o cerrar la app (`onPause`), se envía automáticamente el Intent de desconexión de Tailscale.

---

## 🛠️ Requisitos Previos de Instalación

Para compilar este proyecto desde la línea de comandos / terminal se requiere:

1. **Java Development Kit (JDK)**:
   - **Versión Recomendada**: JDK 17 (Ej. OpenJDK 17, Eclipse Temurin 17 u Oracle JDK 17).
   - Verificar la instalación ejecutando: `java -version`
   - Asegurarse de tener configurada la variable de entorno `JAVA_HOME`.

2. **Android SDK**:
   - **Compile SDK**: 34 (Android 14)
   - **Min SDK**: 24 (Android 7.0)
   - Se requiere `ANDROID_HOME` o `ANDROID_SDK_ROOT` apuntando a la ruta de instalación del Android SDK (por ejemplo, `%LOCALAPPDATA%\Android\Sdk` en Windows o `~/Library/Android/sdk` en macOS).

---

## 🚀 Instrucciones de Compilación desde Consola / Terminal

El proyecto cuenta con el ejecutable **Gradle Wrapper**, lo que permite compilar la aplicación directamente desde la consola sin necesidad de abrir Android Studio ni instalar Gradle de forma global.

### Comandos según el Sistema Operativo:
- **Windows (CMD / PowerShell)**: Utiliza `gradlew.bat`
- **Linux / macOS**: Utiliza `./gradlew` (Asegúrate de otorgar permisos de ejecución con `chmod +x gradlew`).

---

### 📋 Comandos Específicos

#### 1. Limpiar el Proyecto (`clean`)
Elimina los archivos generados en compilaciones previas (carpeta `build`):
- **Windows**:
  ```cmd
  gradlew.bat clean
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew clean
  ```

#### 2. Compilar APK de Depuración (`assembleDebug`)
Genera el paquete APK en modo Debug, listo para instalar en emuladores o dispositivos físicos mediante USB debugging:
- **Windows**:
  ```cmd
  gradlew.bat assembleDebug
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew assembleDebug
  ```

#### 3. Compilar APK de Producción (`assembleRelease`)
Genera el paquete APK optimizado para producción:
- **Windows**:
  ```cmd
  gradlew.bat assembleRelease
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew assembleRelease
  ```

---

## 📍 Ruta Exacta de Archivos `.apk` Generados

Una vez finalizado el proceso de compilación, los archivos `.apk` resultantes se generan en las siguientes rutas relativas al proyecto:

### 🔹 APK de Depuración (Debug):
```text
app/build/outputs/apk/debug/app-debug.apk
```
*Ruta absoluta en este equipo*:
`c:\Users\alber\Desktop\app domotica\app\build\outputs\apk\debug\app-debug.apk`

### 🔹 APK de Producción (Release):
```text
app/build/outputs/apk/release/app-release.apk
```
*Ruta absoluta en este equipo*:
`c:\Users\alber\Desktop\app domotica\app\build\outputs\apk\release\app-release.apk`

---

## ⚙️ Estructura del Proyecto

```text
app domotica/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/domotica/app/
│       │   ├── MainActivity.kt
│       │   ├── SettingsActivity.kt
│       │   ├── PreferencesManager.kt
│       │   ├── NetworkMonitor.kt
│       │   └── TailscaleManager.kt
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── activity_settings.xml
│           ├── values/
│           │   ├── colors.xml
│           │   ├── strings.xml
│           │   └── themes.xml
│           └── drawable/ & mipmap/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```
