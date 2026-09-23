OLD = b'    if (glFinish_p) glFinish_p();\n\n    osm_blit_to_native(currentBundle);\n'
NEW = b'    if (glFinish_p) glFinish_p();\n\n    if (g_diag_swaps < 2 || (g_diag_swaps++ % 500) == 0)\n        osm_diag_sample("swap");\n\n    osm_fallback_readback();\n\n    osm_blit_to_native(currentBundle);\n'
