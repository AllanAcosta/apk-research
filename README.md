# Visor local · Android

Aplicación Android nativa, escrita desde cero en Java, que sirve un panel web en la misma red local. Android 8.0 (API 26) o posterior; compilada con API 35, sin dependencias de terceros en la APK.

[Descargar APK firmada de prueba](https://github.com/AllanAcosta/apk-research/raw/refs/heads/main/downloads/visor-local-1.0.apk) · [SHA-256](downloads/SHA256SUMS)

## Instalar y usar

1. Instala `build/visor-local-1.0.apk` en el teléfono. Android puede pedir autorización para instalar aplicaciones desde el navegador o gestor de archivos usado. La APK está firmada para pruebas con una clave local, no destinada a una tienda.
2. Conecta el teléfono y el navegador del otro dispositivo a la misma red Wi-Fi. Algunas redes de invitados aíslan dispositivos y no permiten conectarlos entre sí.
3. Abre **Visor local**, elige un puerto entre 1024 y 65535 (8080 por defecto) y pulsa **Iniciar servidor**. Concede cámara, micrófono y, desde Android 13, notificaciones. Si deniegas permanentemente un permiso, habilítalo en Ajustes del sistema.
4. Abre en el otro dispositivo la dirección `http://IP_DEL_TELEFONO:PUERTO` que aparece en la app e introduce el código de acceso. El código cambia cada vez que reinicias el servidor; puedes copiarlo con el botón de la app.
5. Activa **Cámara trasera**, **Cámara frontal** o **Compartir pantalla** en el teléfono. Android requiere una confirmación adicional para compartir la pantalla. Solo hay una fuente visual activa a la vez.
6. Para audio, activa el micrófono en el teléfono y pulsa **Escuchar audio** en el navegador. Usa auriculares para evitar realimentación.
7. **Apagar imagen** libera la cámara o captura. El botón del micrófono la activa o detiene por separado. **DETENER TODO**, también disponible en la notificación, cierra el servidor, desconecta los clientes y libera todos los recursos.

El navegador recibe JPEG hasta 5 imágenes/s y PCM mono de 16 kHz. Es una transmisión de supervisión local de baja frecuencia de imagen, no vídeo H.264/WebRTC. Audio e imagen son flujos independientes y no tienen sincronización audiovisual garantizada. La app retransmite; esta versión no guarda grabaciones ni captura audio interno de otras apps. Admite una cámara frontal y una trasera según disponibilidad del dispositivo, no todas las lentes físicas simultáneamente.

La pantalla conserva las dimensiones con las que se autorizó la captura. Al girar el teléfono, apaga y vuelve a activar la captura. Las aplicaciones con contenido protegido pueden aparecer negras. El fabricante puede limitar cámaras y micrófono en segundo plano o al bloquear el teléfono; valida el comportamiento en tu dispositivo.

## Privacidad y límites de red

La app no oculta indicadores, no se inicia con el dispositivo y no permite activar sensores desde el navegador. El usuario del teléfono controla cada fuente. Mantiene un servicio en primer plano con notificación y respeta los avisos y permisos de Android.

El código aleatorio de 128 bits protege las rutas de contenido; no se incluye en URL ni se guarda en el navegador. Detener el servidor revoca todos los accesos. La página inicial es pública, pero no contiene imágenes, audio ni estado de sensores. El servidor acepta clientes de redes privadas, direcciones de enlace local y loopback; no publica el servicio por Internet. No configures redirección de puertos en el router.

**HTTP no cifra el código, las imágenes ni el audio.** Utiliza únicamente una LAN de confianza; el código no protege frente a alguien que intercepte el tráfico. No compartas la pantalla mientras muestre información privada que no quieras transmitir.

## Compilar en este entorno

Requisitos: Linux x86_64, Java 21, Python 3.11 o posterior, `keytool`. Node.js solo se usa para validar JavaScript en los tests. No se necesita Gradle ni un JDK con `javac`: Eclipse ECJ compila Java y las herramientas oficiales de Android producen y firman la APK.

```sh
cd /workspace/apk-research
bash setup.sh
bash test.sh
```

`setup.sh` instala en `/workspace/android-sdk` las plataformas y herramientas oficiales y descarga ECJ en `/workspace/tooling`. Verifica los checksums publicados, conserva TLS y compila con `build.sh`. Las dependencias existentes se reutilizan. Los paquetes de Android están sujetos a los términos del SDK de Google.

```sh
bash build.sh
/workspace/android-sdk/build-tools/35.0.0/apksigner verify --verbose build/visor-local-1.0.apk
```

La clave de prueba se genera en `/workspace/tooling/keys/visor-local.jks`, fuera del repositorio. Su contraseña convencional de desarrollo es `android`; no es una clave de distribución de producción. Conserva esa clave para actualizar instalaciones de prueba sin desinstalar. No la subas al repositorio. `ANDROID_HOME`, `ECJ_JAR` y `SIGNING_KEY` permiten cambiar las rutas. Los scripts no modifican automáticamente código, declaraciones ni archivos de bloqueo.

`test.sh` ejecuta comprobaciones reales de HTTP (autenticación, estado, imagen, PCM y cierre del puerto) sobre la implementación del servidor con un proveedor de sensores simulado; no prueba por sí solo el hardware Android. También valida la sintaxis del panel web. Consulta `VALIDATION.md` para los resultados y límites de la ejecución actual.

## Desarrollo y prueba en dispositivo

Cada tarea en la nube ya está aislada: usa este checkout, sin crear worktrees adicionales. No hace falta ejecutar un servicio web en la máquina de desarrollo para compilar la app; el servidor se ejecuta dentro de la APK instalada.

```sh
/workspace/android-sdk/platform-tools/adb install -r build/visor-local-1.0.apk
/workspace/android-sdk/platform-tools/adb shell am start -n local.lens/.MainActivity
```

Con un dispositivo o emulador autorizado, puedes redirigir un puerto para las pruebas: `adb forward tcp:18080 tcp:8080`. Esto sirve para verificación interna; el uso normal es mediante Wi-Fi local y el puerto elegido en la app. Nunca ocultes los avisos de captura al probar.
