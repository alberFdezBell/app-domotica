# Guía de Notificaciones Internas con Gotify

La aplicación **Domótica** incluye un cliente nativo para **Gotify** integrado con un **Servicio en Primer Plano 24/7** y reconexión por WebSocket.

Esto garantiza que las notificaciones de alerta lleguen **siempre**, incluso si el teléfono se reinicia o si la app lleva semanas cerrada.

---

## ⚙️ Configuración en la App Domótica

1. Abre la pantalla de **Configuración** de la aplicación.
2. En la sección **Servidor de Notificaciones Gotify**, introduce:
   - **URL Servidor Gotify**: `https://gotify.aferbel.es` (por defecto)
   - **Usuario Gotify**: Tu usuario de tu servidor de Gotify
   - **Contraseña Gotify**: Tu contraseña
3. Pulsa **Guardar y Continuar**.

La app se autenticará automáticamente contra la API REST de Gotify (`POST /client`), obtendrá el token de cliente y arrancará el servicio en segundo plano con reconexión automática.

---

## 🏠 Configuración en Home Assistant para enviar a Gotify

### Método 1: Notificación mediante REST (`configuration.yaml`)

Añade esto a tu `configuration.yaml` de Home Assistant:

```yaml
notify:
  - name: gotify_domotica
    platform: rest
    resource: "https://gotify.aferbel.es/message?token=TU_APP_TOKEN_DE_GOTIFY"
    method: POST_JSON
    payload: >-
      {"title": "{{ title }}", "message": "{{ message }}", "priority": 5}
```

> **Nota**: Obtén tu `APP_TOKEN` creando una aplicación dentro del panel de administración web de Gotify (`https://gotify.aferbel.es`).

---

### Método 2: Desde el Editor Visual de Home Assistant (Sin YAML)

En la pantalla de automatizaciones de Home Assistant (**Ajustes -> Automatizaciones y Escenas -> Crear Automatización**):

1. **Acción**: Selecciona **Hacer solicitud HTTP** (*Make HTTP Request*).
2. **URL**: `https://gotify.aferbel.es/message?token=TU_APP_TOKEN_DE_GOTIFY`
3. **Método**: `POST`
4. **Cabecera**: `Content-Type: application/json`
5. **Cuerpo (Body)**:
   ```json
   {
     "title": "🚨 Alerta de Movimiento",
     "message": "Se ha detectado presencia en el salón",
     "priority": 5
   }
   ```

---

## 🔔 Comportamiento al Pulsar la Notificación

- Al llegar una notificación de Gotify, tu teléfono sonará y mostrará el aviso en la barra del sistema.
- **Al pulsar la notificación**:
  1. Se abre la app **Domótica**.
  2. Si estás fuera de casa, la app **conecta automáticamente la VPN de Tailscale**.
  3. Espera a que el túnel responda y carga el panel de **Home Assistant**.
