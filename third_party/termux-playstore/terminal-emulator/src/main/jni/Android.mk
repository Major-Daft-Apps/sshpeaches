LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
LOCAL_CFLAGS += -std=c23 -Wall -Wextra -Werror -Os -fno-stack-protector -Wl,--gc-sections
LOCAL_MODULE := libtermux
# 16 KB-aligned segments (NDK r27 still defaults to 4 KB), or it won't load on phones with 16 KB memory pages.
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
ifeq ($(SSHPEACHES_NO_BUILD_ID),1)
LOCAL_LDFLAGS += -Wl,--build-id=none
endif
LOCAL_SRC_FILES := termux.c
include $(BUILD_SHARED_LIBRARY)
