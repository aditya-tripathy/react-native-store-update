import React, { useEffect, useState } from 'react';
import { Button, StyleSheet, Text, View } from 'react-native';
import {
  addInstallStatusListener,
  checkForUpdate,
  completeUpdate,
  startUpdate,
} from 'react-native-inapp-update';

export default function App() {
  const [info, setInfo] = useState(null);
  const [installStatus, setInstallStatus] = useState('--');
  const [error, setError] = useState(null);

  useEffect(() => {
    checkForUpdate().then(setInfo).catch((e) => setError(e.message));

    const subscription = addInstallStatusListener((event) => {
      setInstallStatus(
        `${event.status} (${event.bytesDownloaded}/${event.totalBytesToDownload})`
      );
    });
    return () => subscription.remove();
  }, []);

  const onUpdate = async (updateType) => {
    setError(null);
    try {
      setInstallStatus(await startUpdate(updateType));
    } catch (e) {
      setError(e.message);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>InAppUpdate example</Text>
      <Text style={styles.row}>
        Update available: {info ? String(info.updateAvailable) : '--'}
      </Text>
      <Text style={styles.row}>
        Current version: {info ? info.currentVersion : '--'}
      </Text>
      <Text style={styles.row}>
        Store version: {info ? info.storeVersion || '(n/a)' : '--'}
      </Text>
      <Text style={styles.row}>Install status: {installStatus}</Text>
      <Button title="Flexible update" onPress={() => onUpdate('flexible')} />
      <Button title="Immediate update" onPress={() => onUpdate('immediate')} />
      <Button title="Complete update" onPress={() => completeUpdate()} />
      {error != null && <Text style={styles.error}>Error: {error}</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 16,
  },
  title: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  row: {
    textAlign: 'center',
    marginBottom: 5,
  },
  error: {
    textAlign: 'center',
    color: '#c00',
    marginTop: 10,
  },
});
