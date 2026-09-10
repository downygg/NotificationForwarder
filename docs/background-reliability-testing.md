# Background Reliability Acceptance Tests

These tests validate Android-supported background behavior. They do not treat Force Stop recovery as a requirement.

## Record device context

For every physical device record:

- manufacturer
- model
- Android version
- Notification Access state
- battery optimization state
- OEM Auto Start/background setting where applicable

## Recents removal

1. Install and configure NotificationForwarder.
2. Grant Notification Access.
3. Configure a valid webhook.
4. Select a test app in WHITELIST.
5. Set NotificationForwarder battery usage to Unrestricted.
6. Swipe NotificationForwarder away from Recents.
7. Do not reopen the UI.
8. Generate a notification from the whitelisted app.
9. Verify it is captured and delivered to the webhook.

Expected: forwarding works without MainActivity remaining open.

## Screen off / idle

1. Configure NotificationForwarder and set battery usage to Unrestricted.
2. Close the UI and turn the screen off.
3. Leave the device idle long enough to exercise background restrictions.
4. Trigger a notification where practical.
5. Verify notification capture and webhook delivery.

## Network loss and restoration

1. Confirm Notification Access and webhook configuration.
2. Disable Wi-Fi and mobile data.
3. Generate several notifications.
4. Verify queue items remain persisted.
5. Swipe NotificationForwarder away from Recents.
6. Restore network connectivity without reopening the app.
7. Wait for WorkManager to become eligible.
8. Verify pending queue processing resumes and no unexpected concurrent duplicates occur.

Expected: normal connectivity recovery does not require manual Retry All or reopening MainActivity.

## Reboot

1. Configure NotificationForwarder, Notification Access, webhook, and Unrestricted battery usage.
2. Enable OEM Auto Start/background permission if the device exposes it.
3. Confirm normal forwarding works.
4. Reboot the device and unlock normally.
5. Do not manually open NotificationForwarder.
6. Trigger a notification from a selected app.
7. Verify capture and webhook delivery.
8. Repeat with pending queue items present before reboot and verify queue recovery.

Note: the current app uses credential-protected Room/preferences. `LOCKED_BOOT_COMPLETED` is declared, but this project does not implement a Direct Boot storage architecture. Normal post-unlock `BOOT_COMPLETED` recovery is the supported target.

## Normal process death

Using development tools where practical, simulate process reclamation without using a command that applies Force Stop semantics.

Verify:

- Room queue survives
- WorkManager state survives
- listener reconnects when Android recreates the process
- MainActivity does not need to remain open

Do not use a Force Stop test as proof of normal process-death behavior.

## Force Stop

1. Open Android Settings → Apps → NotificationForwarder → Force Stop.
2. Generate a notification.

Expected: automatic recovery is not required and may not occur because Android placed the app in the stopped state.

3. Open/interact with NotificationForwarder again.
4. Verify normal operation resumes.

Force Stop semantics must not be bypassed with alarms, services, accessibility, or restart loops.
