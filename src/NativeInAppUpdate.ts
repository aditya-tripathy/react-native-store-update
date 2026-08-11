import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface UpdateInfo {
  updateAvailable: boolean;
  currentVersion: string;
  storeVersion: string;
  currentVersionCode: number;
  availableVersionCode: number;
  updatePriority: number;
  clientVersionStalenessDays: number;
  isFlexibleUpdateAllowed: boolean;
  isImmediateUpdateAllowed: boolean;
  isUpdateInProgress: boolean;
  installStatus: string;
}

export interface Spec extends TurboModule {
  checkForUpdate(country: string): Promise<UpdateInfo>;
  startUpdate(updateType: string): Promise<string>;
  completeUpdate(): Promise<void>;

  readonly addListener: (eventName: string) => void;
  readonly removeListeners: (count: number) => void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('InAppUpdate');
