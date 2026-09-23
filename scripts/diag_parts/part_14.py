OLD = b'    OSMesaContext context = OSMesaCreateContext_p(GL_RGBA, osmesa_share);'
NEW = b'    setenv("PAN_MESA_DEBUG", "gl3,noafbc,nofp16", 1);\n    OSMesaContext context = OSMesaCreateContext_p(GL_RGBA, osmesa_share);'
