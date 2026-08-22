#ifndef FEAR_PLUGIN_LOADER_H
#define FEAR_PLUGIN_LOADER_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Fear Render Plugin API - Custom Render Injection System
 *
 * This system allows external renderer plugins (shared libraries) to be loaded
 * at runtime and injected into the GL/EGL dispatch chain, similar to Zalith Launcher's
 * custom renderer injection.
 *
 * Plugin lifecycle:
 *   1. fear_plugin_load(path)    - Load the .so and call its fear_plugin_init()
 *   2. fear_plugin_get_proc()    - Query the plugin for GL/EGL function overrides
 *   3. fear_plugin_unload()      - Call fear_plugin_shutdown() and dlclose()
 *
 * Plugin contract (the .so must export these symbols):
 *   int     fear_plugin_init(void* userdata);
 *   void    fear_plugin_shutdown(void);
 *   void*   fear_plugin_get_proc(const char* name);   // Returns function pointer or NULL
 *   const char* fear_plugin_get_name(void);
 *   const char* fear_plugin_get_version(void);
 */

// Load a custom renderer plugin from the given absolute path.
// Returns 0 on success, negative on failure.
int fear_plugin_load(const char* path);

// Unload the currently loaded plugin.
void fear_plugin_unload(void);

// Query whether a plugin is currently loaded.
int fear_plugin_is_loaded(void);

// Get the human-readable name of the loaded plugin (or NULL).
const char* fear_plugin_get_name(void);

// Get the version string of the loaded plugin (or NULL).
const char* fear_plugin_get_version(void);

// Resolve a GL/EGL function name through the loaded plugin.
// Returns the plugin's function pointer, or NULL if the plugin
// does not provide an override for this function.
void* fear_plugin_get_proc(const char* name);

// Get the number of functions the plugin has overridden.
int fear_plugin_get_override_count(void);

#ifdef __cplusplus
}
#endif

#endif // FEAR_PLUGIN_LOADER_H
