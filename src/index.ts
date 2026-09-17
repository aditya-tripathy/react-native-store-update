import { NativeEventEmitter } from 'react-native';
import type { EventSubscription } from 'react-native';
import NativeInAppUpdate from './NativeInAppUpdate';
import type { UpdateInfo as NativeUpdateInfo } from './NativeInAppUpdate';

/** Play Store update flow. Ignored on iOS, where the App Store page is opened instead. */
export type AppUpdateType = 'flexible' | 'immediate';

/** Mirrors Play's `InstallStatus`. Always `'unknown'` on iOS. */
export type InstallStatus =
  | 'unknown'
  | 'pending'
  | 'downloading'
  | 'downloaded'
  | 'installing'
  | 'installed'
  | 'failed'
  | 'canceled'
  /** Android: Play needs the user to confirm the update in its UI before it can continue. */
  | 'requiresUiIntent';

export type StartUpdateResult =
  /** Android: the user accepted the update. Immediate updates restart the app from here. */
  | 'accepted'
  /** Android: the user dismissed the Play update dialog. */
  | 'canceled'
  /** Android: Play could not run the update flow. */
  | 'failed'
  /** iOS: the App Store page for the app was opened. */
  | 'openedAppStore';

export interface UpdateInfo extends Omit<NativeUpdateInfo, 'installStatus'> {
  installStatus: InstallStatus;
}

export interface CheckForUpdateOptions {
  /**
   * iOS only: two-letter App Store storefront to look the app up in (for example `'in'`).
   * Defaults to the US storefront, which is all the Apple lookup API returns without it.
   */
  country?: string;
}

export interface InstallStatusEvent {
  status: InstallStatus;
  bytesDownloaded: number;
  totalBytesToDownload: number;
  /** Play `InstallErrorCode`, or `0` when there is no error. */
  errorCode: number;
}

const INSTALL_STATUS_EVENT = 'InAppUpdate:installStatusChanged';

type InstallStatusEvents = {
  [INSTALL_STATUS_EVENT]: [InstallStatusEvent];
};

const emitter = new NativeEventEmitter<InstallStatusEvents>(
  // The event emitter is Android-only; on iOS this module never emits.
  NativeInAppUpdate as unknown as ConstructorParameters<
    typeof NativeEventEmitter
  >[0]
);

/**
 * Asks the store whether a newer build of this app is available.
 *
 * On Android this queries Play's `AppUpdateManager`, so it only reports updates
 * that have actually been rolled out to the current user on a Play-installed build.
 * On iOS it looks the bundle identifier up in the App Store and compares versions.
 */
export function checkForUpdate(
  options?: CheckForUpdateOptions
): Promise<UpdateInfo> {
  return NativeInAppUpdate.checkForUpdate(
    options?.country ?? ''
  ) as Promise<UpdateInfo>;
}

/**
 * Starts the update.
 *
 * On Android this shows the Play update dialog: `'flexible'` downloads in the
 * background (listen with {@link addInstallStatusListener}, then call
 * {@link completeUpdate}), `'immediate'` blocks the app and restarts it itself.
 * On iOS the update type is ignored and the App Store page is opened —
 * {@link checkForUpdate} must have run first so the store URL is known.
 */
export function startUpdate(
  updateType: AppUpdateType = 'flexible'
): Promise<StartUpdateResult> {
  return NativeInAppUpdate.startUpdate(updateType) as Promise<StartUpdateResult>;
}

/**
 * Installs an already-downloaded flexible update and restarts the app.
 * Android only; resolves without doing anything on iOS.
 */
export function completeUpdate(): Promise<void> {
  return NativeInAppUpdate.completeUpdate();
}

/**
 * Subscribes to flexible-update download progress (Android only).
 * Remember to `.remove()` the returned subscription.
 */
export function addInstallStatusListener(
  listener: (event: InstallStatusEvent) => void
): EventSubscription {
  return emitter.addListener(INSTALL_STATUS_EVENT, listener);
}

/** Number of days an update may be pending before v1 escalated to an immediate update. */
const LEGACY_STALE_DAYS = 5;

/**
 * Backwards-compatible helper matching the v1 `checkUpdate()` behaviour: check,
 * then start an immediate update once the update has been available for more
 * than five days, and a flexible one otherwise.
 *
 * Unlike v1 this never shows native UI and never restarts the app on its own —
 * for flexible updates, listen with {@link addInstallStatusListener} and call
 * {@link completeUpdate} when the status becomes `'downloaded'`.
 */
export async function checkUpdate(): Promise<
  StartUpdateResult | 'noUpdateAvailable'
> {
  const info = await checkForUpdate();
  if (!info.updateAvailable) {
    return 'noUpdateAvailable';
  }
  const escalate =
    info.isImmediateUpdateAllowed &&
    info.clientVersionStalenessDays > LEGACY_STALE_DAYS;
  return startUpdate(escalate ? 'immediate' : 'flexible');
}

export default {
  checkForUpdate,
  startUpdate,
  completeUpdate,
  addInstallStatusListener,
  checkUpdate,
};
