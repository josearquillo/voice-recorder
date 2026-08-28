# Voice Recorder

Grabadora de voz para Android. Sin anuncios. 100% offline.
Kotlin + Jetpack Compose + Foreground Service.

## Características

- Grabación de audio en segundo plano (sigue grabando aunque cierres la app)
- Notificación persistente mientras graba (con botón de detener)
- Lista de grabaciones con fecha y tamaño
- Reproducción dentro de la app
- Compartir grabaciones con otras apps
- Eliminar grabaciones con confirmación
- Formato: AAC/M4A, 44.1 kHz, 128 kbps
- Sin anuncios, sin internet, sin trackers

## Instalar

Descarga el APK desde [releases](https://github.com/josearquillo/voice-recorder/releases)
e instálalo en tu dispositivo Android (mínimo Android 7.0 / API 24).

Al abrir el APK, Android pedirá permiso para instalar desde "orígenes
desconocidos" → acepta y listo.

## Compilar

Requisitos: JDK 17.

```bash
gradlew.bat :app:assembleDebug      # Debug APK
gradlew.bat :app:assembleRelease    # Release APK
```

El APK se genera en `app/build/outputs/apk/release/app-release.apk`.

## Info técnica

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Java/Kotlin**: 17
- **Permisos**: micrófono, foreground service, notificaciones
- **Sin anuncios ni trackers**: no incluye ningún SDK de publicidad ni analítica
- **Grabaciones**: se guardan en el almacenamiento interno de la app (no ocupan espacio de la galería)

## Nota sobre privacidad

Android obliga a mostrar una notificación persistente cuando una app graba
audio en segundo plano. No es posible grabar de forma invisible. La notificación
puede ser discreta, pero tiene que existir.

## Licencia

© 2026 Jose Arquillo. Todos los derechos reservados.
