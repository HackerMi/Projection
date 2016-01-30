LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= android_media_RemoteDisplay.cpp

LOCAL_C_INCLUDES:= \
        $(TOP)/frameworks/base/core/jni

LOCAL_SHARED_LIBRARIES:= \
        libbinder                       \
        libcutils                       \
        liblog                          \
        libmedia                        \
        libutils                        \
        libnativehelper                 \
        libandroid_runtime              \

LOCAL_MODULE:= libwfd_jni

LOCAL_MODULE_TAGS:= optional

include $(BUILD_SHARED_LIBRARY)
