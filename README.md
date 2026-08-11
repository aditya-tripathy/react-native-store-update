# react-native-inapp-update

[![npm version](https://img.shields.io/npm/v/react-native-inapp-update.svg)](https://www.npmjs.com/package/react-native-inapp-update)
[![license](https://img.shields.io/npm/l/react-native-inapp-update.svg)](LICENSE)

📦 **npm**: [react-native-inapp-update](https://www.npmjs.com/package/react-native-inapp-update)

Prompt users to update your React Native app:

- **Android** — real [Play in-app updates](https://developer.android.com/guide/playcore/in-app-updates)
  (flexible and immediate flows) via `AppUpdateManager`
- **iOS** — the installed version is compared against the App Store listing, and
  `startUpdate()` opens the store page (iOS has no in-app update API)

Written as a TurboModule with a backward-compatible spec, so it works on both the
**new architecture** and the **old architecture**.

## Requirements

- React Native **0.74 or newer**
- Android minSdk 24, `com.google.android.play:app-update` (bundled)
- iOS: the minimum iOS version supported by your React Native version

## Installation

```sh
npm install react-native-inapp-update
# or
yarn add react-native-inapp-update
```

Then install pods:

```sh
cd ios && pod install
```

Autolinking takes care of the rest — no manual linking.

## Usage

```javascript
import {
  checkForUpdate,
  startUpdate,
  completeUpdate,
  addInstallStatusListener,
} from 'react-native-inapp-update';

const info = await checkForUpdate();

if (info.updateAvailable) {
  // Android: shows the Play dialog. iOS: opens the App Store page.
  await startUpdate('flexible');
}
```

A flexible update downloads in the background, so listen for progress and install
it once it is ready:

```javascript
const subscription = addInstallStatusListener(async (event) => {
  if (event.status === 'downloaded') {
    // Restarts the app to install the update.
    await completeUpdate();
  }
});

// later
subscription.remove();
```

For an immediate update, Play takes over the screen and restarts the app itself:

```javascript
if (info.updateAvailable && info.isImmediateUpdateAllowed) {
  await startUpdate('immediate');
}
```

## API

### `checkForUpdate(options?): Promise<UpdateInfo>`

`options.country` (iOS only) is the two-letter App Store storefront to look the app
up in, e.g. `'in'`. Without it Apple only searches the US storefront.

Resolves with:

| Field | Type | Description |
| --- | --- | --- |
| `updateAvailable` | `boolean` | Android: Play reports an update rolled out to this user. iOS: the App Store version is higher than the installed one. |
| `currentVersion` | `string` | Installed `versionName` (Android) / `CFBundleShortVersionString` (iOS). |
| `storeVersion` | `string` | iOS App Store version. Always `''` on Android — Play only exposes version codes. |
| `currentVersionCode` | `number` | Installed `versionCode` on Android, `-1` on iOS. |
| `availableVersionCode` | `number` | Version code of the pending update on Android, `-1` on iOS. |
| `updatePriority` | `number` | Play [update priority](https://developer.android.com/guide/playcore/in-app-updates#update-priority) (0–5), `-1` on iOS. |
| `clientVersionStalenessDays` | `number` | Days since the update became available, `-1` when unknown or on iOS. |
| `isFlexibleUpdateAllowed` | `boolean` | Play allows a flexible update. Always `false` on iOS. |
| `isImmediateUpdateAllowed` | `boolean` | Play allows an immediate update. Always `false` on iOS. |
| `isUpdateInProgress` | `boolean` | An immediate update was already started and should be resumed. Always `false` on iOS. |
| `installStatus` | `InstallStatus` | Current Play install status. Always `'unknown'` on iOS. |

Rejects with `E_UPDATE_CHECK_FAILED` when the store lookup fails, or
`E_APP_NOT_FOUND` on iOS when the bundle identifier is not on the storefront.

### `startUpdate(updateType?): Promise<StartUpdateResult>`

`updateType` is `'flexible'` (default) or `'immediate'`; it is ignored on iOS.

Resolves with `'accepted'`, `'canceled'` or `'failed'` on Android, reflecting what the
user did in the Play dialog, and `'openedAppStore'` on iOS. An accepted *immediate*
update usually never resolves, because Play restarts the app.

Rejects with `E_NO_UPDATE_AVAILABLE`, `E_UPDATE_TYPE_NOT_ALLOWED`, `E_NO_ACTIVITY`,
`E_UPDATE_IN_PROGRESS`, `E_INVALID_UPDATE_TYPE` or `E_UPDATE_FAILED` on Android, and
`E_NO_STORE_URL` / `E_OPEN_STORE_FAILED` on iOS. On iOS `checkForUpdate()` must run
first so the store URL is known.

### `completeUpdate(): Promise<void>`

Installs a downloaded flexible update and restarts the app. Android only —
resolves without doing anything on iOS. Rejects with `E_COMPLETE_UPDATE_FAILED`.

### `addInstallStatusListener(listener): EventSubscription`

Android only. Called with `{ status, bytesDownloaded, totalBytesToDownload, errorCode }`
as a flexible update downloads. `status` is one of `'unknown'`, `'pending'`,
`'downloading'`, `'downloaded'`, `'installing'`, `'installed'`, `'failed'`,
`'canceled'`, `'requiresUiIntent'`.

### `checkUpdate(): Promise<StartUpdateResult | 'noUpdateAvailable'>`

The v1 helper, kept for compatibility: checks, then starts an immediate update when
the update has been available for more than five days and a flexible one otherwise.

## Platform notes

### Android

- In-app updates only work for apps **installed by Google Play**. A debug build
  installed over adb always reports no update available — test with
  [internal app sharing](https://support.google.com/googleplay/android-developer/answer/9844679)
  or the internal test track, using a build whose `versionCode` is lower than the
  one on Play.
- The Play dialog needs a foreground activity; calling `startUpdate()` while the app
  is in the background rejects with `E_NO_ACTIVITY`.

### iOS

- There is no way to install an update from inside the app. `startUpdate()` opens
  the App Store listing and the user updates from there.
- Apple's lookup endpoint is CDN-cached, so a freshly released version can take a
  few hours to be reported.
- Version comparison is numeric per dot-separated component (`1.2.10 > 1.2.9`);
  non-numeric suffixes such as `-beta` are ignored.

## Migrating from `react-native-android-in-app-update`

v1 exposed a single `checkUpdate()` that decided the update type, showed a native
Snackbar and restarted the app on its own. v2 replaces that with an explicit API and
adds iOS support:

- `AndroidInAppUpdate.checkUpdate()` → `checkUpdate()` (same policy, no native UI),
  or better, `checkForUpdate()` + `startUpdate()`.
- The Snackbar is gone: render your own prompt from
  `addInstallStatusListener()` and call `completeUpdate()` when the status is
  `'downloaded'`. Nothing restarts the app unless you ask it to.
- The module is now a TurboModule, so it needs React Native 0.74 or newer.

## License

MIT
