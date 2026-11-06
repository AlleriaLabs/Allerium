# Allerium

**Allerium** is an Android companion for managing JFA-GO access on your own server, built by **Alleria Labs**.

## What it does

- Save and switch between multiple JFA-GO servers
- Sign in with a server account
- View, search, create, enable, disable, and remove users
- Set user account expiry dates
- Create and manage invitation links, including profile and usage limits
- Review and clear activity entries
- Send announcements to selected users
- View and update server configuration settings

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Android 7.0 (API 24) and newer

## Build

Open this repository in Android Studio or run:

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Server setup

Add the base URL of your JFA-GO server in the app. Use HTTPS whenever it is available. The app stores server names, URLs, and usernames locally; the password is entered at sign-in and the session token is kept in memory for the active session.

## Compatibility note

The Android application ID remains `app.jfago` so existing AlPass installations can receive Allerium updates without requiring an uninstall.

---

© Alleria Labs
