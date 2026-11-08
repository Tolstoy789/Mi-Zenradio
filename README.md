# ZenRadio

Prototipo de app Android nativa para escuchar radio en vivo, con Kotlin, Jetpack Compose y Media3 (ExoPlayer).

## Instalar en el móvil

Cada cambio que llega a `main` se compila solo en GitHub Actions y la APK se publica en *Releases*. La última está siempre en este enlace:

https://github.com/Tolstoy789/Mi-Zenradio/releases/latest/download/ZenRadio.apk

1. Abre el enlace en el móvil y descarga la APK.
2. Ábrela. Android te pedirá permitir "Instalar apps desconocidas" para el navegador o el gestor de archivos: actívalo y vuelve atrás.
3. Pulsa *Instalar*. Las versiones nuevas se instalan encima sin perder tus emisoras ni tus favoritas.

## Cómo abrirlo

1. Instala Android Studio (Ladybug 2024.2 o más reciente).
2. *File → Open* y elige esta carpeta `ZenRadio`.
3. Deja que Gradle sincronice (descarga Gradle 8.11.1, AGP 8.7.3, Compose y Media3).
4. Conecta un móvil o crea un emulador (Android 8.0 / API 26 o superior) y pulsa *Run*.

## Funciones

- **11 emisoras relajantes incluidas**, todas por HTTPS y comprobadas (ver [Comprobar las emisoras](#comprobar-las-emisoras)), con reproducción en segundo plano y notificación de medios:
  - Lo-Fi, Clásica (NPO Radio 4) y Ambiental (Frisky Chill).
  - De SomaFM: Chill (Groove Salad), Beats suaves (Fluid), Voces suaves (Lush), Atmósfera (Synphaera), Espacio profundo (Deep Space One) y Meditación (Drone Zone).
  - Jazz (Jazz24) y Dormir (Ambient Sleeping Pill).
- **Temporizador de apagado** (15 a 90 min) con bajada de volumen suave al final. Botón de ajustes arriba a la derecha.
- **Título de la canción en vivo** cuando la emisora lo envía (metadatos ICY), en la app, la notificación y el widget.
- **Recuerda la última emisora** y, si activas "Reproducir al iniciar", suena al abrir la app.
- **Emisoras propias:** "Añadir emisora" con nombre y URL https. Estrella para favoritas, filtro "Favoritas" y el lápiz junto a "Emisoras" para reordenar o borrar.
- **Ecualizador real:** las barras muestran 5 bandas de frecuencia del audio que suena (FFT sobre el PCM de ExoPlayer, sin permiso de micrófono).
- **Reconexión automática** si se corta la red: reintenta con esperas de 2 a 30 s y al instante cuando vuelve Internet.
- **Diseño "Océano profundo"**: azul marino con acentos turquesa, cristal esmerilado y tipografía Manrope.
  - Fondo vivo: focos de luz que derivan despacio (uno toma el color de la emisora), olas suaves que crecen y respiran con los graves cuando suena, partículas de luz que suben y un grano fino de película.
  - Orbe central que respira en ciclos de 10 s, con un anillo de 64 barras que dibuja el espectro real del audio y un halo del color de la emisora.
  - Mini reproductor que aparece al bajar por la lista (tócalo para volver arriba) y botones de emisora anterior/siguiente.
  - Si en el móvil están desactivadas las animaciones (accesibilidad), el fondo se queda quieto.
- **Temas de color** en Ajustes: Océano (por defecto), Medianoche, Glaciar (modo claro) y el Morado original. Se guarda tu elección.
- **Icono "Luna zen"** en azul océano, con animación de arranque (la luna sube y crecen las barras; Android 12+), icono temático de Android 13 e icono de notificación a juego.
- **Widget** de pantalla de inicio (play/pausa y siguiente emisora) y **Android Auto** (lista de emisoras, favoritas primero).

## Estructura

```
app/src/main/
├── AndroidManifest.xml                 Permisos, servicio, widget, botones multimedia y Android Auto
├── res/layout/widget_player.xml        Diseño del widget
└── java/com/zenradio/app/
    ├── MainActivity.kt                 Entrada de la app y permiso de notificaciones
    ├── data/
    │   ├── Station.kt                  Modelo y catálogo inicial
    │   └── StationRepository.kt        Emisoras del usuario, favoritas, orden y preferencias
    ├── playback/
    │   ├── RadioPlayerService.kt       MediaLibraryService + ExoPlayer: segundo plano, Auto, temporizador
    │   ├── RadioViewModel.kt           Conecta la UI con el servicio (MediaController)
    │   ├── RadioCommands.kt            Comandos compartidos y título en vivo
    │   ├── Reconnector.kt              Reconexión automática
    │   ├── SpectrumAnalyzer.kt         FFT para el ecualizador real
    │   └── ZenRenderersFactory.kt      Inserta el analizador en ExoPlayer
    ├── widget/ZenRadioWidget.kt        Widget de pantalla de inicio
    └── ui/
        ├── ZenRadioScreen.kt           Pantalla principal
        ├── theme/Theme.kt              Temas (Océano, Medianoche, Glaciar, Morado) y tipografía
        └── components/
            ├── AnimatedBackground.kt   Fondo con luces, olas, partículas y grano
            ├── HeroOrb.kt              Orbe que respira, anillo de espectro y botón play
            ├── Controls.kt             Botones de cristal, píldora de estado, chips y logo
            ├── Motion.kt               Reloj de animación y suavizado del audio
            ├── Glass.kt                Superficie de cristal y resplandor de color
            └── …                       Tarjetas, mini reproductor, ecualizador y diálogos
```

## Comprobar las emisoras

El flujo *Emisoras* (`.github/workflows/streams.yml`) comprueba que cada emisora suena de verdad, en dos pasos:

1. **Sonda de red** (`tools/streams/check_streams.py`): conecta a cada emisora en 3 rondas separadas por un minuto, igual que el móvil (solo HTTPS, sin seguir redirecciones a http), y analiza con ffprobe el audio que llega.
2. **Reproducción en Android** (`app/src/androidTest/.../StationPlaybackTest.kt`): pone cada emisora en un emulador con el mismo reproductor que la app y comprueba que empieza a sonar en menos de 20 s y que sigue sonando sin cortes.

Se lanza solo cuando cambia el catálogo y a mano en *Actions → Emisoras → Run workflow*. El resumen de la ejecución trae una tabla por paso con el resultado de cada emisora. En `tools/streams/candidates.txt` hay emisoras de reserva: se prueban a la vez y sirven para reponer una que deje de funcionar.

## Probar Android Auto

En el móvil instala la app "Android Auto", activa el modo desarrollador (toca 10 veces la versión) y habilita "Fuentes desconocidas". También puedes usar el Desktop Head Unit (DHU) del SDK de Android.

## Notas

- Para cambiar las emisoras de fábrica edita `StationCatalog` en `data/Station.kt`. Las que añade el usuario se guardan en el móvil.
- Si añades emisoras de fábrica en una versión nueva, a quien ya tenía la app le aparecen al final de su lista, sin perder su orden ni sus favoritas.
- Las emisoras son de terceros (SomaFM, Jazz24, NPO, Frisky, Ambient Sleeping Pill…). Antes de publicar la app en Google Play, revisa las condiciones de uso de cada una.
- Los colores de cada tema están en `ui/theme/Theme.kt` (`ZenPalettes`). Los del icono, el splash y el widget, en `res/values/colors.xml`.
- Los resplandores de color (botón play, tarjeta activa) usan sombras de color de Android 9+. En Android 8 la app se ve igual pero sin ese brillo.
- La fuente Manrope va incluida en `res/font` (licencia SIL OFL, copia en `assets/licenses`).
- `app/zenradio-dev.keystore` es una clave de pruebas (contraseña `zenradio`) para que todas las APK se instalen una encima de otra. Para publicar en Google Play crea una clave propia y guárdala fuera del repositorio.
- La compilación automática está en `.github/workflows/apk.yml`: compila la APK de *release* (sin depuración, más fluida) en cada cambio de `main` y en cada pull request.
- Si una emisora no responde tras los reintentos, la app muestra un aviso bajo la lista.
