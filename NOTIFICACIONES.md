# Solución al Error 'Integration fcm not found' en Home Assistant

El aviso `Integration 'fcm' not found` aparece porque la plataforma estándar de notificaciones HTTP en Home Assistant se llama **`platform: rest`**.

---

## 🛠️ Configuración Correcta para `configuration.yaml`

Abre tu archivo `configuration.yaml` y sustituye el bloque anterior por el siguiente:

```yaml
notify:
  # 📢 Notificación a TODOS los dispositivos a la vez
  - name: domotica_todos
    platform: rest
    resource: "https://fcm.googleapis.com/fcm/send"
    method: POST_JSON
    headers:
      Authorization: "key=TU_SERVER_KEY_DE_FIREBASE"
      Content-Type: "application/json"
    payload: >-
      {"to": "/topics/todos", "notification": {"title": "{{ title }}", "body": "{{ message }}", "sound": "default"}, "priority": "high"}

  # 🎯 Notificación a SOLO 1 dispositivo (ej. Móvil Alberto)
  - name: domotica_alberto
    platform: rest
    resource: "https://fcm.googleapis.com/fcm/send"
    method: POST_JSON
    headers:
      Authorization: "key=TU_SERVER_KEY_DE_FIREBASE"
      Content-Type: "application/json"
    payload: >-
      {"to": "/topics/dispositivo_movil_alberto", "notification": {"title": "{{ title }}", "body": "{{ message }}", "sound": "default"}, "priority": "high"}
```

*(Una vez guardado, dale a **Verificar configuración** en Home Assistant y verás el mensaje verde de validación sin ninguna advertencia).*
