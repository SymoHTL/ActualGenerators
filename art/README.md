# Art pipeline

Textures are made in two stages, so code and datagen never wait on art:

1. **Mockup (Claude).** A scripted 16×16 PNG at the correct path with the right
   dimensions, palette and light direction. Good enough to see the block in
   game and to keep datagen honest — never good enough to ship.
2. **Final (Symo).** Redrawn by hand from the mockup, using the shared palette.

Mockups live at their real asset path and get overwritten by the final art, so
replacing one is a drop-in — no code or datagen changes.

## Tools

- **[Aseprite](https://www.aseprite.org/)** — the editor finals are drawn in.
  Tiled view catches seams on block textures, palettes load from
  `palette.gpl`, and the frame timeline exports the vertical sprite sheets
  Minecraft's animated `.mcmeta` textures expect.

  Built from source locally at **`D:\Tools\aseprite\build\bin\aseprite.exe`**
  (Aseprite's EULA §2(g) allows compiling for personal use; §2(b) forbids
  distributing the binary, so this build stays on this machine). Rebuild
  against upstream with `D:\Tools\update-aseprite.bat`. This palette is
  preinstalled in that build's palette list as *actual-generators*.

  Free substitutes if the build ever breaks:
  **[Pixelorama](https://orama-interactive.itch.io/pixelorama)** (open source,
  closest in features) or **[LibreSprite](https://libresprite.github.io/)**
  (fork of the last free Aseprite, familiar UI).
- **[Blockbench](https://blockbench.net/)** (free) — companion, not
  replacement. Live 3D preview of a texture on the actual block while
  painting, 3×3 walls for tiling checks, and the editor for any custom model
  geometry. Its
  [Minecraft Style Guide](https://blockbench.net/wiki/guides/minecraft-style-guide/)
  is the reference for matching vanilla's look.

## Rules

- **`palette.gpl` is the palette.** Ten ramps of five shades. Stay inside them
  so every machine reads as one family; add a ramp rather than a stray colour.
- **Light comes from the upper left.** Lightest shade top-left, darkest
  bottom-right, outline in the ramp's darkest shade.
- **16×16, no anti-aliasing, no gradients.** Shade in discrete steps from the
  ramp. Vanilla-level noise — texture, not detail.
- **Prefer animated textures to renderers.** Working machines animate via
  `.mcmeta` sprite sheets; block entity renderers are a last resort
  (see the performance rules in `CLAUDE.md`).
- **No AI image generation.** Diffusion output is mushy, off-grid and
  anti-aliased — wrong for 16×16.
