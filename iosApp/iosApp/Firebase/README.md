# Firebase environments

Place the Apple configuration files downloaded from Firebase Console in this folder:

- `GoogleService-Info-Dev.plist` for the development Firebase project.
- `GoogleService-Info-Prod.plist` for the production Firebase project.

Both Firebase Apple apps must use the exact bundle identifier configured by the selected Xcode build. The application chooses the development file for `Debug` and the production file for `Release`.

Do not place an APNs `.p8` key in the app. Upload that key in Firebase Console under Project settings > Cloud Messaging.
