#ifndef GL_RENDERER_H
#define GL_RENDERER_H

#include "snes_types.h"
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <mutex>

class GLRenderer {
public:
    GLRenderer();
    ~GLRenderer();

    bool setSurface(ANativeWindow* window);
    void updateSurfaceSize(int width, int height);
    void destroySurface();

    void setOptions(AspectRatioMode aspect, VideoFilter filter, PerformanceProfile profile);
    void renderFrame(const uint32_t* framebuffer);

    bool isReady() const { return ready; }

private:
    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;

    int surfaceWidth = 0;
    int surfaceHeight = 0;
    bool ready = false;
    std::mutex renderMutex;

    GLuint program = 0;
    GLuint textureId = 0;
    GLint posAttr = -1;
    GLint texAttr = -1;
    GLint samplerUniform = -1;
    GLint scanlineUniform = -1;
    GLint resolutionUniform = -1;

    AspectRatioMode aspectMode = AspectRatioMode::ORIGINAL_4_3;
    VideoFilter filterMode = VideoFilter::BILINEAR;
    PerformanceProfile perfProfile = PerformanceProfile::BALANCED;

    bool initEGL();
    void destroyEGL();
    bool initGL();
    void destroyGL();
    void calculateViewport(int& vx, int& vy, int& vw, int& vh);
};

#endif // GL_RENDERER_H
