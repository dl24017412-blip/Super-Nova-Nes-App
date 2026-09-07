#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <string>

#include "snes_types.h"
#include "snes_core.h"
#include "gl_renderer.h"
#include "audio_engine.h"

static GLRenderer* gRenderer = nullptr;
static AudioEngine* gAudioEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeInit(JNIEnv* env, jobject /* this */, jstring storagePath) {
    const char* pathStr = env->GetStringUTFChars(storagePath, nullptr);
    std::string path(pathStr);
    env->ReleaseStringUTFChars(storagePath, pathStr);

    if (!gRenderer) {
        gRenderer = new GLRenderer();
    }
    if (!gAudioEngine) {
        gAudioEngine = new AudioEngine();
        gAudioEngine->init();
    }

    SnesCore* core = SnesCore::getInstance();
    core->setRenderer(gRenderer);
    core->setAudioEngine(gAudioEngine);
    return core->initialize(path) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_jni_NativeBridge_nativeLoadRom(JNIEnv* env, jobject /* this */, jbyteArray romBytes, jstring filename) {
    if (!romBytes) return -1;
    jsize len = env->GetArrayLength(romBytes);
    jbyte* bytes = env->GetByteArrayElements(romBytes, nullptr);

    const char* fileStr = env->GetStringUTFChars(filename, nullptr);
    std::string fname(fileStr);
    env->ReleaseStringUTFChars(filename, fileStr);

    int result = SnesCore::getInstance()->loadRom(reinterpret_cast<const uint8_t*>(bytes), len, fname);
    env->ReleaseByteArrayElements(romBytes, bytes, JNI_ABORT);
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeReset(JNIEnv* /* env */, jobject /* this */) {
    SnesCore::getInstance()->reset();
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeStart(JNIEnv* /* env */, jobject /* this */) {
    SnesCore::getInstance()->start();
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativePause(JNIEnv* /* env */, jobject /* this */) {
    SnesCore::getInstance()->pause();
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeResume(JNIEnv* /* env */, jobject /* this */) {
    SnesCore::getInstance()->resume();
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeStop(JNIEnv* /* env */, jobject /* this */) {
    SnesCore::getInstance()->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeSetSurface(JNIEnv* env, jobject /* this */, jobject surface) {
    if (!gRenderer) {
        gRenderer = new GLRenderer();
        SnesCore::getInstance()->setRenderer(gRenderer);
    }

    if (!surface) {
        gRenderer->destroySurface();
        return JNI_TRUE;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get ANativeWindow from Surface");
        return JNI_FALSE;
    }

    bool ok = gRenderer->setSurface(window);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeSurfaceChanged(JNIEnv* /* env */, jobject /* this */, jint width, jint height) {
    if (gRenderer) {
        gRenderer->updateSurfaceSize(width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeSurfaceDestroyed(JNIEnv* /* env */, jobject /* this */) {
    if (gRenderer) {
        gRenderer->destroySurface();
    }
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeSetInputState(JNIEnv* /* env */, jobject /* this */, jint controller, jint buttons) {
    SnesCore::getInstance()->setInputState(controller, static_cast<uint16_t>(buttons));
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeSaveState(JNIEnv* env, jobject /* this */, jint slot, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    std::string p(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    return SnesCore::getInstance()->saveState(slot, p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeLoadState(JNIEnv* env, jobject /* this */, jint slot, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    std::string p(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    return SnesCore::getInstance()->loadState(slot, p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeSaveSram(JNIEnv* env, jobject /* this */, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    std::string p(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    return SnesCore::getInstance()->saveSram(p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeLoadSram(JNIEnv* env, jobject /* this */, jstring path) {
    const char* pathStr = env->GetStringUTFChars(path, nullptr);
    std::string p(pathStr);
    env->ReleaseStringUTFChars(path, pathStr);

    return SnesCore::getInstance()->loadSram(p) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeSetVideoOptions(JNIEnv* /* env */, jobject /* this */, jint aspect, jint filter, jint profile) {
    SnesCore::getInstance()->setVideoOptions(
        static_cast<AspectRatioMode>(aspect),
        static_cast<VideoFilter>(filter),
        static_cast<PerformanceProfile>(profile)
    );
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeSetAudioOptions(JNIEnv* /* env */, jobject /* this */, jboolean enabled, jfloat volume) {
    SnesCore::getInstance()->setAudioOptions(enabled == JNI_TRUE, volume);
}

JNIEXPORT void JNICALL
Java_com_example_jni_NativeBridge_nativeGetStats(JNIEnv* env, jobject /* this */, jfloatArray statsArray) {
    if (!statsArray) return;
    float fps = 0.0f, frameTime = 0.0f, cpuTime = 0.0f;
    SnesCore::getInstance()->getStats(fps, frameTime, cpuTime);

    jfloat elements[3] = { fps, frameTime, cpuTime };
    env->SetFloatArrayRegion(statsArray, 0, 3, elements);
}

JNIEXPORT jstring JNICALL
Java_com_example_jni_NativeBridge_nativeGetRomTitle(JNIEnv* env, jobject /* this */) {
    const RomHeaderInfo& header = SnesCore::getInstance()->getRomHeader();
    return env->NewStringUTF(header.title);
}

JNIEXPORT jlong JNICALL
Java_com_example_jni_NativeBridge_nativeGetRomSize(JNIEnv* /* env */, jobject /* this */) {
    const RomHeaderInfo& header = SnesCore::getInstance()->getRomHeader();
    return static_cast<jlong>(header.realRomSize);
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeIsHiRom(JNIEnv* /* env */, jobject /* this */) {
    const RomHeaderInfo& header = SnesCore::getInstance()->getRomHeader();
    return header.isHiRom ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_jni_NativeBridge_nativeHasBattery(JNIEnv* /* env */, jobject /* this */) {
    const RomHeaderInfo& header = SnesCore::getInstance()->getRomHeader();
    return header.hasBattery ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
