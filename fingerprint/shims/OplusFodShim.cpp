/*
 * Copyright (C) 2025 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

#define LOG_TAG "OplusFodShim"

#include <log/log.h>
#include <android/binder_ibinder.h>
#include <android/binder_parcel.h>
#include <aidl/android/hardware/biometrics/fingerprint/ISession.h>
#include <aidl/android/hardware/biometrics/fingerprint/ISessionCallback.h>
#include <android-base/properties.h>
#include <fcntl.h>
#include <unistd.h>
#include <poll.h>
#include <pthread.h>
#include <dlfcn.h>
#include <string.h>
#include <atomic>
#include <errno.h>

using aidl::android::hardware::biometrics::fingerprint::AcquiredInfo;
using aidl::android::hardware::biometrics::fingerprint::ISession;
using aidl::android::hardware::biometrics::fingerprint::ISessionCallback;
using android::base::GetProperty;

namespace {

static const char* kFodNode = "/sys/kernel/oplus_display/notify_fppress";
static const char* kFpStateNode = "/sys/kernel/oplus_display/fp_state";
static const char kSessionDesc[] = "android.hardware.biometrics.fingerprint.ISession";

/*
 * OPlus fingerprint HAL vendor-specific onAcquired codes.
 * The HAL sends these via ISessionCallback::onAcquired to signal finger
 * down/up, even under AOD where the kernel's fp_state doesn't fire.
 *
 * They are only meaningful as the vendorCode argument, i.e. when the
 * AcquiredInfo argument is VENDOR.
 */
static constexpr int32_t kVendorFingerDown = 22;
static constexpr int32_t kVendorFingerUp = 23;
static constexpr int32_t kAcquiredVendor = static_cast<int32_t>(AcquiredInfo::VENDOR);

/*
 * Optional safety net: force-release the press if it has been held for this
 * long without anything clearing it. A normal auth holds it ~150ms and an
 * enroll capture well under a second, so a multi-second value cannot cut a
 * legitimate capture short. Set to 0 to disable (default: disabled, so the
 * runtime behaviour is identical to before).
 */
static constexpr int64_t kPressWatchdogMs = 0;

/* Poll cadence and failure backoff for the fp_state monitor. */
static constexpr int kPollTimeoutMs = 200;
static constexpr int kReadFailBackoffMs = 50;
static constexpr int kReadFailReopenAfter = 20;

static std::atomic_bool gMonitorRunning{false};
static pthread_t gMonitorThread;

/*
 * gStateMutex protects gPressed / gWaitRelease / gPressedAtMs.
 * gIoMutex serializes the actual sysfs writes and the notify_fppress fd.
 *
 * Splitting them keeps the (blocking, ~10ms) sysfs write off the state lock
 * while still guaranteeing that concurrent writers converge on the latest
 * state instead of racing each other into an inconsistent node value.
 */
static pthread_mutex_t gStateMutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t gIoMutex = PTHREAD_MUTEX_INITIALIZER;

static bool gPressed = false;
static bool gWaitRelease = false;  // suppress press until fp_state returns to 0
static int64_t gPressedAtMs = 0;

static int gFodFd = -1;
static int gLastWritten = -1;  // -1 = unknown, 0/1 = last value pushed to sysfs

static AIBinder_Class_onTransact gOrigSessionOnTransact = nullptr;

/*
 * Thread-local capture of int32 values written to the current outgoing
 * binder parcel.  We hook AIBinder_prepareTransaction (start) and
 * AParcel_writeInt32 (capture) so that by the time AIBinder_transact fires,
 * we can inspect the payload — specifically the vendor code inside onAcquired.
 */
struct ParcelCapture {
    AParcel* parcel;
    int32_t vals[16];
    int count;
};
static thread_local ParcelCapture gCapture = {nullptr, {0}, 0};

static void resetCapture() {
    gCapture.parcel = nullptr;
    gCapture.count = 0;
}

static int64_t nowMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/*
 * Push the current press state to the kernel.
 *
 * Everything happens under gIoMutex, and the value to write is re-read from
 * the shared state inside that critical section. Concurrent callers therefore
 * serialize and the node always ends up holding the most recent state, even
 * if two threads flip gPressed in quick succession. Redundant writes are
 * skipped via gLastWritten.
 */
static void syncFodNode() {
    pthread_mutex_lock(&gIoMutex);

    pthread_mutex_lock(&gStateMutex);
    const bool want = gPressed;
    pthread_mutex_unlock(&gStateMutex);

    const int wantVal = want ? 1 : 0;
    if (gLastWritten == wantVal) {
        pthread_mutex_unlock(&gIoMutex);
        return;
    }
    if (gFodFd < 0) {
        pthread_mutex_unlock(&gIoMutex);
        ALOGE("notify_fppress fd not open");
        return;
    }

    const char* val = want ? "1" : "0";
    const size_t len = 1;

    ssize_t ret;
    int savedErrno = 0;
    for (int attempt = 0; attempt < 3; ++attempt) {
        ret = pwrite(gFodFd, val, len, 0);
        savedErrno = errno;
        if (ret == (ssize_t)len) break;
        if (ret < 0 && (savedErrno == EINTR || savedErrno == EAGAIN)) continue;
        break;
    }

    if (ret != (ssize_t)len) {
        /*
         * Leave gLastWritten untouched so the next transition retries rather
         * than assuming the kernel already has this value.
         */
        pthread_mutex_unlock(&gIoMutex);
        ALOGE("notify_fppress <= %s failed: ret=%zd errno=%d (%s)",
              val, ret, savedErrno, strerror(savedErrno));
        return;
    }

    gLastWritten = wantVal;
    pthread_mutex_unlock(&gIoMutex);

    ALOGI("notify_fppress <= %s", val);
}

static void setPressed(bool pressed) {
    pthread_mutex_lock(&gStateMutex);
    const bool oldPressed = gPressed;
    const bool changed = oldPressed != pressed;
    gPressed = pressed;
    if (changed && pressed) {
        gPressedAtMs = nowMs();
    }
    pthread_mutex_unlock(&gStateMutex);

    if (changed) {
        ALOGI("setPressed: %d -> %d", oldPressed, pressed);
    }

    /*
     * Always sync, even when this caller saw no change: another thread may
     * have flipped the state between its own update and its write, and this
     * call converges the node onto the current value.
     */
    syncFodNode();
}

/*
 * Turn off HBM and arm the monitor to ignore fp_state until the finger is
 * actually released.
 *
 * The sensor is stopped before the finger lifts, so fp_state can still read 1
 * at this point. Simply zeroing lastState would make that stale 1 look like a
 * fresh finger-down and re-enable HBM with no session running. Instead we wait
 * for an observed transition to 0 before arming the next press.
 */
static void notifySessionEnd() {
    ALOGI("notifySessionEnd: resetting session state");
    setPressed(false);
    pthread_mutex_lock(&gStateMutex);
    gWaitRelease = true;
    pthread_mutex_unlock(&gStateMutex);
}

static bool readFpState(int fd, int& x, int& y, int& state) {
    char buffer[128];
    if (lseek(fd, 0, SEEK_SET) < 0) {
        ALOGE("readFpState: lseek failed: %s", strerror(errno));
        return false;
    }
    ssize_t len = read(fd, buffer, sizeof(buffer) - 1);
    if (len <= 0) {
        ALOGE("readFpState: read failed, len=%zd: %s", len, strerror(errno));
        return false;
    }
    buffer[len] = '\0';
    if (sscanf(buffer, "%d,%d,%d", &x, &y, &state) != 3) {
        ALOGE("readFpState: parse failed, buffer=%s", buffer);
        return false;
    }
    return true;
}

/*
 * Monitor thread: watches fp_state via poll() for instant wakeup.
 * Handles the lockscreen / light-AOD case where the touch driver DOES
 * report finger events.  Deep AOD is handled by the vendor code path.
 *
 * Note on sysfs semantics: sysfs_kf_poll() returns EPOLLERR|EPOLLPRI when the
 * attribute changes, so POLLERR here is a normal notification, not a failure.
 * Only POLLNVAL indicates a genuinely dead descriptor.
 */
static void* monitorThread(void* /*arg*/) {
    int fd = open(kFpStateNode, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        ALOGE("monitorThread: failed to open %s: %s", kFpStateNode, strerror(errno));
        return nullptr;
    }

    ALOGI("monitorThread: started fd=%d", fd);

    int lastState = 0;
    int x = 0;
    int y = 0;
    int state = 0;
    if (readFpState(fd, x, y, state)) {
        lastState = state;
    }

    int readFailures = 0;

    while (gMonitorRunning.load(std::memory_order_relaxed)) {
        struct pollfd pfd = { .fd = fd, .events = POLLPRI | POLLERR, .revents = 0 };

        int ret = poll(&pfd, 1, kPollTimeoutMs);
        if (ret < 0) {
            if (errno == EINTR) continue;
            ALOGE("monitorThread: poll failed: %s", strerror(errno));
            usleep(kReadFailBackoffMs * 1000);
            continue;
        }

        if (pfd.revents & POLLNVAL) {
            ALOGE("monitorThread: fd became invalid, reopening");
            close(fd);
            usleep(kReadFailBackoffMs * 1000);
            fd = open(kFpStateNode, O_RDONLY | O_CLOEXEC);
            if (fd < 0) {
                ALOGE("monitorThread: reopen failed: %s", strerror(errno));
                usleep(500 * 1000);
            }
            continue;
        }

        if (!readFpState(fd, x, y, state)) {
            /*
             * A failed read leaves the POLLPRI edge pending, so returning
             * straight to poll() would spin. Back off, and reopen if this
             * keeps happening.
             */
            if (++readFailures >= kReadFailReopenAfter) {
                ALOGE("monitorThread: %d consecutive read failures, reopening",
                      readFailures);
                close(fd);
                fd = open(kFpStateNode, O_RDONLY | O_CLOEXEC);
                readFailures = 0;
                if (fd < 0) {
                    ALOGE("monitorThread: reopen failed: %s", strerror(errno));
                    usleep(500 * 1000);
                    continue;
                }
            }
            usleep(kReadFailBackoffMs * 1000);
            continue;
        }
        readFailures = 0;

        bool waitRelease;
        pthread_mutex_lock(&gStateMutex);
        waitRelease = gWaitRelease;
        pthread_mutex_unlock(&gStateMutex);

        if (waitRelease) {
            if (state == 0) {
                pthread_mutex_lock(&gStateMutex);
                gWaitRelease = false;
                pthread_mutex_unlock(&gStateMutex);
                ALOGI("monitor: session-end reset (lastState %d -> 0)", lastState);
                lastState = 0;
            } else {
                /* Finger still down from the finished session: keep ignoring. */
                lastState = state;
            }
            continue;
        }

        if (state != lastState) {
            ALOGI("fp_state: %d,%d,%d (was %d)", x, y, state, lastState);
            setPressed(state > 0);
            lastState = state;
            continue;
        }

        if (kPressWatchdogMs > 0) {
            bool stuck = false;
            pthread_mutex_lock(&gStateMutex);
            if (gPressed && gPressedAtMs != 0 &&
                nowMs() - gPressedAtMs > kPressWatchdogMs) {
                stuck = true;
            }
            pthread_mutex_unlock(&gStateMutex);
            if (stuck) {
                ALOGE("monitor: press held > %lldms, forcing release",
                      (long long)kPressWatchdogMs);
                setPressed(false);
                lastState = state;
            }
        }
    }

    ALOGI("monitorThread: stopped");
    if (fd >= 0) close(fd);
    return nullptr;
}

/*
 * One-shot attempt to bring up the fp_state monitor. Returns true on
 * success. On failure, *definitiveDisabled is set when there is no point
 * retrying (the sensor-type property is set to something else, so this
 * device simply isn't an optical UDFPS).
 *
 * The first boot after a flash is racy: persist.* properties and the
 * /sys/kernel/oplus_display nodes (created by the display kernel driver)
 * may not be ready when the HAL process starts. The constructor used to
 * check once and permanently disable itself; the startup thread retries
 * instead (see startupThread below).
 */
static bool tryStartMonitor(bool* definitiveDisabled) {
    *definitiveDisabled = false;

    std::string sensorType = GetProperty("persist.vendor.fingerprint.sensor_type", "");
    if (sensorType != "optical") {
        *definitiveDisabled = !sensorType.empty();
        ALOGI("startup: sensor_type=%s%s", sensorType.c_str(),
              sensorType.empty() ? " (empty, keep retrying)" : ", monitor disabled");
        return false;
    }
    if (access(kFpStateNode, R_OK) != 0) {
        ALOGE("startup: %s not readable yet, retrying", kFpStateNode);
        return false;
    }

    pthread_mutex_lock(&gIoMutex);
    if (gFodFd >= 0) {
        close(gFodFd);
        gFodFd = -1;
    }
    gFodFd = open(kFodNode, O_WRONLY | O_CLOEXEC);
    gLastWritten = -1;  // unknown kernel state after (re)open
    const int fodFd = gFodFd;
    pthread_mutex_unlock(&gIoMutex);

    if (fodFd < 0) {
        ALOGE("startup: open %s failed: %s, retrying", kFodNode, strerror(errno));
        return false;
    }
    ALOGI("startup: fp_state readable, notify_fppress fd=%d", fodFd);

    gMonitorRunning.store(true, std::memory_order_relaxed);

    if (pthread_create(&gMonitorThread, nullptr, monitorThread, nullptr) == 0) {
        pthread_setname_np(gMonitorThread, "FodMonitor");
        ALOGI("startup: monitor thread created");
        return true;
    }
    ALOGE("startup: failed to create monitor thread");
    gMonitorRunning.store(false, std::memory_order_relaxed);
    pthread_mutex_lock(&gIoMutex);
    if (gFodFd >= 0) {
        close(gFodFd);
        gFodFd = -1;
    }
    gLastWritten = -1;
    pthread_mutex_unlock(&gIoMutex);
    return false;
}

/*
 * Startup thread: retries the bring-up until the persist property and the
 * kernel display nodes are ready (200ms cadence, 60s budget), then starts
 * the monitor. This covers the first-boot-after-flash race where
 * persist.vendor.fingerprint.sensor_type and the oplus_display sysfs nodes
 * appear after the HAL process has already started.
 */
static void* startupThread(void* /*arg*/) {
    constexpr int kMaxTries = 300;  // 200ms * 300 = 60s budget
    for (int i = 0; i < kMaxTries; ++i) {
        bool definitive = false;
        if (tryStartMonitor(&definitive)) return nullptr;
        if (definitive) {
            ALOGI("startup: not an optical sensor, giving up");
            return nullptr;
        }
        usleep(200 * 1000);
    }
    ALOGE("startup: giving up after %d retries", kMaxTries);
    return nullptr;
}

/* ISession incoming-call wrapper (if AIBinder_Class_define hook fires) */
static binder_status_t sessionOnTransactWrapper(AIBinder* binder, transaction_code_t code,
                                                const AParcel* in, AParcel* out) {
    if (code == ISession::TRANSACTION_onPointerDownWithContext ||
        code == ISession::TRANSACTION_onPointerDown) {
        ALOGI("ISession::onPointerDown -> HBM on");
        setPressed(true);
    } else if (code == ISession::TRANSACTION_onPointerUpWithContext ||
               code == ISession::TRANSACTION_onPointerUp) {
        ALOGI("ISession::onPointerUp -> HBM off");
        setPressed(false);
    }
    if (!gOrigSessionOnTransact) {
        ALOGE("ISession onTransact wrapper has no original handler");
        return STATUS_UNKNOWN_ERROR;
    }
    return gOrigSessionOnTransact(binder, code, in, out);
}

} // namespace

/*
 * Hook: capture the parcel pointer at transaction start.
 */
extern "C"
binder_status_t AIBinder_prepareTransaction(AIBinder* binder, AParcel** in) {
    using Fn = binder_status_t (*)(AIBinder*, AParcel**);
    static auto orig = (Fn)dlsym(RTLD_NEXT, "AIBinder_prepareTransaction");
    if (!orig) {
        ALOGE("AIBinder_prepareTransaction: dlsym failed");
        return STATUS_UNKNOWN_ERROR;
    }

    binder_status_t st = orig(binder, in);

    /*
     * Only track the parcel when preparation actually succeeded; on failure
     * *in is not a valid parcel we may compare against later.
     */
    if (st == STATUS_OK && in && *in) {
        gCapture.parcel = *in;
        gCapture.count = 0;
    } else {
        resetCapture();
    }
    return st;
}

/*
 * Hook: record every int32 written to the current outgoing parcel.
 */
extern "C"
binder_status_t AParcel_writeInt32(AParcel* parcel, int32_t value) {
    using Fn = binder_status_t (*)(AParcel*, int32_t);
    static auto orig = (Fn)dlsym(RTLD_NEXT, "AParcel_writeInt32");
    if (!orig) {
        ALOGE("AParcel_writeInt32: dlsym failed");
        return STATUS_UNKNOWN_ERROR;
    }

    if (parcel && parcel == gCapture.parcel &&
        gCapture.count < (int)(sizeof(gCapture.vals) / sizeof(gCapture.vals[0]))) {
        gCapture.vals[gCapture.count++] = value;
    }
    return orig(parcel, value);
}

/*
 * Hook: outgoing binder calls (HAL -> framework callbacks).
 *
 * Key insight: the OPlus HAL sends onAcquired with vendor codes 22 (finger
 * down / need HBM) and 23 (finger up / HBM off).  This fires even under
 * deep AOD, where the kernel's fp_state doesn't report finger-down events.
 */
extern "C"
binder_status_t AIBinder_transact(AIBinder* binder, transaction_code_t code,
                                  AParcel** in, AParcel** out, binder_flags_t flags) {
    using Fn = binder_status_t (*)(AIBinder*, transaction_code_t, AParcel**, AParcel**, binder_flags_t);
    static auto orig = (Fn)dlsym(RTLD_NEXT, "AIBinder_transact");
    if (!orig) {
        ALOGE("AIBinder_transact: dlsym failed");
        resetCapture();
        return STATUS_UNKNOWN_ERROR;
    }

    /* onAcquired: inspect parcel for vendor finger down/up codes */
    if (code == ISessionCallback::TRANSACTION_onAcquired &&
        gCapture.parcel && in && *in == gCapture.parcel && gCapture.count > 0) {
        /*
         * onAcquired(AcquiredInfo info, int32_t vendorCode): the vendor code
         * only carries meaning when info == VENDOR, and it is the second
         * int32 in the parcel. Match that shape first.
         */
        bool handled = false;
        if (gCapture.count >= 2 && gCapture.vals[0] == kAcquiredVendor) {
            const int32_t vendorCode = gCapture.vals[1];
            if (vendorCode == kVendorFingerDown) {
                ALOGI("onAcquired: vendor finger-down (%d) -> HBM on", vendorCode);
                setPressed(true);
                handled = true;
            } else if (vendorCode == kVendorFingerUp) {
                ALOGI("onAcquired: vendor finger-up (%d) -> HBM off", vendorCode);
                setPressed(false);
                handled = true;
            } else {
                handled = true;  // a vendor code we don't care about
            }
        }

        /*
         * Fallback to the original loose scan if the parcel didn't have the
         * expected layout, so a future AIDL revision can't silently disable
         * the AOD path. The warning makes the mismatch visible in logcat.
         */
        if (!handled) {
            for (int i = 0; i < gCapture.count; ++i) {
                if (gCapture.vals[i] == kVendorFingerDown) {
                    ALOGW("onAcquired: finger-down matched by fallback scan "
                          "(count=%d, vals[0]=%d) -> HBM on",
                          gCapture.count, gCapture.vals[0]);
                    setPressed(true);
                    break;
                }
                if (gCapture.vals[i] == kVendorFingerUp) {
                    ALOGW("onAcquired: finger-up matched by fallback scan "
                          "(count=%d, vals[0]=%d) -> HBM off",
                          gCapture.count, gCapture.vals[0]);
                    setPressed(false);
                    break;
                }
            }
        }
    }

    /* Session-end events: ensure HBM is off and reset monitor state */
    if (code == ISessionCallback::TRANSACTION_onAuthenticationSucceeded ||
        code == ISessionCallback::TRANSACTION_onError ||
        code == ISessionCallback::TRANSACTION_onSessionClosed) {
        ALOGI("session end (code=%d) -> HBM off + reset", code);
        notifySessionEnd();
    }

    binder_status_t ret = orig(binder, code, in, out, flags);

    resetCapture();
    return ret;
}

/*
 * Hook: binder class registration — wrap ISession to see incoming calls.
 * May not fire on all devices (HIDL HALs), but harmless if it doesn't.
 */
extern "C"
AIBinder_Class* AIBinder_Class_define(const char* interfaceDescriptor,
                                      AIBinder_Class_onCreate onCreate,
                                      AIBinder_Class_onDestroy onDestroy,
                                      AIBinder_Class_onTransact onTransact) {
    using Fn = AIBinder_Class* (*)(const char*, AIBinder_Class_onCreate,
                                   AIBinder_Class_onDestroy, AIBinder_Class_onTransact);
    static auto orig = (Fn)dlsym(RTLD_NEXT, "AIBinder_Class_define");
    if (!orig) {
        ALOGE("AIBinder_Class_define: dlsym failed");
        return nullptr;
    }

    if (interfaceDescriptor) {
        ALOGI("AIBinder_Class_define: %s", interfaceDescriptor);
    }

    if (interfaceDescriptor &&
        strncmp(interfaceDescriptor, kSessionDesc, sizeof(kSessionDesc) - 1) == 0 &&
        (interfaceDescriptor[sizeof(kSessionDesc) - 1] == '\0' ||
         interfaceDescriptor[sizeof(kSessionDesc) - 1] == '/')) {
        /*
         * Never wrap our own wrapper, and never overwrite an already-captured
         * original: a second registration would otherwise make
         * gOrigSessionOnTransact point at sessionOnTransactWrapper and recurse.
         */
        if (onTransact == sessionOnTransactWrapper) {
            ALOGW("AIBinder_Class_define: already wrapped, passing through");
        } else if (gOrigSessionOnTransact != nullptr) {
            ALOGW("AIBinder_Class_define: ISession registered twice, "
                  "keeping the first handler");
        } else {
            ALOGI("Wrapping ISession onTransact (descriptor: %s)", interfaceDescriptor);
            gOrigSessionOnTransact = onTransact;
            return orig(interfaceDescriptor, onCreate, onDestroy, sessionOnTransactWrapper);
        }
    }

    return orig(interfaceDescriptor, onCreate, onDestroy, onTransact);
}

__attribute__((constructor))
static void init() {
    ALOGI("OplusFodShim: constructor");
    pthread_t t;
    if (pthread_create(&t, nullptr, startupThread, nullptr) == 0) {
        pthread_setname_np(t, "FodStartup");
        pthread_detach(t);
        ALOGI("OplusFodShim: startup thread created");
    } else {
        ALOGE("OplusFodShim: failed to create startup thread");
        bool definitive = false;
        tryStartMonitor(&definitive);
    }
}

__attribute__((destructor))
static void cleanup() {
    ALOGI("OplusFodShim: destructor");
    if (gMonitorRunning.exchange(false, std::memory_order_relaxed)) {
        pthread_join(gMonitorThread, nullptr);
    }
    setPressed(false);
    pthread_mutex_lock(&gIoMutex);
    if (gFodFd >= 0) {
        close(gFodFd);
        gFodFd = -1;
    }
    gLastWritten = -1;
    pthread_mutex_unlock(&gIoMutex);
}
