LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
OPENCV_INSTALL_MODULES:=on
include /home/james/NVPACK/OpenCV-2.4.8.2-Tegra-sdk/sdk/native/jni/OpenCV.mk

LOCAL_MODULE := ProcessImage_jni
LOCAL_SRC_FILES := ProcessImage_jni.cpp

include $(BUILD_SHARED_LIBRARY)