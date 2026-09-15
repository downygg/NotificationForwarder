# Notification Forwarder (Android)

Android app to listen for incoming notifications and forward them to a configurable webhook API.

## Features

- Notification capture using `NotificationListenerService`
- Webhook forwarding with configurable URL, HTTP method, auth mode, custom headers, query params, and payload template
- Queue system with Room (durable local storage)
- Retry system with WorkManager (network constraints + backoff)
- Manual single-item retry and Retry All using the same atomic queue claim as automatic delivery
- Queue lifecycle timestamps and persistent Diagnostics/Event Log
- Background support and boot recovery

## Queue observability

Queue rows expose the delivery lifecycle using local-time formatting with seconds:

- **Captured** (`capturedAt`): when `NotificationListenerService.onNotificationPosted()` received the callback. Historical rows created before database v2 remain empty because this time cannot be reconstructed reliably.
- **Queued** (`createdAt`): when the event was accepted/persisted into the delivery queue. The existing creation timestamp is the source of truth.
- **First Attempt** (`firstAttemptAt`): timestamp of the first real delivery claim. It is written atomically with the queue claim and never overwritten by later retries.
- **Last Attempt** (`lastAttemptAt`): timestamp of the most recent real delivery claim. A button press that loses the atomic claim does not update it.
- **Sent** (`sentAt`): set only after the existing webhook success criteria report success.
- **Next Retry** (`nextRetryAt`): the existing retry/backoff source of truth; shown only while an automatic retry is pending.
- **Attempts** (`attemptCount`): incremented atomically only when the authoritative delivery path successfully claims the row for an HTTP attempt.

`Last Attempt` deliberately differs from `Sent`: failed HTTP/network requests are attempts but are not successful sends.

## Diagnostics

The **Diagnostics** tab provides a persistent structured event log without requiring ADB/Logcat. It records listener connection/disconnection/rebind requests, notification reception and filter outcomes, queue creation, worker execution, webhook attempts/results, retry scheduling, manual retry requests, Retry All, application startup, and boot handling where those events are observable.

The overview reports Notification Access separately from actual listener runtime state. `Connected` is shown only from `NotificationListenerService.onListenerConnected()` state; after process startup the state is `Unknown` until Android provides a trustworthy callback.

Diagnostics retain the newest **3,000** events. Cleanup runs off the notification callback path and also at application startup. Diagnostic writes are best-effort: a logging failure is caught and never becomes a prerequisite for forwarding.

For privacy, diagnostics store operational metadata such as package name, queue ID, rule/filter mode, attempt number, HTTP status and failure category. They do **not** duplicate webhook payloads, notification title/text, Authorization headers, API keys, bearer tokens, cookies, or credentials.

## Background Reliability Setup

1. Open app -> **Home**.
2. Open Notification Access settings and enable Notification Access.
3. Open Battery Settings and set the app to unrestricted/no restriction if available.
4. On OEM ROMs that require it, enable Auto Start for the app.

## Build and validation

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

GitHub Actions runs the same test, lint and debug-build sequence for branch pushes.

## Webhook Configuration

Supported HTTP methods are `GET`, `POST`, `PUT`, and `PATCH`. Authentication can be None, Bearer, or custom headers. Query parameters use `key=value` per line.

Payload templates support `{deviceId}`, `{packageName}`, `{appName}`, `{title}`, `{text}`, `{postedAt}`, and `{notificationKey}`. Leave the template blank to use the default JSON payload.

## Local Webhook API (`webhook/`)

The repository includes a Node.js webhook receiver for local testing. Run `npm install`, copy `.env.example` to `.env`, then use `npm run start`. The default endpoint is `POST /webhook` and health check is `GET /health`.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
