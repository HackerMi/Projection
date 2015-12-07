LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_FULL_MANIFEST_FILE := $(LOCAL_PATH)/src/main/AndroidManifest.xml
LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, src/main/res)
LOCAL_ASSET_DIR := $(addprefix $(LOCAL_PATH)/, src/main/assets)

#LOCAL_JAVA_LIBRARIES := android-support-v4
LOCAL_STATIC_JAVA_LIBRARIES := lib_upnp_common lib_upnp_on_lan lib_upnp_stack_service
                               
LOCAL_PACKAGE_NAME := Projection
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_AAPT_INCLUDES := $(call intermediates-dir-for,APPS,miui,,COMMON)/package-export.apk
include $(BUILD_PACKAGE)