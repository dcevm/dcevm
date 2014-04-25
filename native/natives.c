/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include <stdio.h>
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include "natives.h"

JNIEXPORT jint JNICALL Java_at_ssw_hotswap_test_natives_SimpleNativeTest_00024A_value(JNIEnv *env, jclass c) {
    return 1;
}

JNIEXPORT jint JNICALL Java_at_ssw_hotswap_test_natives_SimpleNativeTest_00024A_value2(JNIEnv *env, jclass c) {
    return 2;
}

JNIEXPORT jclass JNICALL Java_at_ssw_hotswap_test_access_jni_JNIVMAccess_findClassNative(JNIEnv *env, jclass c, jstring s) {
    const char* name = (*env)->GetStringUTFChars(env, s, 0);
    jclass clazz = (*env)->FindClass(env, name);
    (*env)->ReleaseStringUTFChars(env, s, name);
    return clazz;
}

JNIEXPORT jobject JNICALL Java_at_ssw_hotswap_test_access_jni_JNIClassAccess_findMethodNative(JNIEnv *env, jclass c, jclass cls, jstring methodName) {
    const char *methodstr = (*env)->GetStringUTFChars(env, methodName, 0);

    jclass jCls = (*env)->GetObjectClass(env, cls);

    // Get Method ID of getMethods()
    jmethodID midGetFields = (*env)->GetMethodID(env, jCls, "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");
    (*env)->ExceptionDescribe(env);
    jobjectArray jobjArray = (jobjectArray) (*env)->CallObjectMethod(env, cls, midGetFields);

    jsize len = (*env)->GetArrayLength(env, jobjArray);
    jsize i = 0;

    for (i = 0; i < len; i++) {
        jobject _strMethod = (*env)->GetObjectArrayElement(env, jobjArray, i);
        jclass _methodClazz = (*env)->GetObjectClass(env, _strMethod);
        jmethodID mid = (*env)->GetMethodID(env, _methodClazz, "getName", "()Ljava/lang/String;");
        jstring _name = (jstring) (*env)->CallObjectMethod(env, _strMethod, mid);

        const char *str = (*env)->GetStringUTFChars(env, _name, 0);

        if (strcmp(str, methodstr) == 0) {
            (*env)->ReleaseStringUTFChars(env, methodName, methodstr);
            (*env)->ReleaseStringUTFChars(env, _name, str);
            return _strMethod;
        }
        (*env)->ReleaseStringUTFChars(env, _name, str);
    }

    jclass exc = (*env)->FindClass(env, "java/lang/NoSuchMethodError");
    (*env)->ThrowNew(env, exc, methodstr);
    (*env)->ReleaseStringUTFChars(env, methodName, methodstr);

}

JNIEXPORT jobjectArray JNICALL Java_at_ssw_hotswap_test_access_jni_JNIClassAccess_getMethodsNative(JNIEnv *env, jclass c, jclass cls) {
    jobjectArray array;

    jclass jCls = (*env)->GetObjectClass(env, cls);

    // Get Method ID of getMethods()
    jmethodID midGetFields = (*env)->GetMethodID(env, jCls, "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");
    (*env)->ExceptionDescribe(env);
    jobjectArray jobjArray = (jobjectArray) (*env)->CallObjectMethod(env, cls, midGetFields);

    jsize len = (*env)->GetArrayLength(env, jobjArray);
    jsize i = 0;

    array = (*env)->NewObjectArray(env, len, (*env)->FindClass(env, "java/lang/reflect/Method"), 0);

    for (i = 0; i < len; i++) {
        jobject _strMethod = (*env)->GetObjectArrayElement(env, jobjArray, i);
        (*env)->SetObjectArrayElement(env, array, i, _strMethod);
    }

    return array;
}

jobject callVoidMethod(JNIEnv *env, jobject obj, jboolean staticValue, jmethodID methodID, jvalue *params) {
    if (staticValue) {
        (*env)->CallStaticVoidMethodA(env, obj, methodID, params);
    } else {
        (*env)->CallVoidMethodA(env, obj, methodID, params);
    }
    return (*env)->NewGlobalRef(env, NULL);
}

jobject callIntMethod(JNIEnv *env, jobject obj, jboolean staticValue, jmethodID methodID, jvalue *params) {
    jint intValue;
    if (staticValue) {
        intValue = (*env)->CallStaticIntMethodA(env, obj, methodID, params);
    } else {
        intValue = (*env)->CallIntMethodA(env, obj, methodID, params);
    }
    jclass clazz = (*env)->FindClass(env, "Ljava/lang/Integer;");
    jmethodID methodIDInteger = (*env)->GetMethodID(env, clazz, "<init>", "(I)V");
    return (*env)->NewObject(env, clazz, methodIDInteger, intValue);
}

jobject callObjectMethod(JNIEnv *env, jobject obj, jboolean staticValue, jmethodID methodID, jvalue *params) {
    if (staticValue) {
        return (*env)->CallStaticObjectMethodA(env, obj, methodID, params);
    } else {
        return (*env)->CallObjectMethodA(env, obj, methodID, params);
    }
}

JNIEXPORT jobject JNICALL Java_at_ssw_hotswap_test_access_jni_JNIMethodAccess_invokeMethodNative(JNIEnv *env, jclass c, jclass cls, jobject obj, jstring methodName, jstring retValue, jboolean staticValue, jstring descriptor, jobjectArray params) {
    const char *methodstr = (*env)->GetStringUTFChars(env, methodName, 0);
    const char *descriptorstr = (*env)->GetStringUTFChars(env, descriptor, 0);
    const char *retValuestr = (*env)->GetStringUTFChars(env, retValue, 0);

    jmethodID methodID;
    if (staticValue) {
        methodID = (*env)->GetStaticMethodID(env, cls, methodstr, descriptorstr);
    } else {
        methodID = (*env)->GetMethodID(env, cls, methodstr, descriptorstr);
    }

    jsize len = (*env)->GetArrayLength(env, params);
    jvalue *m = (jvalue*) malloc(sizeof (jvalue) * len);

    jvalue *mm = m;
    int i = 0;
    for (i; i < len; i++) {
        *mm = (jvalue)(*env)->GetObjectArrayElement(env, params, i);
        mm += 1;
    }

    jobject object = (*env)->NewGlobalRef(env, NULL);

    if (strcmp(retValuestr, "void") == 0) {
        object = callVoidMethod(env, obj, staticValue, methodID, m);
    } else if (strcmp(retValuestr, "int") == 0) {
        object = callIntMethod(env, obj, staticValue, methodID, m);
    } else if (strcmp(retValuestr, "java.lang.Object") == 0) {
        object = callObjectMethod(env, obj, staticValue, methodID, m);
    } else {
        jclass exc = (*env)->FindClass(env, "java.lang.NotImplementedException");
        (*env)->ThrowNew(env, exc, "required retValue: bool/int/object");
    }

    (*env)->ReleaseStringUTFChars(env, methodName, methodstr);
    (*env)->ReleaseStringUTFChars(env, descriptor, descriptorstr);
    (*env)->ReleaseStringUTFChars(env, retValue, retValuestr);

    return object;
}

