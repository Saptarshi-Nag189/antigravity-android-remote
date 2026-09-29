# Contributing to Antigravity Remote

Thank you for your interest in contributing to Antigravity Remote.

## 1. Principles
- **100% Free and Open Source**: No proprietary SDKs, advertising, or telemetry libraries are permitted.
- **Privacy First**: Never commit personal email addresses, device hostnames, or authentication tokens.
- **Google Developer Documentation Style**: All UI elements, buttons, menus, and toasts must follow sentence case and the guidelines at [https://developers.google.com/style/ui-elements](https://developers.google.com/style/ui-elements).
- **Zero Emojis**: Emojis are strictly prohibited across code, layouts, drawables, strings, and notifications.

## 2. Development Setup
Prerequisites:
- Android SDK 35 (API 35/36)
- Java Development Kit (JDK) 17
- Gradle 8.14+ (bundled via `./gradlew`)

To build the project locally:
```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned, ready for F-Droid signing)
./gradlew assembleRelease
```

## 3. Submitting Changes
1. Fork the repository.
2. Create a descriptive feature branch (`git checkout -b feature/my-feature`).
3. Verify that both debug and release builds pass:
   ```bash
   ./gradlew test assembleRelease
   ```
4. Check that no personal data or PII was introduced:
   ```bash
   git grep -i "gmail"
   ```
5. Commit your changes and open a pull request.
