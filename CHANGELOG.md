## CHANGELOG - Sodium Legacy

## 0.1.1-release

### Compatibility (hardware & runtime detection)

- **Graphics card detection is now reliable on old Windows drivers.** Some `wmic` outputs (common
  on older Intel driver installations) split the fields of a single card into several blocks,
  which used to be reported as three bogus "unknown" adapters.

- **Driver/compatibility dialogs are actually visible now.** The native Windows message box is
  unavailable on this Minecraft version, so the important compatibility warnings (e.g. the
  "your Intel driver is too old" startup screen) previously only appeared in the log. A Swing
  dialog fallback now shows them on every platform.

- **Calmer, more accurate NVIDIA workaround message.** The NVIDIA Threaded-Optimizations
  workaround relies on native helpers that do not exist on Minecraft 1.8.9.
  It now logs, once and quietly, the manual fix: disable *Threaded Optimizations* for
  `javaw.exe` in the NVIDIA Control Panel if you ever see stutters on an NVIDIA GPU.

### Underwater fog (visual fix)

- **Entities no longer stand out as dark silhouettes underwater.** Vanilla 1.8.9 renders
  underwater with exponential (EXP) fog that applies to everything — terrain and entities
  alike. The chunk shader now reproduces the fixed-function fog exactly — it reads the fog
  mode (LINEAR / EXP / EXP2), its density and its color from GL state, and blends terrain
  geometry with the same exponential falloff theentities receive. Underwater (and with blindness), 
  terrain and entities now fade into the same fog color, matching vanilla's look.

### Performance (more FPS, less micro-stutter)

- **Occlusion culling now respects fog.** The renderer's visibility graph used to search the
  full render distance every frame the camera moves. When fog occlusion is enabled, the search
  is now trimmed against the fog's end distance — sections that lie entirely beyond the fog
  are fully fog-colored and can never contribute visible geometry, so their per-frame graph
  traversal and frustum checks are skipped entirely.

- **Fewer OpenGL state reads per frame.** The chunk render matrices were re-read from the GL
  matrix stacks for every terrain layer (up to 8 reads per frame); they are now read once per
  frame and re-used. The fog state (mode, start, end, density, color) was likewise re-queried
  once per *pass*; it is now captured once per frame and shared by all passes, also removing
  the small scratch arrays allocated per pass.

- **Less garbage per frame while moving.** Removed several per-frame allocations from the
  hot render path (a camera position vector passed into the renderer every frame, a fresh
  block position per graph update, and a fresh visibility-graph visitor per traversal — the
  latter two are now re-used objects). This lowers garbage-collector pressure, which matters
  on the Java version 1.8.9 runs on: fewer pauses and steadier frame rates while walking,
  flying, or looking around.

- **Faster leaf-heavy scenery (forests, trees).** With "Hide Inner Faces" enabled, the hidden
  interior faces between neighboring leaf blocks are now skipped even when the leaves are
  different kinds (oak next to birch, and blocks whose internal decay state differs), regardless
  of whether the face's model declares an explicit cull-face or not.

- **Less wasted work every frame.** Removed an internal pass that, in worlds with many animated
  textures, re-checked every visible section on every frame unnecessarily: the game already
  handles texture animations on its own.

- **Camera and viewport state re-used.** The renderer recalculates the camera position and its
  shader options every frame; it now re-uses its internal objects instead of constantly creating
  and discarding fresh copies. This reduces pressure on Java's garbage collector.

- **Chunk uploads to the GPU are much more efficient.** On older graphics cards (without support
  for persistent buffer mapping), every chunk built or updated forced the GPU to re-allocate 
  its internal memory over and over — an expensive operation that causes stutter, especially
  when flying fast, teleporting, or building quickly. That memory is now reserved once and
  re-used, so terrain updates cost far less.
