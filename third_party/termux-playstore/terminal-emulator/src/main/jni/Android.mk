LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_CFLAGS += -std=c23 -Wall -Wextra -Werror -Os -fno-stack-protector -Wl,--gc-sections
LOCAL_MODULE := libtermux
ifeq ($(SSHPEACHES_NO_BUILD_ID),1)
LOCAL_LDFLAGS += -Wl,--build-id=none
endif
LOCAL_SRC_FILES := termux.c
include $(BUILD_SHARED_LIBRARY)
