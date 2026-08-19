<h1 align="center">
  FOGLTLOGLES
</h1>

**Fast OpenGL to OpenGL ES translation layer**

A proof-of-concept project enabling Minecraft built for OpenGL 3.2 (desktop) to run on OpenGL ES 3.2 platforms—such as Android or embedded systems.

# Goal
- [ ] Support mods
  - [x] Run performance mods (Sodium/Embeddium)
  - [ ] Mods that uses OpenGL 3.3+ (like [ProtoManly's Weather](https://modrinth.com/mod/protomanlys-weather), [Veil (compute shaders)](https://modrinth.com/mod/veil), and etc.)

- [ ] Support shaders
  - [ ] Compute shaders
  - [x] Basic OF/Iris shaders
  - [ ] Modern OF/Iris shaders

- [ ] All Minecraft versions compatibility
  - [x] 1.21.5+
  - [x] 1.17+ & <1.21.4
  - [ ] 1.0+ & <1.17
  - [ ] Beta
  - [ ] Alpha
  - [ ] Infdev

- [ ] OpenGL version translation
  - [ ] Legacy (<2.1)
  - [x] Core (3.2) [Partial]
  - [ ] Modern (3.3+)

- [ ] Optimized for performance

# License
<h2 align="center"> ARR </h2>

# Credits
- [ShaderC](https://github.com/google/shaderc)
- [SPIRV-Cross](https://github.com/KhronosGroup/SPIRV-Cross)
- [fifo_map](https://github.com/nlohmann/fifo_map)
- TinyWrapper
- [LTW](https://github.com/artdeell/LTW)
- [MobileGlues](https://github.com/MobileGL-Dev/MobileGlues)
- [@artdeell](https://github.com/artdeell) for helping me
