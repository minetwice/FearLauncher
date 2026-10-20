/*
 * FearLauncher: a do-nothing stand-in for the flite narrator library.
 *
 * Minecraft's text-to-speech narrator calls System.loadLibrary("flite") while the
 * client starts up, and a few mods that reuse the narrator do the same. When the
 * library is missing the JVM throws UnsatisfiedLinkError, which surfaces as
 *
 *     com.mojang.text2speech.Narrator$InitializeException: Failed to load library flite
 *         Caused by: java.lang.UnsatisfiedLinkError: dlopen failed: library "libflite.so" not found
 *
 * That is noisy at best, and fatal in a caller that does not catch it. The narrator
 * is switched off anyway (-Dminecraft.narrator=false), so an empty library under
 * that exact name is enough: the load succeeds, every symbol lookup simply fails,
 * and the callers already handle that.
 *
 * The one exported symbol exists only so the linker keeps the object rather than
 * producing an empty shared library; nothing calls it.
 */

int fear_narrator_stub_anchor(void) {
    return 0;
}
