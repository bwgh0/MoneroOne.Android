#include <jni.h>
#include <stdint.h>
#include <stddef.h>

extern char *oah_mobile_call(const uint8_t *input, size_t length);
extern void oah_mobile_free(char *output);

JNIEXPORT jstring JNICALL
Java_one_monero_moneroone_core_username_OahCore_callNative(JNIEnv *env, jclass type, jbyteArray input) {
    (void)type;
    if (!input) return NULL;
    jsize length = (*env)->GetArrayLength(env, input);
    if (length > 16777216) return NULL;
    jbyte *bytes = (*env)->GetByteArrayElements(env, input, NULL);
    if (!bytes) return NULL;
    char *output = oah_mobile_call((const uint8_t *)bytes, (size_t)length);
    (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);
    if (!output) return NULL;
    jstring result = (*env)->NewStringUTF(env, output);
    oah_mobile_free(output);
    return result;
}
