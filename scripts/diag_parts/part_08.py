OLD = b'void osm_swap_buffers() {\n    if (currentBundle == NULL) return;\n'
NEW = b'void osm_swap_buffers() {\n    if (currentBundle == NULL) {\n        if ((g_diag_swaps++ % 100) == 0)\n            fprintf(stderr, "OSMDIAG: swap with NO current bundle\\n");\n        return;\n    }\n'
