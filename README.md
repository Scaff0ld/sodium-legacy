<img src="fabric/src/main/resources/assets/sodiumlegacy/icon.png" width="128" alt="Sodium Legacy icon">

# Sodium Legacy

Sodium Legacy is a free and open-source backport of the [Sodium 0.5.8](https://modrinth.com/mod/sodium) terrain
renderer to legacy versions of the Minecraft client. It replaces the vanilla chunk renderer with Sodium's
section-based pipeline while preserving the look and behavior of the original game, achieving significantly
better frame rates and reduced micro-stutter on Minecraft 1.8.9.

## ℹ️ Overview

Like upstream Sodium, the terrain rendering engine replaces the vanilla chunk renderer with Sodium's
section-based pipeline. On top of that, Sodium Legacy does two things:

**A faithful port to Minecraft 1.8.9**

- **Native 1.8.9 terrain pipeline** — Sodium's modern chunk meshing/occlusion architecture, adapted to the
  fixed-function OpenGL 2.1 core and LWJGL 2 that Minecraft 1.8.9 provides (a small LWJGL 3 API shim bridges
  the API differences for code shared with upstream).
- **OpenGL 2.1 features backported** — modern GL code (e.g. persistent mapped staging buffers) is layered on
  ARB extensions available on 1.8.9-era drivers, with fallbacks for hardware without them.
- **Vanilla-parity rendering** — F1/F5, entity outlines, block entities, the debug HUD and fluids render
  exactly like vanilla; the block/entity rendering code paths themselves remain untouched.

**Features that go beyond what Sodium offers**

- **Zoom (OptiFine-style)** — a configurable zoom key (default: `C`, in "Miscellaneous" under Controls) with
  adjustable zoom amount, Hold/Toggle activation modes and smooth animated zooming.
- **Dynamic Lights (OptiFine-style)** — light-emitting blocks (torches, glowstone…) illuminate their
  surroundings while held or dropped, with Fast (0.5 s) and Fancy (per-tick) update modes.
- **Connected Textures (OptiFine/MCPatcher compatible)** — glass and stained glass connect seamlessly,
  tile textures are generated automatically, and resource packs that ship OptiFine/MCPatcher CTM rules
  are read and applied. Plus "Hide Inner Faces", which culls the interior faces between adjacent
  translucent blocks.
- **Per-particle toggles** — each particle type vanilla 1.8.9 spawns can be switched on or off
  individually (rain splash, flames, portal, explosions…) instead of an all-or-nothing setting.
- **mouse-delay fix** — fixes a vanilla 1.8.9 bug where clicks while moving the camera at high
  mouse speeds land on the wrong target, caused by the head yaw lagging one tick behind.
- **On-screen console + friendly diagnostics** — in-game HUD messages for broken NVIDIA driver versions,
  incompatible resource packs and corrupted configs, plus a full Spanish translation of the options GUI
  (`en_us` / `es_es`).
- **Gameplay-friendly chunk updates** — deferred chunk updates are throttled per frame so bursts of builds
  (teleporting, flying) don't cause single-frame spikes.

## ✅ Requirements

- **Minecraft 1.8.9** with either **Legacy Fabric Loader** or **Forge 11.15.1.2318+**.
- A GPU with OpenGL 2.1+ and working drivers. Persistent buffer mapping (ARB 4.4) is used when available
  and emulated otherwise.

### OpenGL Compatibility Layers

Devices which need to use OpenGL translation layers (such as GL4ES, ANGLE, etc) are not supported and will
very likely not work with Sodium Legacy.

## 🛠️ Developer Guide

### Project layout

| Module    | Description |
|-----------|-------------|
| `core`    | Loader-independent code: the LWJGL 3 API shim (built on LWJGL 2) and everything shared between loaders. It never depends on Minecraft and only uses libraries already shipped by 1.8.9. |
| `fabric`  | Legacy Fabric front-end (Legacy Yarn names): mixins, render code, GUI and resources. |
| `forge`   | Forge 1.8.9 front-end (MCP names). Kept as a **standalone build** (`forge/`) because Essential Loom does not run on the Gradle version used by Fabric Loom; the root project only delegates tasks. |

### Building from sources

- **Fabric** (root, Gradle 9.x):

  ```bash
  ./gradlew :fabric:build
  # output: fabric/build/libs/sodiumlegacy-fabric-<version>-beta.jar
  ```

- **Forge** (delegated from the root, ideal):

  ```bash
  ./gradlew :forge:buildForge
  # output: forge/build/libs/sodiumlegacy-forge-<version>-beta.jar
  ```

  You can also run the build directly inside `forge/`:

  ```bash
  cd forge
  ./gradlew build
  ```

#### Requirements

- **JDK 21+** is required to *configure* the :fabric module (Fabric Loom 1.16 needs a JVM 21+ daemon),
  or **JDK 17** to *run* the Forge build's Gradle 8.1.1 daemon (`forge/`). Either set `JAVA_HOME`
  accordingly before invoking Gradle, or pass:
    - `-PforgeJavaHome=C:\path\to\jdk-17` for any `:forge:*` task,
    - and run `./gradlew` with JDK 21 on `PATH`/`JAVA_HOME` for `:fabric:*` tasks.
- Gradle is not installed manually — use the provided wrappers (`gradlew` for the root/Fabric build,
  `forge/gradlew` for the Forge build).
- `org.gradle.configureondemand=true` is enabled, so `:forge:*` / `:core:*` tasks never configure
  `:fabric` (which is what would otherwise pull in the JVM 21 requirement).

## 🐛 Reporting Issues

Please report bugs and crashes on this project's issue tracker. Before opening a new issue, use the search
tool to make sure it hasn't already been reported, and attach the relevant log
(`.minecraft/logs/latest.log`) or crash report.

## 📜 License

Sodium Legacy is a derivative work of Sodium, which is licensed under the GNU LGPL-3.0. Substantial amounts
of code in this repository originate from Sodium and remain under those terms; new port-specific code is
provided under the same license.

When submitting pull requests to this repository, it is assumed that you are licensing your contribution
under the GNU LGPLv3, unless you state otherwise.
