# PAPERCUT — doodle wave shooter (Android nativo, Kotlin)

Shooter de oleadas en primera persona, estilo cuaderno de dibujo a boli. Port nativo en
**Kotlin puro** (sin motor de juegos, sin dependencias externas) del juego web
[PAPERCUT](../doodle-wave-shooter): misma arena, mismas 5 armas, mismas oleadas y el
mismo jefe (**THE DOODLER**) cada 4 rondas.

## Cómo está hecho

- **Render propio por software** (`SoftView`): el mundo 3D se dibuja como líneas proyectadas
  con `Canvas` (estilo wireframe/doodle a tinta). No usa OpenGL — por diseño, así el juego
  también funciona en emuladores cuyo stack GL/ANGLE está roto (ver nota abajo).
- **Engine** (`Game.kt`): física con colisiones AABB, escalar escaleras/plataformas,
  hitscan (rayo-esfera / rayo-AABB), oleadas, IA de enemigos (grunt/shooter/boss),
  gancho con yank, bloqueo y deflexión con la katana, dash-slash, barriles explosivos.
- **Matemáticas** (`Math3D.kt`): Vec3 y matrices 4x4 (perspectiva + vista FPS) escritas a mano.
- **Estilo doodle** (`Sketch.kt`): segmentos con temblor de boli, círculos a doble trazo,
  stickmen animados (caminar, windup, disparo, muerte).
- **HUD + controles** (`HudView.kt`): todo dibujado con Canvas; joystick virtual (pulgar
  izquierdo), mirar arrastrando (pulgar derecho) y botones FUEGO / SALTO / MIRA / GANCHO /
  REC / DASH / cambio de arma / PAUSA.
- **Audio sintetizado** (`Sfx.kt`): efectos generados con `AudioTrack` (sin assets).

## Compilar y probar

Requisitos: JDK 17+ y Android SDK (platform 36, build-tools 34+).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

En Android Studio: abrir la carpeta y ejecutar la configuración `app`.

### Emulador

Cualquier AVD sirve (probado con API 33/34/36). Si tu emulador tiene el driver GL roto
(WebGL sin render en Chrome es el síntoma), este juego igual funciona porque no usa OpenGL.

> Nota: con emulator 37.x sobre macOS el driver GLES del huésped (ANGLE/SwiftShader) tiene
> un bug que tira SIGSEGV en `glCreateProgram` en *cualquier* app GL — por eso este port
> renderiza con Canvas. En dispositivos reales también funciona igual de bien.

## Controles

| Entrada | Acción |
| --- | --- |
| Pulgar izquierdo | joystick para moverse (a fondo = sprint) |
| Pulgar derecho | deslizar para mirar |
| FUEGO | disparar / tajo de katana |
| MIRA / BLOQ | apuntar · zoom (sniper) · bloquear con katana |
| SALTO / GANCHO / REC | saltar · gancho (yank a enemigos o cornisas) · recargar |
| RIF·SHG·REV·SNP·KAT | cambio de arma (DASH aparece con la katana) |
| Botón atrás del sistema | pausa |

## Estilo de juego

- Oleadas de garabatos (scribbles) que te persiguen; cada 4 oleadas llega **THE DOODLER**.
- Multiplicadores de estilo: HEADSHOT x2 · SLICED x1.5 · YANKED x1.5 · BLOCKED x2 · AIRBORNE x2.5.
- Barriles naranjas explotan; los pickups curan o recargan.
