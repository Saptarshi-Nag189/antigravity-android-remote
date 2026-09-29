# Privacy Policy for Antigravity Remote

**Last Updated**: September 2026

## 1. Overview
Antigravity Remote ("the Application") is an independent, open-source client designed to provide a mobile interface for Google Antigravity web development sessions.

The Application is built with privacy-by-design principles:
- **Zero Third-Party Telemetry**: Contains no Google Play Services tracking, Google Analytics, Firebase, Crashlytics, or third-party advertising SDKs.
- **Direct Connection Only**: The Application communicates exclusively with official Google authentication endpoints (`antigravity.google.com`, `accounts.google.com`) or the workstation host URL you explicitly specify in the remote link settings. No data passes through any intermediary server.

## 2. Data Collection and Processing
The Application processes the following data exclusively on your device:
- **Authentication Credentials and Cookies**: Google authentication cookies are managed locally by the standard Android Chromium WebView container (`CookieManager`). The Application does not store, log, or transmit your session tokens anywhere other than Google's official endpoints.
- **Account Metadata**: Display name, email address, and profile picture URL are extracted directly from your authenticated Google Antigravity session and stored in your device's private app storage (`SharedPreferences`) solely to populate the companion controls.
- **Workstation Configuration**: Hostnames and custom remote session links entered in the companion controls remain strictly in private device storage.

## 3. Data Deletion
You can immediately purge all cached data, authentication cookies, HTML5 web storage, and profile information by tapping **Sign out of all accounts** in the companion controls. This permanently deletes:
- All cookies from `CookieManager`
- All HTML5 local storage, session storage, and IndexedDB data
- Cached avatar bitmaps from internal device storage
- Cached user preferences

## 4. Device Permissions Justification
- `android.permission.INTERNET`: Required to communicate with `antigravity.google.com` and load the remote agent interface.
- `android.permission.ACCESS_NETWORK_STATE`: Monitors network connectivity changes to maintain streaming WebSocket sessions and toggle the offline status view.
- `android.permission.FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC`: Keeps streaming responses and WebSockets active when switching apps. Displays an ongoing status notification.
- `android.permission.POST_NOTIFICATIONS`: Required on Android 13+ to display the foreground keep-alive service notification.
- `android.permission.WAKE_LOCK`: Prevents process freezing during active agent task execution.
- `android.permission.READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`: Used solely when you explicitly attach an image or screenshot to the chat via the standard Android photo picker.

## 5. F-Droid Anti-Features Declaration
- **NonFreeNet**: The Application communicates with Google Antigravity, which is a proprietary network service. No proprietary binaries or SDKs are bundled in this build.
