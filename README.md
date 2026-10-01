# Domótica - Aplicación Android para Home Assistant (WireGuard Embebido)

Aplicación móvil nativa en Kotlin para Android que permite acceder a **Home Assistant** de forma transparente y optimizada, utilizando la **misma dirección IP local** tanto dentro de casa (Wi-Fi) como fuera de casa (datos móviles) gracias a la integración nativa de **WireGuard (Wg-Easy)** embebido con lector de códigos QR.

---

## 📱 Características Principales

1. **Configuración Única e Intuitiva (First Run / Settings)**:
   - Pantalla de configuración inicial accesible la primera vez que se abre la app o mediante la combinación secreta de gestos desde el panel principal.
   - Permite escanear directamente el **código QR de tu servidor Wg-Easy** desde la cámara del teléfono usando la librería embebida de escaneo.
   - Se utiliza **una única URL para Home Assistant** (ej. `http://192.168.0.24:8123`), ya que el túnel WireGuard conecta directamente el móvil a la subred local de tu hogar.

2. **Acceso Oculto a Ajustes (Combinación Secreta de Gestos)**:
   - Para abrir la pantalla de Configuración en cualquier momento sin ocupar espacio visual en pantalla con botones:
     👉 **Mantén pulsado en la esquina inferior izquierda de la pantalla y da 3 toques seguidos en la esquina inferior derecha**.
   - El teléfono emitirá una breve vibración de confirmación y abrirá la pantalla de Ajustes.

3. **Notificación Persistente 24/7 (Gotify)**:
   - Incluye un servicio en primer plano (`Foreground Service`) que mantiene la conexión WebSocket permanente con tu servidor Gotify.
   - Muestra una notificación fija en la barra del sistema indicando que el servicio está activo para evitar que Android o los economizadores de batería maten el proceso.

4. **Panel WebView Fullscreen**:
   - Carga la interfaz completa de Home Assistant con soporte para JavaScript, almacenamiento DOM, cookies persistentes y gesto *Swipe-to-Refresh*.

5. **Lógica de Red y VPN (WireGuard GoBackend Embebido)**:
   - Monitoreo en tiempo real mediante `ConnectivityManager` y `NetworkCallback`.
   - **En Red Wi-Fi Local**: Si el SSID del Wi-Fi coincide con el guardado, el túnel WireGuard se detiene automáticamente y la app conecta directamente por Wi-Fi a la velocidad máxima.
   - **En Datos Móviles / Red Externa**: Al salir de casa, la app levanta en segundo plano el túnel WireGuard embebido (`WireGuardTunnelEngine` / `GoBackend`), permitiendo cargar exactamente la **misma IP local** de Home Assistant.

---

## 📲 Puesta en Marcha Operativa con Wg-Easy (Paso a Paso)

Para activar el acceso remoto transparente con WireGuard desde tu servidor **Wg-Easy**:

1. Abre el panel de administración web de tu **Wg-Easy** en tu navegador (ej. `http://192.168.0.24:51821`).
2. Crea un nuevo cliente/dispositivo (ej. `Movil-Alberto`).
3. Haz clic en el botón de **Mostrar Código QR** (*Show QR code*).
4. En la app **Domótica**, abre **Ajustes** (mediante el gesto secreto: mantener abajo-izquierda + 3 toques abajo-derecha).
5. Pulsa en el botón **📷 Escanear QR de Wg-Easy** y apunta la cámara al código QR en la pantalla de tu ordenador.
6. La configuración de WireGuard se cargará instantáneamente. Asegúrate de tener introducida la dirección de tu Home Assistant (ej. `http://192.168.0.24:8123`) y pulsa **Guardar y Continuar**.

---

## 🧹 Cómo Borrar Restos de Compilaciones (Limpieza de Caché)

Para eliminar archivos temporales, binarios antiguos, cachés de DataBinding o clases obsoletas antes de generar un nuevo APK:

### 1. Limpiar Archivos de Compilación Previa (`clean`):
- **Windows**:
  ```cmd
  gradlew.bat clean
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew clean
  ```

### 2. Limpiar y Reconstruir APK de Depuración en un Solo Paso:
- **Windows**:
  ```cmd
  gradlew.bat clean assembleDebug
  ```
- **Linux / macOS**:
  ```bash
  ./gradlew clean assembleDebug
  ```

---

## 🛠️ Solución de Problemas Frecuentes

### ❌ 1. Error: *"Gotify no se pudo conectar, comprueba usuario y contraseña"*
- **URL sin protocolo**: Si tu servidor Gotify es `gotify.aferbel.es`, no te preocupes, la app añade automáticamente `https://`.
- **Credenciales con caracteres especiales**: La autenticación Basic Auth codifica ahora la contraseña en UTF-8 estándar.
- **Cliente ya creado**: La app consulta primero la API de Gotify para reutilizar el cliente existente sin duplicar tokens.

### ❌ 2. Notificación persistente no visible en la barra de estado
- Asegúrate de aceptar el permiso de **Notificaciones** al abrir la app (en Android 13+).
- En los ajustes del sistema -> *Aplicaciones -> Domótica -> Notificaciones*, comprueba que el canal *"Servicio de Alertas Domótica"* esté activado.

### ❌ 3. Error: *"No se pudo establecer el túnel WireGuard a tiempo"*
- **Aceptar permiso VPN de Android**: La primera vez que conectes fuera de casa, Android mostrará una ventana preguntando si permites a Domótica crear una conexión VPN. Pulsa en **Aceptar**.
- **Falta de Configuración**: Asegúrate de haber escaneado el código QR de Wg-Easy en Ajustes.

---

## 🛠️ Requisitos Previos de Instalación

Para compilar este proyecto se requiere:
1. **JDK 17**: Configurado en la variable `JAVA_HOME`.
2. **Android SDK**: `Compile SDK 34` y `Min SDK 24`.

---

## 🚀 Instrucciones de Compilación

### Comandos de Compilación:
- **Windows**:
  - Depuración: `gradlew.bat assembleDebug`
  - Producción Firmada: `gradlew.bat assembleRelease`
- **Linux / macOS**:
  - Depuración: `./gradlew assembleDebug`
  - Producción Firmada: `./gradlew assembleRelease`

---

## 🔑 Configuración de Firma para APK Release

1. Crea el archivo `keystore.properties` a partir de `keystore.properties.example` con las claves de tu `.keystore`.
2. Ejecuta `gradlew.bat assembleRelease` para generar el paquete APK firmado listo para distribución.

---

## 📍 Ruta de Archivos `.apk` Generados

- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

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
│       │   ├── GotifyClientHelper.kt
│       │   ├── GotifyNotificationService.kt
│       │   ├── WireGuardManager.kt
│       │   └── WireGuardTunnelEngine.kt
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
