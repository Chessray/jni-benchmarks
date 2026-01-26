/**
 * Copyright © 2026, Evolved Binary Ltd
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <jni.h>

#include <algorithm>
#include <cstring>
#include <stdexcept>

#include "Iterator.h"

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_evolvedbinary_jnibench_common_iterators_NativeIterator
 * Method:    createIterator
 * Signature: (IJ)J
 */
JNIEXPORT jlong JNICALL Java_com_evolvedbinary_jnibench_common_iterators_NativeIterator_createIterator
  (JNIEnv *env, jobject obj, jint num_elements, jlong element_size) {
    Iterator* iterator = new Iterator(num_elements, static_cast<size_t>(element_size));
    return reinterpret_cast<jlong>(iterator);
}

/*
 * Class:     com_evolvedbinary_jnibench_common_iterators_NativeIterator
 * Method:    hasNext
 * Signature: (J)B
 */
JNIEXPORT jboolean JNICALL Java_com_evolvedbinary_jnibench_common_iterators_NativeIterator_hasNext
  (JNIEnv *env, jobject obj, jlong handle) {
    Iterator* iterator = reinterpret_cast<Iterator*>(handle);
    return iterator->hasNext() ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_evolvedbinary_jnibench_common_iterators_NativeIterator
 * Method:    next
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL Java_com_evolvedbinary_jnibench_common_iterators_NativeIterator_next
  (JNIEnv *env, jobject obj, jlong handle) {
    Iterator* iterator = reinterpret_cast<Iterator*>(handle);
    try {
        const std::vector<char>& data = iterator->next();
        jbyteArray result = env->NewByteArray(static_cast<jsize>(data.size()));
        if (result == nullptr) {
            return nullptr;
        }
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte*>(data.data()));
        return result;
    } catch (const std::out_of_range& e) {
        jclass exClass = env->FindClass("java/util/NoSuchElementException");
        if (exClass != nullptr) {
            env->ThrowNew(exClass, e.what());
        }
        return nullptr;
    }
}

/*
 * Class:     com_evolvedbinary_jnibench_common_iterators_NativeIterator
 * Method:    disposeInternal
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_evolvedbinary_jnibench_common_iterators_NativeIterator_disposeInternal
  (JNIEnv *env, jobject obj, jlong handle) {
    Iterator* iterator = reinterpret_cast<Iterator*>(handle);
    delete iterator;
}

extern "C" void* iterator_create(int num_elements, size_t element_size) {
    return new Iterator(num_elements, element_size);
}

extern "C" int iterator_has_next(void* handle) {
    auto* iterator = reinterpret_cast<Iterator*>(handle);
    return iterator->hasNext() ? 1 : 0;
}

extern "C" int iterator_next(void* handle, char* dest, int dest_len) {
    auto* iterator = reinterpret_cast<Iterator*>(handle);
    try {
        const auto& data = iterator->next();
        const int size = std::min(static_cast<int>(data.size()), dest_len);
        std::memcpy(dest, data.data(), static_cast<size_t>(size));
        return size;
    } catch (const std::out_of_range&) {
        return -1;
    }
}

extern "C" void iterator_dispose(void* handle) {
    auto* iterator = reinterpret_cast<Iterator*>(handle);
    delete iterator;
}

#ifdef __cplusplus
}
#endif
