# SPDX-FileCopyrightText: 2023-2024 Andrew Gunnerson
# SPDX-License-Identifier: GPL-3.0-only

# On some devices, the system time is set too late in the boot process. This,
# for some reason, causes the package manager service to not update the cache
# entry for BCR despite the mtime of the apk being newer than the mtime of the
# cache entry [1]. This causes BCR to crash with an obscure error about the app
# theme not being derived from Theme.AppCompat. This script works around the
# issue by forcibly deleting BCR's package manager cache entry on every boot.
#
# [1] https://cs.android.com/android/platform/superproject/+/android-13.0.0_r42:frameworks/base/services/core/java/com/android/server/pm/parsing/PackageCacher.java;l=139

# Source common functions
source "${0%/*}/boot_common.sh" /data/local/tmp/bcr_post-fs-data.log

# 1. Timestamp and debug info (keeps your existing logging)
header "Timestamps"
ls -ldZ "${cli_apk%/*}"
find /data/system/package_cache -name "${app_id}-*" -exec ls -ldZ {} \+

# 2. Clear caches (your existing functionality)
header "Clear package manager caches"
run_cli_apk com.chiller3.bcr.standalone.ClearPackageManagerCachesKt

# 3. Auto-grant all permissions (NEW SECTION)
header "Granting permissions"
PKG="com.chiller3.bcr"

# List of permissions to grant (from your manifest)
PERMS=(
  "android.permission.CONTROL_INCALL_EXPERIENCE"
  "android.permission.RECORD_AUDIO"
  "android.permission.READ_CONTACTS"
  "android.permission.FOREGROUND_SERVICE"
  "android.permission.FOREGROUND_SERVICE_MICROPHONE"
  "android.permission.POST_NOTIFICATIONS"
  "android.permission.READ_CALL_LOG"
  "android.permission.READ_PHONE_STATE"
  "android.permission.RECEIVE_BOOT_COMPLETED"
  "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
  "android.permission.VIBRATE"
  "android.permission.INTERNET"
)

for PERM in "${PERMS[@]}"; do
  cmd package grant "$PKG" "$PERM" && \
    log_i "Granted $PERM" || \
    log_e "Failed to grant $PERM"
done

# 4. Special handling for runtime permissions
appops set "$PKG" RECORD_AUDIO allow
appops set "$PKG" POST_NOTIFICATIONS allow
