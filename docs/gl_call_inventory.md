# Minecraft 1.21 / LWJGL3 OpenGL Call Inventory

This document serves as the contract and inventory for OpenGL calls made by Minecraft 1.21, LWJGL3, and Iris/Quasar shader pipelines.

## 1. Fixed Function Legacy Emulation
- `glMatrixMode`: CPU matrix stack mode selection.
- `glPushMatrix` / `glPopMatrix`: CPU matrix stack operations.
- `glLoadIdentity` / `glLoadMatrixf` / `glMultMatrixf`: Matrix transformations.
- `glTranslatef` / `glRotatef` / `glScalef`: Matrix operations.
- `glBegin` / `glEnd`: Immediate mode geometry rendering.
- `glVertex2f` / `glVertex3f` / `glVertex3fv` / `glVertex4f`: Immediate mode vertex positioning.
- `glTexCoord2f` / `glTexCoord2fv`: Immediate mode texture coordinates.
- `glColor3f` / `glColor4f` / `glColor4ub` / `glColor4fv`: Immediate mode color attributes.
- `glNormal3f` / `glNormal3fv`: Immediate mode normal vectors.
- `glShadeModel`: Flat vs Smooth shading mode setting (No-op/Emulated).
- `glAlphaFunc`: Legacy alpha testing threshold setting (Emulated in fragment shaders).

## 2. Modern Pipeline & Drawing
- `glGenBuffers` / `glBindBuffer` / `glBufferData` / `glBufferSubData` / `glDeleteBuffers`: VBO management (GLES Native).
- `glGenVertexArrays` / `glBindVertexArray` / `glDeleteVertexArrays`: VAO management via OES/GLES 3.0 (GLES Native).
- `glVertexAttribPointer` / `glEnableVertexAttribArray` / `glDisableVertexAttribArray`: Attribute pointers (GLES Native).
- `glVertexAttribIPointer`: Integer attribute pointers (GLES 3.0 Native).
- `glDrawArrays` / `glDrawElements` / `glDrawElementsBaseVertex` / `glDrawArraysInstanced` / `glDrawElementsInstanced`: Drawing routines (GLES Native / Emulated fallback).

## 3. Shader Pipeline & Iris/Quasar Integration
- `glCreateShader` / `glShaderSource` / `glCompileShader` / `glGetShaderiv` / `glGetShaderInfoLog` / `glDeleteShader`: Shader compilation (GLES Native / Transpiled).
- `glCreateProgram` / `glAttachShader` / `glDetachShader` / `glLinkProgram` / `glGetProgramiv` / `glGetProgramInfoLog` / `glUseProgram` / `glDeleteProgram`: Program management (GLES Native).
- `glGetUniformLocation` / `glUniform1i` / `glUniform1f` / `glUniform2f` / `glUniform3f` / `glUniform4f` / `glUniformMatrix4fv` / `glUniform1fv` / `glUniform2fv` / `glUniform3fv` / `glUniform4fv`: Shader uniform updates (GLES Native).
- `glGetProgramBinary` / `glProgramBinary`: Disk shader binary cache (GLES Native extension).

## 4. Texture Management
- `glGenTextures` / `glBindTexture` / `glActiveTexture` / `glDeleteTextures`: Texture binding & management (GLES Native).
- `glTexImage2D` / `glTexSubImage2D` / `glTexImage3D` / `glTexSubImage3D`: Texture data uploads (GLES Native with Format Engine translation).
- `glTexParameteri` / `glTexParameterf` / `glTexParameteriv` / `glTexParameterfv`: Texture sampler configuration (GLES Native).
- `glGenerateMipmap`: Mipmap generation (GLES Native).

## 5. Framebuffer Objects (FBO) & Multi-Render Targets (MRT)
- `glGenFramebuffers` / `glBindFramebuffer` / `glDeleteFramebuffers` / `glCheckFramebufferStatus`: Framebuffer setup (GLES Native).
- `glGenRenderbuffers` / `glBindRenderbuffer` / `glRenderbufferStorage` / `glDeleteRenderbuffers`: Renderbuffer setup (GLES Native).
- `glFramebufferTexture2D` / `glFramebufferTextureLayer` / `glFramebufferRenderbuffer`: FBO attachment points (GLES Native with retry/repair engine).
- `glDrawBuffers` / `glReadBuffer`: MRT output configuration (GLES Native).
- `glBlitFramebuffer`: FBO blitting (GLES 3.0 Native).

## 6. Context State, Query & Clear Operations
- `glEnable` / `glDisable` / `glIsEnabled`: Render state toggles (GLES Native).
- `glBlendFunc` / `glBlendFuncSeparate` / `glBlendEquation` / `glBlendEquationSeparate`: Alpha blending setup (GLES Native).
- `glDepthFunc` / `glDepthMask` / `glDepthRange` / `glDepthRangef`: Depth testing setup (GLES Native).
- `glColorMask` / `glStencilMask` / `glStencilFunc` / `glStencilOp`: Color & stencil masks (GLES Native).
- `glViewport` / `glScissor` / `glCullFace` / `glFrontFace` / `glPolygonOffset`: Rasterization state setup (GLES Native).
- `glClear` / `glClearColor` / `glClearDepth` / `glClearDepthf` / `glClearStencil`: Buffer clearing (GLES Native).
- `glGetIntegerv` / `glGetFloatv` / `glGetBooleanv` / `glGetString` / `glGetStringi` / `glGetError`: Capability & state queries (Guarded with safe defaults).
