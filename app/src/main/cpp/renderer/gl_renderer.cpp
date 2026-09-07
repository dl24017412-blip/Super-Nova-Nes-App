#include "gl_renderer.h"
#include <android/native_window.h>
#include <cmath>
#include <algorithm>

static const char* VERTEX_SHADER = R"(
attribute vec2 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
)";

static const char* FRAGMENT_SHADER = R"(
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform float uScanline;
void main() {
    vec4 color = texture2D(uTexture, vTexCoord);
    if (uScanline > 0.5) {
        float s = sin(vTexCoord.y * 224.0 * 3.14159265);
        color.rgb -= abs(s) * 0.12;
    }
    gl_FragColor = color;
}
)";

static const GLfloat QUAD_VERTICES[] = {
    -1.0f,  1.0f,  0.0f, 0.0f, // Top-left
    -1.0f, -1.0f,  0.0f, 1.0f, // Bottom-left
     1.0f,  1.0f,  1.0f, 0.0f, // Top-right
     1.0f, -1.0f,  1.0f, 1.0f  // Bottom-right
};

GLRenderer::GLRenderer() = default;

GLRenderer::~GLRenderer() {
    destroySurface();
}

static GLuint compileShader(GLenum type, const char* src) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &src, nullptr);
    glCompileShader(shader);
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        LOGE("Shader compilation failed!");
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

bool GLRenderer::initEGL() {
    display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) return false;

    if (!eglInitialize(display, nullptr, nullptr)) return false;

    const EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_NONE
    };

    EGLConfig config;
    EGLint numConfigs;
    if (!eglChooseConfig(display, attribs, &config, 1, &numConfigs) || numConfigs <= 0) {
        return false;
    }

    surface = eglCreateWindowSurface(display, config, window, nullptr);
    if (surface == EGL_NO_SURFACE) return false;

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 2,
        EGL_NONE
    };

    context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) return false;

    if (!eglMakeCurrent(display, surface, surface, context)) return false;

    eglQuerySurface(display, surface, EGL_WIDTH, &surfaceWidth);
    eglQuerySurface(display, surface, EGL_HEIGHT, &surfaceHeight);

    return true;
}

bool GLRenderer::initGL() {
    GLuint vs = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
    if (!vs || !fs) return false;

    program = glCreateProgram();
    glAttachShader(program, vs);
    glAttachShader(program, fs);
    glLinkProgram(program);

    glDeleteShader(vs);
    glDeleteShader(fs);

    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        LOGE("Program link failed!");
        return false;
    }

    posAttr = glGetAttribLocation(program, "aPosition");
    texAttr = glGetAttribLocation(program, "aTexCoord");
    samplerUniform = glGetUniformLocation(program, "uTexture");
    scanlineUniform = glGetUniformLocation(program, "uScanline");

    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);

    GLint filter = (filterMode == VideoFilter::NEAREST) ? GL_NEAREST : GL_LINEAR;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // Initial empty texture
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, SNES_SCREEN_WIDTH, SNES_SCREEN_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    return true;
}

void GLRenderer::destroyGL() {
    if (textureId) {
        glDeleteTextures(1, &textureId);
        textureId = 0;
    }
    if (program) {
        glDeleteProgram(program);
        program = 0;
    }
}

void GLRenderer::destroyEGL() {
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface != EGL_NO_SURFACE) {
            eglDestroySurface(display, surface);
            surface = EGL_NO_SURFACE;
        }
        if (context != EGL_NO_CONTEXT) {
            eglDestroyContext(display, context);
            context = EGL_NO_CONTEXT;
        }
        eglTerminate(display);
        display = EGL_NO_DISPLAY;
    }
}

bool GLRenderer::setSurface(ANativeWindow* win) {
    std::lock_guard<std::mutex> lock(renderMutex);
    destroySurface();

    if (!win) return false;
    window = win;

    if (!initEGL() || !initGL()) {
        destroySurface();
        return false;
    }

    ready = true;
    LOGI("GLRenderer initialized: %dx%d", surfaceWidth, surfaceHeight);

    // Detach from UI thread so emuThread can make it current
    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    return true;
}

void GLRenderer::updateSurfaceSize(int width, int height) {
    std::lock_guard<std::mutex> lock(renderMutex);
    surfaceWidth = width;
    surfaceHeight = height;
}

void GLRenderer::destroySurface() {
    ready = false;
    if (display != EGL_NO_DISPLAY) {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    }
    destroyGL();
    destroyEGL();
    if (window) {
        ANativeWindow_release(window);
        window = nullptr;
    }
}

void GLRenderer::setOptions(AspectRatioMode aspect, VideoFilter filter, PerformanceProfile profile) {
    std::lock_guard<std::mutex> lock(renderMutex);
    aspectMode = aspect;
    filterMode = filter;
    perfProfile = profile;

    if (textureId && ready) {
        glBindTexture(GL_TEXTURE_2D, textureId);
        GLint glFilter = (filterMode == VideoFilter::NEAREST || perfProfile == PerformanceProfile::LOW) ? GL_NEAREST : GL_LINEAR;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, glFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, glFilter);
    }
}

void GLRenderer::calculateViewport(int& vx, int& vy, int& vw, int& vh) {
    if (surfaceWidth <= 0 || surfaceHeight <= 0) {
        vx = vy = 0;
        vw = surfaceWidth;
        vh = surfaceHeight;
        return;
    }

    if (aspectMode == AspectRatioMode::FILL_SCREEN) {
        vx = 0; vy = 0;
        vw = surfaceWidth; vh = surfaceHeight;
        return;
    }

    if (aspectMode == AspectRatioMode::INTEGER_SCALE) {
        int scaleX = surfaceWidth / SNES_SCREEN_WIDTH;
        int scaleY = surfaceHeight / SNES_SCREEN_HEIGHT;
        int scale = std::max(1, std::min(scaleX, scaleY));
        vw = SNES_SCREEN_WIDTH * scale;
        vh = SNES_SCREEN_HEIGHT * scale;
        vx = (surfaceWidth - vw) / 2;
        vy = (surfaceHeight - vh) / 2;
        return;
    }

    // Default ORIGINAL_4_3
    float targetAspect = 4.0f / 3.0f;
    float currentAspect = (float)surfaceWidth / (float)surfaceHeight;

    if (currentAspect > targetAspect) {
        vh = surfaceHeight;
        vw = (int)(surfaceHeight * targetAspect);
        vx = (surfaceWidth - vw) / 2;
        vy = 0;
    } else {
        vw = surfaceWidth;
        vh = (int)(surfaceWidth / targetAspect);
        vx = 0;
        vy = (surfaceHeight - vh) / 2;
    }
}

void GLRenderer::renderFrame(const uint32_t* framebuffer) {
    std::lock_guard<std::mutex> lock(renderMutex);
    if (!ready || !framebuffer || display == EGL_NO_DISPLAY || surface == EGL_NO_SURFACE) return;

    if (eglGetCurrentContext() != context) {
        if (!eglMakeCurrent(display, surface, surface, context)) {
            LOGE("Failed to make EGL context current on emuThread: %d", eglGetError());
            return;
        }
    }

    int vx, vy, vw, vh;
    calculateViewport(vx, vy, vw, vh);

    glViewport(0, 0, surfaceWidth, surfaceHeight);
    glClear(GL_COLOR_BUFFER_BIT);

    glViewport(vx, vy, vw, vh);

    glUseProgram(program);

    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, SNES_SCREEN_WIDTH, SNES_SCREEN_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, framebuffer);

    glUniform1i(samplerUniform, 0);
    glUniform1f(scanlineUniform, (filterMode == VideoFilter::SCANLINE) ? 1.0f : 0.0f);

    glEnableVertexAttribArray(posAttr);
    glVertexAttribPointer(posAttr, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), QUAD_VERTICES);

    glEnableVertexAttribArray(texAttr);
    glVertexAttribPointer(texAttr, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(GLfloat), &QUAD_VERTICES[2]);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

    glDisableVertexAttribArray(posAttr);
    glDisableVertexAttribArray(texAttr);

    eglSwapBuffers(display, surface);
}
