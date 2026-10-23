# Validación · 21 de octubre de 2026

## Comprobado en la máquina de desarrollo

- Instalación repetible con `bash setup.sh` y compilación con `bash build.sh`.
- Compilación Java y recursos Android; conversión DEX y empaquetado APK.
- Firma APK verificada con `apksigner`: esquemas v2 y v3 válidos.
- Alineación del paquete verificada con `zipalign -c -v 4`.
- Manifiesto verificado: `local.lens`, versión 1.0, API mínima 26 y API objetivo 35. No contiene bibliotecas nativas específicas de una arquitectura.
- `bash test.sh`: **11 comprobaciones aprobadas** del servidor HTTP real con fuentes de sensores simuladas. Incluye página, autenticación obligatoria en estado/imagen/audio, estado, ausencia de imagen sin activación, transporte de imagen sin caché, micrófono apagado, transporte PCM, ruta desconocida y cierre del puerto.
- Sintaxis JavaScript del panel validada con Node.js.

## Alcance

La firma es de desarrollo local, adecuada para instalar y probar directamente; no es una publicación en una tienda. El contenido usa HTTP sin cifrar, restringido a redes privadas/de enlace local y loopback.

Los tests del servidor no verifican cámaras físicas, sonido audible, permisos del fabricante, sincronización audiovisual ni reproducción en navegadores reales. La comprobación final debe hacerse con el teléfono y otro dispositivo en la misma Wi-Fi siguiendo README.md. El código retransmite imagen y micrófono; no guarda grabaciones.

## Prueba Android en este entorno

Emulador Android 15/API 35, imagen AOSP x86_64 oficial, ejecutado sin aceleración de CPU:

- Instalación directa con `adb install --no-incremental -r`: **Success**. Reinstalación de la APK final: **Success**.
- Apertura de `local.lens/.MainActivity` y revisión de la pantalla principal.
- Servicio en primer plano confirmado por Android, con notificación persistente y acción de detener.
- El servidor dentro de Android devuelve HTTP 200 y el mismo HTML del proyecto, mediante redirección ADB para prueba interna.
- `/status` rechaza peticiones sin código con HTTP 401; con el código visible en la app devuelve HTTP 200.
- `/frame` devuelve HTTP 204 antes de activar una fuente.
- Tras pulsar Cámara trasera en la app, `/frame` entregó un JPEG de 5.534 bytes desde la cámara emulada.
- Se pulsó DETENER TODO al terminar la prueba.

El arranque por software tardó varios minutos y System UI sufrió bloqueos; UIAutomator no pudo completar su conexión. La inspección visual y las peticiones HTTP sí permitieron las comprobaciones anteriores. No se validaron en este emulador la cámara frontal, el flujo completo de consentimiento/captura de pantalla ni la reproducción de audio del navegador. Estos puntos, así como el hardware físico y la conectividad entre dos dispositivos de una LAN real, siguen pendientes de prueba en el dispositivo del usuario. No se declara validación funcional completa de esas capacidades.

Se guardaron `install_script` y `start_skill` en el borrador del entorno. Esto no equivale a publicar el entorno ni verificar su restauración en una tarea nueva.


## Actualización 1.1 · solicitud de desinstalación

- Compilación de APK 1.1 (versionCode 2), firmada con la misma clave local para permitir actualización.
- 15 pruebas HTTP aprobadas, incluidas cuatro nuevas: POST sin código rechazado, GET sin efectos, POST autenticado aceptado (202) y rechazo de POST en rutas de lectura.
- Sintaxis del JavaScript comprobada y firma del APK verificada.
- El backend de pruebas simula la recepción de la solicitud; estas pruebas no ejecutan ni validan el diálogo nativo de desinstalación. La confirmación local y el diálogo de Android de la versión 1.1 están pendientes de prueba en un dispositivo. Las pruebas Android descritas arriba corresponden a la versión 1.0.
