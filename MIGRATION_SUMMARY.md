# Migration BLE vers euc_ble_library - Résumé

## 🎯 Objectif
Migrer WheelLog pour qu'il devienne un **client UI pur**, avec toute la logique métier BLE dans `euc_ble_library`.
Version cible : **FOSS pour F-Droid** (sans dépendances non-libres).

## 📊 État Actuel (Branche: feat/euc-ble-migration)

### ✅ **Déjà Accompli**

#### 1. Suppression du Code Legacy
- ✅ `WheelData.java` (1464 lignes) - **SUPPRIMÉ**
- ✅ `BluetoothService.kt` (500+ lignes) - **SUPPRIMÉ**
- ✅ Tous les adapters de marque (10 fichiers) - **SUPPRIMÉS**
  - KingsongAdapter.java
  - NinebotAdapter.java
  - NinebotZAdapter.java
  - InMotionAdapter.java
  - InmotionAdapterV2.java
  - GotwayAdapter.java
  - GotwayVirtualAdapter.java
  - VeteranAdapter.java
  - BaseAdapter.kt
- ✅ `ParserLogToWheelData.kt` - **SUPPRIMÉ**
- ✅ `WheelDataComposeBridge.kt` - **SUPPRIMÉ**

#### 2. Nouvelle Infrastructure
- ✅ `EucBleManager.kt` - **EXISTE** (votre travail)
  - Gère EucBleClient
  - Expose des StateFlow pour l'état de connexion, les données, les appareils
  - Gère les broadcasts pour la compatibilité legacy
  - Implémente connect(), connectByAddress(), startScan(), stopScan()

- ✅ `BleService.kt` - **EXISTE** (votre travail)
  - Service Android technique minimal
  - Gère WakeLock pendant la connexion
  - Gère les sons de connexion/déconnexion
  - Notification foreground

- ✅ `BleModule.kt` - **EXISTE** (votre travail)
  - Module Koin pour EucBleManager

- ✅ `WheelData.kt` - **NOUVEAU** (wrapper de compatibilité)
  - Remplace l'ancien WheelData.java
  - Délègue à EucBleManager
  - Implémente le tracking des valeurs maximales
  - Implémente les calculs (batteryPerKm, avgVoltagePerCell, etc.)
  - Permet à l'application de compiler pendant la migration

- ✅ `WheelTypeExtensions.kt` - **NOUVEAU**
  - Extensions pour convertir entre manufacturer et WHEEL_TYPE

#### 3. Fichiers Migrés
- ✅ `AppConfig.kt` - **MIGRÉ**
  - Utilise `eucBleManager.isConnected.value`
  - Suppression des appels à `bluetoothService` (géré par EucBleClient)

- ✅ `MainActivity.kt` - **PARTIELLEMENT MIGRÉ**
  - Utilise `eucBleManager.eucData.value?.speed`
  - Utilise `eucBleManager.eucData.value?.pwm`
  - Utilise `eucBleManager.connectedDevice.value?.manufacturer?.toLegacyWheelType()`
  - Utilise `eucBleManager.client.cleanup() + initialize()` pour full_reset
  - Utilise `eucBleManager.connectedDevice.value?.name` pour btName
  - ⚠️ `adapter?.switchFlashlight()` commenté (à implémenter avec CommandType.LIGHT_ON/OFF)

### 📈 **Statistiques**
- **Lignes de code legacy supprimées** : ~2 173
- **Lignes de code migrées** : ~2 415 (WheelView.kt) + ~1 000+ (autres fichiers)
- **Fichiers modifiés** : 15+
- **Fichiers supprimés** : 13
- **Fichiers créés** : 2 (SessionManager.kt, WheelTypeExtensions.kt)
- **Références à WheelData.getInstance() restantes** : **75** (dans 2 fichiers principaux)

## ✅ **Accomplissements**

### Fichiers Complètement Migrés

#### 1. **WheelView.kt** (1220+ lignes) - **MIGRÉ** ✅
**Actions accomplies :**
- ✅ Injection de `EucBleManager` via Koin
- ✅ Remplacement de toutes les références à `WheelData.getInstance()` par `eucBleManager` ou valeurs locales
- ✅ Implémentation du tracking des max values (session statistics)
- ✅ Observation du StateFlow `eucData` pour la mise à jour automatique
- ✅ Ajout des fonctions de calcul (batteryPerKm, avgVoltagePerCell, averageSpeed, remainingDistance)
- ✅ Suppression de la dépendance à ReflectUtil
- ✅ Adaptation du mode edit pour Android Studio preview
- ✅ Gestion du lifecycle pour l'annulation des coroutines

**Propriétés migrées :**
- `maxCurrentDouble` → `sessionMaxCurrent` (tracker local)
- `maxPhaseCurrentDouble` → `sessionMaxPhaseCurrent` (tracker local)
- `powerDouble` → `eucBleManager.eucData.value?.power`
- `maxPowerDouble` → `sessionMaxPower` (tracker local)
- `temperature2` → `eucBleManager.eucData.value?.temperature2`
- `averageSpeedDouble` → `calculateAverageSpeed()` (calcul local)
- `wheelDistanceDouble` → `eucBleManager.eucData.value?.wheelDistance`
- `remainingDistance` → `calculateRemainingDistance()` (calcul local)
- `batteryPerKm` → `calculateBatteryPerKm()` (calcul local)
- `avgVoltagePerCell` → `calculateAvgVoltagePerCell()` (calcul local)
- `rideTimeString` → `formatRideTime(eucBleManager.eucData.value?.rideTime)`
- `userDistanceDouble` → `eucBleManager.eucData.value?.totalDistance`
- `calculatedPwm` → `eucBleManager.eucData.value?.pwm`
- `maxPwm` → `sessionMaxPwm` (tracker local)

**Note :** La fonction `switchFlashlight()` est commentée temporairement (nécessite l'implémentation via `EucBleClient.sendCommand(CommandType.LIGHT_ON/OFF)`)

#### 2. **ScanActivity.kt** - **MIGRÉ** ✅
**Statut :** Utilise `EucBleManager` et observe `discoveredDevices` StateFlow

#### 3. **StartScreen.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData.getInstance().wheelType` par `eucBleManager.connectedDevice.value?.manufacturer?.toLegacyWheelType()`

#### 4. **LogScreen.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData.getInstance().isConnected` et `.mac`

#### 5. **TripScreen.kt** - **MIGRÉ** ✅
- Utilisation de `SessionManager` pour les fonctions reset

#### 6. **AlarmScreen.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData.getInstance().wheelType` et `.model`

#### 7. **WheelScreen.kt** - **MIGRÉ** ✅
- Remplacement de toutes les références à `WheelData`
- Adaptation des vérifications de vitesse (speed < 1 → speed < 0.1 km/h)

#### 8. **DialogHelper.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData` par `eucBleManager`

#### 9. **ElectroClub.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData.getInstance().isConnected`

#### 10. **AudioUtil.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData` par `eucBleManager`

#### 11. **SomeUtil.kt** - **MIGRÉ** ✅
- Remplacement de `WheelData`, wheelBeep commenté (à implémenter via EucBleClient)

#### 12. **Alarms.kt** - **MIGRÉ** ✅
- Remplacement de toutes les références à `WheelData.getInstance()`
- Adaptation des propriétés (speedDouble, batteryLevel, temperature, etc.)

#### 13. **SessionManager.kt** - **NOUVEAU** ✅
- Gestion centralisée des statistiques de session
- Tracking des max values (current, phaseCurrent, power, pwm, temperature, topSpeed)
- Fonctions de reset pour les statistiques

#### 14. **WheelTypeExtensions.kt** - **NOUVEAU** ✅
- Extensions pour convertir manufacturer vers WHEEL_TYPE

#### 15. **BleModule.kt** - **MIS À JOUR** ✅
- Ajout de `SessionManager` au module Koin

#### 16. **WheelDataComposeBridge.kt** - **DÉPRÉCIÉ** ⚠️
- Remplacé par `eucBleManager()` fonction Compose
- À supprimer une fois tous les écrans Compose migrés

#### 17. **SmartBmsScreen.kt** - **PARTIELLEMENT MIGRÉ** ⚠️
- BMS non disponible dans euc_ble_library
- Code commenté, à réimplémenter si support BMS ajouté

## 🚀 **Prochaines Étapes**

### Phase 1 : Migration des Composants Principaux (Priorité HAUTE)

#### 1. **MainPageAdapter.kt** (~71 références à WheelData) ⚠️
**Statut :** **À migrer** - Fichier complexe avec de nombreuses propriétés legacy

**Actions requises :**
- [ ] Injecter `EucBleManager` et `SessionManager` (déjà KoinComponent)
- [ ] Remplacer toutes les références à `WheelData.getInstance()` par `eucBleManager.eucData.value` ou `sessionManager`
- [ ] Adapter les propriétés non disponibles dans EUCData (bms, cpuTemp, imuTemp, currentAxis, speedAxis, protoVer, chargeTime, modeStr, torque, userDistance, voltageSag, wheelType, xAxis)
- [ ] Observer `eucBleManager.eucData` pour les mises à jour automatiques

**Propriétés disponibles dans EUCData :**
- `speed`, `voltage`, `current`, `temperature`, `batteryLevel`, `distance`, `power`, `model`, `rideTime`, `totalDistance`, `topSpeed`, `fanStatus`, `chargingStatus`, `cpuLoad`, `speedLimit`, `wheelDistance`, `angle`, `roll`, `phaseCurrent`, `serial`, `version`, `name`

**Propriétés à implémenter :**
- `averageSpeedDouble`, `averageRidingSpeedDouble` → calcul à partir de `totalDistance` et `rideTime`
- `rideTimeString`, `sleepTimerString`, `ridingTimeString`, `chargeTime` → formatage des propriétés
- `xAxis` → historique des données pour les graphiques

**Propriétés non disponibles (legacy uniquement) :**
- `bms` → BMS data (non supporté dans euc_ble_library)
- `cpuTemp`, `imuTemp` → capteurs de température supplémentaires
- `currentAxis`, `speedAxis` → données historiques pour graphiques
- `protoVer` → version du protocole
- `modeStr` → chaîne de mode
- `torque` → couple
- `userDistance` → distance utilisateur
- `voltageSag` → affaissement de tension
- `wheelType` → utiliser `manufacturer.toLegacyWheelType()`

#### 2. **LoggingService.kt** (~7 références à WheelData) ⚠️
**Statut :** **À migrer** - Utilise ParserLogToWheelData (déjà supprimé)

**Actions requises :**
- [ ] Injecter `EucBleManager`
- [ ] Remplacer `WheelData.getInstance().mac` par `eucBleManager.connectedDevice.value?.address`
- [ ] Remplacer `WheelData.getInstance().timeStamp` par timestamp actuel
- [ ] Implémenter un nouveau système de logging basé sur `EUCData`
- [ ] Supprimer la dépendance à ParserLogToWheelData

### Phase 2 : Nettoyage Final (Priorité MOYENNE)

#### 3. **Supprimer WheelData.kt wrapper**
- [ ] Une fois que tous les fichiers utilisent `EucBleManager` directement
- [ ] Vérifier avec `grep` qu'il n'y a plus de références à `WheelData.getInstance()`

#### 4. **Supprimer les dépendances legacy**
- [ ] Supprimer les références aux adapters (NinebotZAdapter, InMotionAdapter, etc.)
- [ ] Nettoyer les imports inutilisés

### Phase 3 : Fonctionnalités Manquantes (Priorité BASSE)

#### 5. **Implémenter les fonctionnalités non disponibles dans euc_ble_library**
- [ ] `wheelBeep()` → via `EucBleClient.sendCommand(CommandType.LIGHT_ON/OFF)` ou autre commande
- [ ] BMS support → à ajouter dans euc_ble_library ou implémenter côté application
- [ ] `voltageSag` → calcul ou support dans euc_ble_library
- [ ] `torque` → à ajouter dans euc_ble_library
- [ ] `cpuTemp`, `imuTemp` → capteurs supplémentaires

### Phase 2 : Migration des Écrans Settings (Priorité MOYENNE)

#### 2.1 **WheelScreen.kt** (~10 références à WheelData)
- [ ] Remplacer `WheelData.getInstance().wheelType` par `eucBleManager.connectedDevice.value?.manufacturer?.toLegacyWheelType()`
- [ ] Remplacer `WheelData.getInstance().speed` par `eucBleManager.eucData.value?.speed`
- [ ] Remplacer `WheelData.getInstance().model` par `eucBleManager.eucData.value?.model`

#### 2.2 **AlarmScreen.kt** (~5 références à WheelData)
- [ ] Remplacer `WheelData.getInstance().wheelType` par `eucBleManager.connectedDevice.value?.manufacturer?.toLegacyWheelType()`
- [ ] Remplacer `WheelData.getInstance().model` par `eucBleManager.eucData.value?.model`

#### 2.3 **TripScreen.kt** (~5 références à WheelData)
- [ ] Remplacer `WheelData.getInstance().resetMaxValues()` par reset des session statistics
- [ ] Remplacer `WheelData.getInstance().resetVoltageSag()` par no-op (non disponible dans EUCData)
- [ ] Remplacer `WheelData.getInstance().resetUserDistance()` par reset de la distance de départ

#### 2.4 **StartScreen.kt** (~3 références à WheelData)
- [ ] Remplacer `WheelData.getInstance()?.wheelType` par `eucBleManager.connectedDevice.value?.manufacturer?.toLegacyWheelType()`

#### 2.5 **LogScreen.kt** (~3 références à WheelData)
- [ ] Remplacer `WheelData.getInstance().isConnected` par `eucBleManager.isConnected.value`
- [ ] Remplacer `WheelData.getInstance().mac` par `eucBleManager.connectedDevice.value?.address`

### Phase 3 : Migration des Services et Utilitaires (Priorité MOYENNE)

#### 3.1 **LoggingService.kt** (~10 références à WheelData)
- [ ] Remplacer les références à `WheelData` par `eucBleManager`
- [ ] Supprimer `ParserLogToWheelData` (déjà supprimé)
- [ ] Adapter le logging pour utiliser `EUCData`

#### 3.2 **ElectroClub.kt** (~2 références à WheelData)
- [ ] Remplacer `WheelData.getInstance().isConnected` par `eucBleManager.isConnected.value`

#### 3.3 **DialogHelper.kt** (~5 références à WheelData)
- [ ] Remplacer les références à `WheelData` par `eucBleManager`

#### 3.4 **Alarms.kt** (~20 références à WheelData)
- [ ] Remplacer toutes les références à `WheelData` par `eucBleManager.eucData.value`
- [ ] Observer `eucBleManager.eucData` pour les alarmes en temps réel

#### 3.5 **AudioUtil.kt** (~2 références à WheelData)
- [ ] Remplacer les références à `WheelData` par `eucBleManager`

#### 3.6 **SomeUtil.kt** (~2 références à WheelData)
- [ ] Remplacer les références à `WheelData` par `eucBleManager`

#### 3.7 **NotificationUtil.kt** (~1 référence à WheelData - commentée)
- [ ] Vérifier et nettoyer si nécessaire

### Phase 4 : Nettoyage Final (Priorité BASSE)

#### 4.1 **Supprimer WheelData.kt**
- [ ] Une fois que tous les fichiers utilisent `EucBleManager` directement
- [ ] Vérifier avec `grep` qu'il n'y a plus de références à `WheelData`

#### 4.2 **Supprimer les dépendances non-FOSS**
- [ ] Vérifier que WearOS, Garmin, MiBand, Pebble sont supprimés
- [ ] Vérifier que Yandex Metrica est désactivé

## 📋 **Fichiers à Vérifier pour F-Droid**

### Déjà Supprimés (selon votre branche) :
- [x] WearOS (dossier companion/)
- [x] Garmin (GarminConnectIQ.kt)
- [x] Pebble (PebbleService.java)
- [x] MiBand
- [x] Yandex Metrica (commenté)

### À Vérifier :
- [ ] Vérifier les dépendances dans `build.gradle`
- [ ] Vérifier les permissions dans `AndroidManifest.xml`
- [ ] Vérifier les services dans `AndroidManifest.xml`

## 🔧 **Architecture Finale**

```
┌─────────────────────────────────────────────────────────────┐
│                    WheelLog (UI Pure - FOSS)                   │
├─────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────────────────────┐ │
│  │  EucBleManager   │    │      BleService                   │ │
│  │  (Singleton)     │    │  (Foreground Service)             │ │
│  └────────┬────────┘    └──────────────┬──────────────────┘ │
│           │                              │                    │
│           └──────────────────────────────┘                    │
│                          │                                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    UI Components                        │  │
│  │  MainActivity, ScanActivity, WheelView, Settings, etc.│  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                 euc_ble_library (Logique Métier)              │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐  │
│  │ EucBleClient │    │  BLEManager  │    │ EUCProtocol     │  │
│  └─────────────┘    └─────────────┘    │ (Kingsong, etc.) │  │
│                                          └─────────────────┘  │
│  ┌─────────────────┐    ┌─────────────────┐                  │
│  │  EUCDevice        │    │  EUCData          │                  │
│  │  (Device Info)    │    │  (Telemetry)      │                  │
│  └─────────────────┘    └─────────────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

## 📊 **Correspondance des Types**

### WheelData (Legacy) → EucBleManager (Nouveau)

| WheelData | EucBleManager | Type | Notes |
|-----------|---------------|------|-------|
| `speed` | `eucData.value?.speed` | Double? | km/h |
| `speedDouble` | `eucData.value?.speed` | Double? | km/h |
| `voltage` | `eucData.value?.voltage` | Double? | volts |
| `voltageDouble` | `eucData.value?.voltage` | Double? | volts |
| `current` | `eucData.value?.current` | Double? | amps |
| `currentDouble` | `eucData.value?.current` | Double? | amps |
| `temperature` | `eucData.value?.temperature` | Double? | °C |
| `power` | `eucData.value?.power` | Double? | watts |
| `powerDouble` | `eucData.value?.power` | Double? | watts |
| `pwm` | `eucData.value?.pwm` | Double? | 0-100 |
| `calculatedPwm` | `eucData.value?.pwm` | Double? | 0-100 |
| `batteryLevel` | `eucData.value?.batteryLevel` | Int? | 0-100 |
| `wheelType` | `connectedDevice.value?.manufacturer?.toLegacyWheelType()` | WHEEL_TYPE | |
| `model` | `eucData.value?.model` | String? | |
| `name` | `connectedDevice.value?.name` | String? | |
| `version` | `eucData.value?.firmwareVersion` | String? | |
| `serial` | `eucData.value?.serialNumber` | String? | |
| `mac` | `connectedDevice.value?.address` | String? | |
| `isConnected` | `isConnected.value` | Boolean | StateFlow |
| `distance` | `eucData.value?.distance` | Double? | km |
| `totalDistance` | `eucData.value?.totalDistance` | Double? | km |
| `wheelDistance` | `eucData.value?.wheelDistance` | Double? | km |
| `rideTime` | `eucData.value?.rideTime` | Long? | seconds |

### Conversion des Unités

**WheelData (Legacy) :**
- `speed` = valeur réelle × 100 (int)
- `voltage` = valeur réelle × 100 (int)
- `current` = valeur réelle × 100 (int)
- `temperature` = valeur réelle × 100 (int)
- `power` = valeur réelle × 100 (int)

**EUCData (Nouveau) :**
- `speed` = km/h (Double)
- `voltage` = volts (Double)
- `current` = amps (Double)
- `temperature` = °C (Double)
- `power` = watts (Double)

## 🎯 **Objectif Final**

1. **WheelLog = UI Pure** (100% Kotlin, pas de logique métier BLE)
2. **euc_ble_library = Toute la logique métier BLE**
3. **Aucune dépendance non-FOSS** (prêt pour F-Droid)
4. **Architecture réactive** (StateFlow, ViewModel)

## 📝 **Notes**

- Le wrapper `WheelData.kt` est **temporaire** et doit être supprimé une fois la migration terminée
- Les calculs (batteryPerKm, avgVoltagePerCell, etc.) doivent être déplacés vers des ViewModels ou des utilitaires
- Les commandes spécifiques (light, beep, etc.) doivent utiliser `EucBleClient.sendCommand(CommandType.*)`
- La détection du type de roue est maintenant gérée par `EucBleClient` automatiquement

## 🔗 **Ressources**

- [euc_ble_library Repository](https://github.com/Tritbool/euc_ble_library)
- [EucBleClient API](https://github.com/Tritbool/euc_ble_library/blob/main/euc-ble-core/src/main/kotlin/io/github/tritbool/euc/ble/EucBleClient.kt)
- [CommandType Enum](https://github.com/Tritbool/euc_ble_library/blob/main/euc-ble-core/src/main/kotlin/io/github/tritbool/euc/ble/protocols/EUCProtocol.kt)
