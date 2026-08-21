# Minecraft Java 1.21.x GL Call Inventory (Fear Render)

## Fixed Function & Matrix Emulation (Legacy Paths)
- `glMatrixMode`, `glPushMatrix`, `glPopMatrix`, `glLoadMatrixd`, `glLoadMatrixf`, `glMultMatrixd`, `glMultMatrixf`
- `glTranslatef`, `glRotatef`, `glScalef`
- `glBegin`, `glEnd`, `glVertex2f`, `glVertex3f`, `glColor3f`, `glColor4f`, `glTexCoord2f`

## Modern Core Rendering Paths
- `glGenBuffers`, `glBindBuffer`, `glBufferData`, `glBufferSubData`, `glDeleteBuffers`
- `glVertexAttribPointer`, `glEnableVertexAttribArray`, `glDisableVertexAttribArray`
- `glGenVertexArrays`, `glBindVertexArray`, `glDeleteVertexArrays`
- `glDrawArrays`, `glDrawElements`, `glDrawElementsInstanced`

## Shaders & Programs (Iris / Vanilla)
- `glCreateShader`, `glShaderSource`, `glCompileShader`, `glGetShaderiv`, `glGetShaderInfoLog`
- `glCreateProgram`, `glAttachShader`, `glLinkProgram`, `glGetProgramiv`, `glGetProgramInfoLog`, `glUseProgram`
- `glGetUniformLocation`, `glUniform1i`, `glUniform1f`, `glUniform2f`, `glUniform3f`, `glUniform4f`, `glUniformMatrix4fv`

## Textures & FBO Engine
- `glGenTextures`, `glBindTexture`, `glTexImage2D`, `glTexSubImage2D`, `glTexParameteri`, `glActiveTexture`, `glDeleteTextures`
- `glGenFramebuffers`, `glBindFramebuffer`, `glFramebufferTexture2D`, `glCheckFramebufferStatus`, `glDeleteFramebuffers`, `glDrawBuffers`

## State & Clear
- `glEnable`, `glDisable`, `glBlendFunc`, `glDepthFunc`, `glDepthMask`, `glColorMask`, `glViewport`, `glScissor`, `glCullFace`, `glClear`, `glClearColor`
