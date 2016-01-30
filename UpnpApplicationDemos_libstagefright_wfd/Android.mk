LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

ifeq (true,$(call is-greater-than,$(PLATFORM_SDK_VERSION),22))
V_SRC_PATH := src/main/v23
else
V_SRC_PATH := src/main/v21
endif

LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java) \
		   $(call all-java-files-under, $(V_SRC_PATH))

LOCAL_FULL_MANIFEST_FILE := $(LOCAL_PATH)/src/main/AndroidManifest.xml
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, src/main/res)
LOCAL_ASSET_DIR := $(addprefix $(LOCAL_PATH)/, src/main/assets)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 lib_upnp_session lib_upnp_manager
                               
LOCAL_PACKAGE_NAME := UpnpApplicationDemos
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_AAPT_INCLUDES := $(call intermediates-dir-for,APPS,miui,,COMMON)/package-export.apk
include $(BUILD_PACKAGE)
