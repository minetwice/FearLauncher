OLD = b'    __android_log_print(ANDROID_LOG_WARN, g_LogTag, "No native surface \xe2\x80\x94 color buffer only");'
NEW = b'    __android_log_print(ANDROID_LOG_WARN, g_LogTag, "No native surface \xe2\x80\x94 color buffer only");\n    fprintf(stderr, "OSMDIAG: no native surface (pojavWindow=%p) \xe2\x80\x94 rendering disabled\\n",\n            (void*X©ridge_environ.pojavWindow);'
