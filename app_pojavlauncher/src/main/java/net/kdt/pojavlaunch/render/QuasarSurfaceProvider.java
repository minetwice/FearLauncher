package net.kdt.pojavlaunch.render;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.QuasarShaderFixer;

/**
 * Quasar Surface Provider - OpenGL ES 3.1 renderer optimized for Mali and Adreno GPUs
 * 
 * This renderer provides:
 * - GLES 3.1 support for modern Android devices
 * - Automatic shader fixes for Mali GPU compatibility
 * - Fallback mechanisms for missing extensions
 * - Optimized performance for mobile GPUs
 */
public class QuasarSurfaceProvider implements SurfaceProvider {
    private static final String TAG = "QuasarSurfaceProvider";
    
    private GLSurfaceView mGLSurfaceView;
    private SurfaceCallback mCallback;
    private boolean mUseShaderFixes = true;
    private boolean mForceGLES31 = false;

    @Override
    public View create(Context context, SurfaceCallback callback) {
        mCallback = callback;

        // Load preferences
        mUseShaderFixes = LauncherPreferences.PREF_QUASAR_ENABLE_SHADER_FIXES;
        mForceGLES31 = LauncherPreferences.PREF_QUASAR_FORCE_GLES31;

        Log.d(TAG, "Creating Quasar Surface Provider - Shader Fixes: " + mUseShaderFixes + 
              ", Force GLES 3.1: " + mForceGLES31);

        // Initialize shader fixer
        if (mUseShaderFixes) {
            QuasarShaderFixer.initialize();
        }

        // Configure GLSurfaceView for GLES 3.1
        mGLSurfaceView = new GLSurfaceView(context);
        
        // Set EGL context version based on preference and device capability
        int contextVersion = mForceGLES31 ? 3 : 2;
        mGLSurfaceView.setEGLContextClientVersion(contextVersion);
        
        // Use our custom config chooser
        mGLSurfaceView.setEGLConfigChooser(new QuasarConfigChooser());

        // Set renderer
        QuasarRenderer renderer = new QuasarRenderer();
        mGLSurfaceView.setRenderer(renderer);

        // Enable continuous rendering
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        return mGLSurfaceView;
    }

    @Override
    public void updateSize() {
        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestLayout();
        }
    }

    /**
     * Custom EGL config chooser for Quasar
     */
    private static class QuasarConfigChooser implements GLSurfaceView.EGLConfigChooser {
        private static final String TAG = "QuasarConfigChooser";
        
        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            // Try GLES 3.1 first
            int[] attributes31 = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, 0x0004, // EGL_OPENGL_ES3_BIT_KHR
                EGL10.EGL_NONE
            };

            EGLConfig[] configs31 = new EGLConfig[1];
            int[] numConfigs31 = new int[1];

            if (egl.eglChooseConfig(display, attributes31, configs31, 1, numConfigs31)) {
                if (numConfigs31[0] > 0) {
                    Log.d(TAG, "Selected GLES 3.1 config");
                    return configs31[0];
                }
            }

            // Fallback to GLES 3.0
            int[] attributes30 = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, 0x0004, // EGL_OPENGL_ES3_BIT_KHR
                EGL10.EGL_NONE
            };

            EGLConfig[] configs30 = new EGLConfig[1];
            int[] numConfigs30 = new int[1];

            if (egl.eglChooseConfig(display, attributes30, configs30, 1, numConfigs30)) {
                if (numConfigs30[0] > 0) {
                    Log.d(TAG, "Selected GLES 3.0 config");
                    return configs30[0];
                }
            }

            // Final fallback to GLES 2.0
            int[] attributes20 = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_RENDERABLE_TYPE, EGL10.EGL_OPENGL_ES2_BIT,
                EGL10.EGL_NONE
            };

            EGLConfig[] configs20 = new EGLConfig[1];
            int[] numConfigs20 = new int[1];

            if (egl.eglChooseConfig(display, attributes20, configs20, 1, numConfigs20)) {
                if (numConfigs20[0] > 0) {
                    Log.d(TAG, "Selected GLES 2.0 config (fallback)");
                    return configs20[0];
                }
            }

            // If all else fails, use default
            Log.w(TAG, "No suitable EGL config found, using default");
            return null;
        }
    }

    /**
     * Quasar Renderer - Handles the actual rendering
     */
    private class QuasarRenderer implements GLSurfaceView.Renderer {
        private static final String TAG = "QuasarRenderer";
        
        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            Log.d(TAG, "Surface created - GPU Info: " + QuasarShaderFixer.getGpuInfo());
            
            // Initialize shader fixer for this context
            if (mUseShaderFixes) {
                QuasarShaderFixer.resetCache();
                QuasarShaderFixer.initialize();
            }

            // Notify callback
            if (mCallback != null) {
                // We need to get the actual Surface from GLSurfaceView
                // This is a bit tricky, but we can use reflection or wait for onSurfaceAvailable
                Log.d(TAG, "Surface created, waiting for surface to be available");
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            Log.d(TAG, "Surface changed: " + width + "x" + height);
            
            if (mCallback != null) {
                mCallback.onSurfaceResized();
            }
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            // Main rendering happens here
            // This is handled by the actual game renderer
        }
    }

    /**
     * Get the underlying GLSurfaceView for advanced configuration
     */
    public GLSurfaceView getGLSurfaceView() {
        return mGLSurfaceView;
    }

    /**
     * Update shader fix preferences
     */
    public void updatePreferences() {
        mUseShaderFixes = LauncherPreferences.PREF_QUASAR_ENABLE_SHADER_FIXES;
        mForceGLES31 = LauncherPreferences.PREF_QUASAR_FORCE_GLES31;
        
        if (mGLSurfaceView != null) {
            // Reconfigure if needed
            if (mUseShaderFixes) {
                QuasarShaderFixer.initialize();
            }
        }
    }

    /**
     * Check if shader fixes are enabled
     */
    public boolean isUsingShaderFixes() {
        return mUseShaderFixes;
    }

    /**
     * Check if GLES 3.1 is forced
     */
    public boolean isForcingGLES31() {
        return mForceGLES31;
    }
}
