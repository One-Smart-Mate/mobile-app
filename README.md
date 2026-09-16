# OneSmartMate

Kotlin Multiplatform application with shared business logic and native presentation layers:

- `sharedLogic`: shared domain, data, networking, persistence and dependency injection.
- `androidApp`: Android entry point, native Jetpack Compose UI and design system.
- `iosApp`: native SwiftUI application consuming `SharedLogic`.

The Android production application keeps the published package name `com.ih.osm`.
The `dev` flavor uses `com.ih.osm.dev` so both variants can be installed together.

## Local configuration

Copy the documented keys from `local.properties.example` into the ignored
`local.properties` file. API configuration is injected into Android `BuildConfig`:

```properties
OSM_DEV_API_BASE_URL=https://api.dev.one-sm.com/
OSM_PROD_API_BASE_URL=https://api.one-sm.com/
```

iOS uses the platform-native equivalent. Copy
`iosApp/Configuration/Secrets.xcconfig.example` to
`iosApp/Configuration/Secrets.xcconfig`. Debug selects development and Release
selects production. The real file is ignored by version control.

API URLs are environment configuration, not confidential credentials. Private
server keys must never be embedded in either mobile application because values
inside an APK or IPA can be extracted.

## Android signing

The production build reads the existing Play Store signing identity from ignored
local configuration or CI environment variables:

```properties
OSM_KEYSTORE_FILE=/absolute/path/to/android-key
OSM_KEYSTORE_PASSWORD=...
OSM_KEY_ALIAS=android-key
OSM_KEY_PASSWORD=...
```

Do not commit the keystore or its passwords. A release is signed only when all
four values are present. Before the first Play Store upload, verify that its
certificate fingerprint matches the currently published application and increase
`versionCode` above the current store value.

## Networking and dependency injection

Ktor is configured once in `sharedLogic` with JSON negotiation, timeouts, a base
URL and development-only metadata logging. The `NetworkClient` centralizes HTTP
verbs and maps responses into `NetworkResult`.

Koin owns the shared network graph. Android starts it from
`OneSmartMateApplication`; iOS starts the same graph from the SwiftUI `App` entry
point. Platform ViewModels remain native and will consume shared use cases or
controllers as features are added.

## Build verification

```bash
./gradlew :sharedLogic:allTests
./gradlew :androidApp:assembleDevDebug
./gradlew :androidApp:assembleProdDebug
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode or build the `iosApp` scheme.
