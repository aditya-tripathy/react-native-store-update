# react-native-store-update

[![npm version](https://img.shields.io/npm/v/react-native-store-update.svg)](https://www.npmjs.com/package/react-native-store-update)
[![license](https://img.shields.io/npm/l/react-native-store-update.svg)](LICENSE)

📦 **npm**: [react-native-store-update](https://www.npmjs.com/package/react-native-store-update)

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
npm install react-native-store-update
# or
yarn add react-native-store-update
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
} from 'react-native-store-update';

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
`E_UPDATE_IN_PROGRESS`, `E_INVALID_UPDATE_TYPE`, `E_UPDATE_FAILED` or
`E_UPDATE_RESULT_LOST` on Android, and `E_NO_STORE_URL` / `E_OPEN_STORE_FAILED` on iOS.
On iOS `checkForUpdate()` must run first so the store URL is known.

Only one Android update flow runs at a time; a second call while one is pending rejects
with `E_UPDATE_IN_PROGRESS`. `E_UPDATE_RESULT_LOST` means Play's dialog closed and the app
came back to the foreground without Play reporting what the user chose — check again and
retry if the update is still available.

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

## Example app

[`example/App.js`](example/App.js) is a single screen that calls `checkForUpdate()`
on mount, prints everything it resolves with, and has buttons for a flexible
update, an immediate update and `completeUpdate()`. It is the quickest way to see
what Play reports for a given build.

To run it, drop it into a React Native app:

```sh
npx @react-native-community/cli init UpdateExample
cd UpdateExample
npm install react-native-store-update
curl -o App.js https://raw.githubusercontent.com/aditya-tripathy/react-native-store-update/master/example/App.js
```

Delete `App.tsx` if the template created one, then build a **release** APK/AAB and
publish it as described below — the example is only useful on a Play-installed
build.

## Testing in-app updates on Android

Play in-app updates cannot be tested from a local build. The Play Store decides
whether an update exists, and it only answers for an app that **it** installed,
signed with the same key as the build on Play. A debug build installed over
`adb install` always resolves `updateAvailable: false`.

There are two ways to get a real update flow on a device.

### Option 1 — Internal app sharing (fastest)

No review, no version code rules, no tester lists. Good for iterating.

1. In Play Console, open **Test and release → Internal app sharing** and upload an
   AAB/APK of your app — call it **build A** (e.g. `versionCode 1`).
2. On the device, open the Play Store → **Settings → General → Internal app
   sharing** and turn it on. Open the share link for build A and install from
   there. It must be installed through the link, not adb.
3. Upload **build B** with a higher `versionCode` (e.g. `2`) to internal app
   sharing as well. Do not install it.
4. Open build A on the device and run the example app / your own
   `checkForUpdate()`. Play now reports `updateAvailable: true` with
   `availableVersionCode: 2`, and `startUpdate('flexible' | 'immediate')` shows the
   real Play dialog.

### Option 2 — Internal / closed (alpha, beta) test track

Closer to production, and the only way to test update **priority** and staleness.

1. Upload build A (`versionCode 1`) to **Internal testing** (or a closed
   alpha/beta track) and add your Google account to the tester list.
2. Open the opt-in link on that account, install the app from Play, and confirm it
   came from Play (its listing shows "Installed").
3. Upload build B (`versionCode 2`) to the same track and let it roll out.
4. Reopen build A. It may take a few hours before Play serves the new version to
   the device — force it along with Play Store → **Manage apps & device**, or clear
   the Play Store app's cache.

Notes for either option:

- The signing key must match. Use the same upload key / Play App Signing for both
  builds, and build in **release** mode.
- Test on the Google account that is a tester on that track. Other accounts on the
  device will not see the update.
- `isImmediateUpdateAllowed` / `isFlexibleUpdateAllowed` come from Play, not from
  this library. If `startUpdate('immediate')` rejects with
  `E_UPDATE_TYPE_NOT_ALLOWED`, Play has not allowed that flow for the build.
- `updatePriority` can only be set through the
  [Play Developer API](https://developer.android.com/guide/playcore/in-app-updates#update-priority)
  (`edits.tracks.releases.inAppUpdatePriority`) when you publish the release — it
  cannot be set in the Play Console UI, and it is `0` unless you set it.
- `clientVersionStalenessDays` starts counting from when the update reached the
  device, so it is `0` or `-1` on a freshly published build. The five-day rule in
  `checkUpdate()` will therefore pick a flexible update while testing.
- Flexible updates download in the background: after `startUpdate('flexible')`
  resolves `'accepted'`, watch `addInstallStatusListener()` for `'downloading'` →
  `'downloaded'`, then call `completeUpdate()` to restart into the new build.
- Immediate updates take over the screen and Play restarts the app itself, so
  `startUpdate('immediate')` usually never resolves.

To exercise your own UI without Play, wrap `checkForUpdate()` behind a flag and
return a fake `UpdateInfo` in development. For native instrumentation tests,
Play Core ships
[`FakeAppUpdateManager`](https://developer.android.com/guide/playcore/in-app-updates/test),
which is not reachable from JavaScript.

## Platform notes

### Android

- In-app updates only work for apps **installed by Google Play** — see
  [Testing in-app updates on Android](#testing-in-app-updates-on-android).
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
