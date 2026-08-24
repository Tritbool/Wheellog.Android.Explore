# Plan de Migration WheelLog vers euc_ble_library

## Objectif
Migrer WheelLog pour qu'il devienne un client UI pur, avec toute la logique métier BLE dans `euc_ble_library`.

## État Actuel
- **Problème principal**: Toute l'IHM repose sur `WheelData` (singleton legacy)
- **Dépendance**: `WheelData` est utilisé dans 100+ fichiers
- **Complexité**: La logique métier BLE est répartie entre `BluetoothService`, `WheelData`, et les adapters de marque

## Nouvelle Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                    WheelLog (UI Pure)                          │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐    ┌─────────────────────────────────┐ │
│  │  BleSessionState │    │      BleSessionViewModel          │ │
│  │  (Data Class)    │    │  (ViewModel + EucBleClient)       │ │
│  └────────┬────────┘    └──────────────┬──────────────────┘ │
│           │                              │                    │
│           └──────────────────────────────┘                    │
│                          │                                    │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    UI Components                        │  │
│  │  MainActivity, ScanActivity, MainPageAdapter, etc.    │  │
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

## Étapes de Migration

### Phase 1: Infrastructure (✅ Complété)
- [x] Créer `BleSessionState.kt` - État UI basé sur `EUCDevice`/`EUCData`
- [x] Créer `BleSessionViewModel.kt` - ViewModel avec `EucBleClient` et StateFlow
- [x] Créer `WheelDataExtensions.kt` - Couche de compatibilité pour la migration

### Phase 1.5: Dashboard Compose-First (✅ Complété)
- [x] Étendre `BleSessionState` avec `sessionMaxPwm`, `sessionBatteryLowest`, `sessionRidingTimeSec`
- [x] Créer `DashboardUiState` — source de vérité unique pour l'écran dashboard
- [x] Créer `DashboardMapper` — fonction pure `BleSessionState + AppConfig → DashboardUiState`
- [x] Créer `DashboardViewModel` — expose `StateFlow<DashboardUiState>` + `toggleDisplayMode()`
- [x] Créer `DashboardGauge` — Canvas Compose remplaçant `WheelView` (arcs, texte, animation)
- [x] Mettre à jour `MainPageScreen` pour utiliser `DashboardViewModel` + `DashboardGauge`
- [x] Activer le feature flag `AppConfig.useComposeUI = true` (fallback `WheelView` conservé)
- [x] Enregistrer `dashboardModule` dans Koin
- [x] Tests unitaires : `DashboardMapperTest`, `DashboardViewModelTest`
- [x] ADR : `docs/adr/001-compose-first-dashboard.md`, `docs/adr/002-koin-di.md`

**Flux de données dashboard :**
```
EUCData (BLE lib)
   └─► BleSessionViewModel (StateFlow<BleSessionState>)
              └─► DashboardViewModel (combine + map via DashboardMapper)
                        └─► StateFlow<DashboardUiState>
                                  └─► DashboardGauge  ──►  pixels on screen
```

**Suppression legacy (après stabilisation) :**
- Supprimer `WheelView.kt` et les layouts XML associés
- Supprimer `LegacyMainView()` de `MainScreen.kt`
- Supprimer `AppConfig.useComposeUI` flag

### Phase 2: Migration des Composants Principaux


#### 2.1 MainActivity.kt (Priorité: HAUTE)
**Dépendances actuelles:**
- `WheelData.getInstance()` pour l'état de connexion
- `BluetoothService` pour la gestion BLE
- Écoutes les broadcasts `ACTION_WHEEL_DATA_AVAILABLE`

**Actions requises:**
1. Injecter `BleSessionViewModel` au lieu de dépendre de `WheelData`
2. Remplacer les appels à `WheelData.getInstance()` par `viewModel.sessionState.value`
3. Remplacer les broadcasts par l'observation du StateFlow
4. Utiliser `viewModel.startScan()`, `viewModel.connect()`, etc.

**Fichiers à modifier:**
- `MainActivity.kt`
- `MainActivityCompose.kt` (si existe)

**Exemple de migration:**
```kotlin
// Avant
val speed = WheelData.getInstance().speed
val isConnected = WheelData.getInstance().isConnected

// Après
val sessionState = viewModel.sessionState.collectAsState()
val speed = sessionState.value.currentSpeed
val isConnected = sessionState.value.isConnected
```

#### 2.2 ScanActivity.kt (Priorité: HAUTE)
**Dépendances actuelles:**
- Utilise `BluetoothCentralManager` directement
- Gère la liste des appareils découverts
- Utilise `DeviceListAdapter` avec des données personnalisées

**Actions requises:**
1. Utiliser `BleSessionViewModel` pour le scan
2. Remplacer `BluetoothCentralManager` par `EucBleClient`
3. Adapter `DeviceListAdapter` pour utiliser `List<EUCDevice>`
4. Convertir les résultats de scan de la lib en format UI

**Fichiers à modifier:**
- `ScanActivity.kt`
- `DeviceListAdapter.kt`

#### 2.3 MainPageAdapter.kt (Priorité: HAUTE)
**Dépendances actuelles:**
- Utilise `WheelData.getInstance()` pour toutes les données
- Gère `WheelView` et les graphiques
- Dépend de `Constants.WHEEL_TYPE`

**Actions requises:**
1. Recevoir `BleSessionState` comme paramètre
2. Remplacer tous les appels à `WheelData` par les propriétés de `BleSessionState`
3. Utiliser les extensions de compatibilité pour les conversions

**Fichiers à modifier:**
- `MainPageAdapter.kt`
- `WheelView.kt`

### Phase 3: Migration des Écrans de Configuration

#### 3.1 WheelScreen.kt
**Dépendances:**
- `WheelData.getInstance().wheelType`
- `WheelData.getInstance().speed`
- `WheelData.getInstance().model`

**Actions:**
- Utiliser `sessionState.getWheelType()`, `sessionState.currentSpeed`, etc.
- Remplacer les conditions sur `wheelType` par des checks sur `manufacturer`

#### 3.2 AlarmScreen.kt
**Dépendances:**
- `WheelData.getInstance().wheelType`
- `WheelData.getInstance().model`

**Actions:**
- Utiliser `sessionState.getWheelType()` et `sessionState.deviceModel`

#### 3.3 TripScreen.kt
**Dépendances:**
- `WheelData.getInstance().resetMaxValues()`
- `WheelData.getInstance().resetVoltageSag()`
- `WheelData.getInstance().resetUserDistance()`

**Actions:**
- Appeler `viewModel.resetSessionStatistics()`
- Implémenter les fonctions de reset dans le ViewModel

#### 3.4 StartScreen.kt
**Dépendances:**
- `WheelData.getInstance()?.wheelType`

**Actions:**
- Utiliser `sessionState.getWheelType()`

#### 3.5 LogScreen.kt
**Dépendances:**
- `WheelData.getInstance().isConnected`
- `WheelData.getInstance().mac`

**Actions:**
- Utiliser `sessionState.isConnected` et `sessionState.getMac()`

### Phase 4: Services et Utilitaires

#### 4.1 BluetoothService.kt
**Dépendances:**
- Contient `WheelData.getInstance().detectWheel()`
- Gère la connexion BLE directement
- Utilise les adapters de marque (KingsongAdapter, etc.)

**Actions:**
- **Conserver temporairement** comme service Android technique
- Supprimer toute la logique métier BLE
- Remplacer par des appels à `EucBleClient`
- Supprimer les références aux adapters de marque

**À supprimer:**
- `readData()`
- `writeWheelCharacteristic()`
- Le `when (WheelData.getInstance().wheelType)`
- Tous les appels aux adapters de marque

#### 4.2 DialogHelper.kt
**Dépendances:**
- `WheelData.getInstance()` pour le modèle et le type

**Actions:**
- Passer `BleSessionState` comme paramètre aux fonctions
- Utiliser les propriétés de `BleSessionState`

#### 4.3 ElectroClub.kt
**Dépendances:**
- `WheelData.getInstance().isConnected`

**Actions:**
- Injecter `BleSessionViewModel` ou `BleSessionState`
- Utiliser `sessionState.isConnected`

#### 4.4 LoggingService.kt
**Dépendances:**
- `WheelData.getInstance()` pour plusieurs propriétés
- `ParserLogToWheelData`

**Actions:**
- Utiliser `BleSessionState` pour les données
- Créer un nouveau parser basé sur `EUCData`

#### 4.5 GearService.java
**Dépendances:**
- Plusieurs appels à `WheelData.getInstance()`

**Actions:**
- Injecter `BleSessionViewModel` ou observer `BleSessionState`
- Utiliser les extensions de compatibilité

#### 4.6 GarminConnectIQ.kt
**Dépendances:**
- `WheelData.getInstance()` pour les données de télémetry

**Actions:**
- Observer `BleSessionState` pour les données
- Utiliser les propriétés de compatibilité

### Phase 5: Composants Compose

#### 5.1 WheelDataComposeBridge.kt
**Actions:**
- **SUPPRIMER** - Remplacer par l'injection directe de `BleSessionViewModel`

#### 5.2 SmartBmsScreen.kt
**Dépendances:**
- `WheelData.getInstance()`

**Actions:**
- Utiliser `BleSessionState` passé comme paramètre

#### 5.3 ParamsListScreen.kt
**Dépendances:**
- `WheelDataComposeBridge.data`

**Actions:**
- Utiliser `BleSessionState` passé comme paramètre

#### 5.4 MainPageScreen.kt
**Dépendances:**
- `WheelDataComposeBridge.data`

**Actions:**
- Utiliser `BleSessionState` passé comme paramètre

### Phase 6: Nettoyage Final

#### 6.1 Supprimer WheelData.java
**Condition:** Quand plus aucun fichier ne l'utilise

**Actions:**
- Vérifier avec `grep` qu'il n'y a plus de références
- Supprimer le fichier
- Supprimer la règle ProGuard associée

#### 6.2 Supprimer les adapters de marque
**Fichiers à supprimer:**
- `KingsongAdapter.java`
- `NinebotAdapter.java`
- `NinebotZAdapter.java`
- `InMotionAdapter.java`
- `InmotionAdapterV2.java`
- `GotwayAdapter.java`
- `GotwayVirtualAdapter.java`
- `VeteranAdapter.java`
- `BaseAdapter.java`

**Condition:** Quand `BluetoothService` ne les utilise plus

#### 6.3 Supprimer BluetoothService.kt (partiellement)
**Actions:**
- Garder uniquement le service Android technique si nécessaire
- Supprimer toute la logique BLE métier
- Ou supprimer complètement si tout est géré par `BleSessionViewModel`

## Fichiers à Supprimer (Phase Finale)

### Adapters de Marque (10 fichiers)
- `utils/KingsongAdapter.java`
- `utils/NinebotAdapter.java`
- `utils/NinebotZAdapter.java`
- `utils/InMotionAdapter.java`
- `utils/InmotionAdapterV2.java`
- `utils/GotwayAdapter.java`
- `utils/GotwayVirtualAdapter.java`
- `utils/VeteranAdapter.java`
- `utils/BaseAdapter.java`
- `utils/ParserLogToWheelData.kt`

### Fichiers Legacy
- `WheelData.java`
- `WheelDataComposeBridge.kt`
- `BluetoothService.kt` (ou le vider complètement)

## Fichiers à Modifier (Par Ordre de Priorité)

### Priorité HAUTE (Bloquants pour l'IHM)
1. `MainActivity.kt`
2. `ScanActivity.kt`
3. `MainPageAdapter.kt`
4. `DeviceListAdapter.kt`

### Priorité MOYENNE (Fonctionnalités importantes)
5. `DialogHelper.kt`
6. `WheelScreen.kt`
7. `AlarmScreen.kt`
8. `TripScreen.kt`
9. `StartScreen.kt`
10. `LogScreen.kt`
11. `LoggingService.kt`
12. `ElectroClub.kt`

### Priorité BASSE (Services et utilitaires)
13. `BluetoothService.kt`
14. `GearService.java`
15. `GarminConnectIQ.kt`
16. `PebbleService.java`

### Priorité COMPOSE (Migration UI moderne)
17. `SmartBmsScreen.kt`
18. `ParamsListScreen.kt`
19. `MainPageScreen.kt`
20. `MainActivityCompose.kt`

## Correspondance des Types de Données

### WheelData → BleSessionState

| WheelData | BleSessionState | Type | Notes |
|-----------|-----------------|------|-------|
| `speed` | `currentSpeed` | Double | km/h |
| `voltage` | `currentVoltage` | Double | volts |
| `current` | `currentCurrent` | Double | amps |
| `temperature` | `currentTemperature` | Double | °C |
| `battery` | `batteryLevel` | Int | 0-100 |
| `power` | `currentPower` | Double | watts |
| `pwm` | `pwm` | Double? | 0-100 |
| `wheelType` | `getWheelType()` | WHEEL_TYPE | Extension |
| `model` | `deviceModel` | String | |
| `name` | `deviceName` | String | |
| `version` | `firmwareVersion` | String? | |
| `serial` | `serialNumber` | String? | |
| `isConnected` | `isConnected` | Boolean | |
| `mac` | `deviceAddress` | String | |
| `topSpeed` | `topSpeed` | Double? | |
| `distance` | `wheelDistance` | Double? | km |
| `totalDistance` | `totalDistance` | Double? | km |
| `rideTime` | `rideTime` | Long? | seconds |

### Conversion des Unités

**WheelData utilise des unités multipliées par 100:**
- `speed` = valeur réelle × 100
- `voltage` = valeur réelle × 100
- `current` = valeur réelle × 100
- `temperature` = valeur réelle × 100
- `power` = valeur réelle × 100

**EUCData utilise des unités standard:**
- `speed` = km/h (double)
- `voltage` = volts (double)
- `current` = amps (double)
- `temperature` = °C (double)
- `power` = watts (double)

**Extensions de compatibilité disponibles:**
- `EUCData.toLegacySpeed()`
- `EUCData.toLegacyVoltage()`
- `EUCData.toLegacyCurrent()`
- `EUCData.toLegacyTemperature()`
- `EUCData.toLegacyPower()`

## Configuration Koin

Ajouter au module Koin:

```kotlin
// Dans le module d'application
single { BleSessionViewModel(get()) }

// Ou comme ViewModel
viewModel { BleSessionViewModel(get()) }
```

## Intégration avec les Écrans Existants

### Pour les Activities
```kotlin
class MainActivity : AppCompatActivity() {
    private val viewModel: BleSessionViewModel by viewModel()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Observer l'état
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sessionState.collect { state ->
                    // Mettre à jour l'UI
                    updateUI(state)
                }
            }
        }
    }
}
```

### Pour les Écrans Compose
```kotlin
@Composable
fun WheelScreen(viewModel: BleSessionViewModel = viewModel()) {
    val sessionState by viewModel.sessionState.collectAsState()
    
    // Utiliser sessionState pour afficher les données
    Text(text = "Speed: ${sessionState.currentSpeed} km/h")
    Text(text = "Battery: ${sessionState.batteryLevel}%")
}
```

## Gestion des Permissions

Les nouvelles API BLE nécessitent des permissions:

```kotlin
@RequiresApi(Build.VERSION_CODES.M)
@RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
fun startScan() {
    viewModel.startScan()
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun connect(device: EUCDevice) {
    viewModel.connect(device)
}
```

## Tests de Migration

1. **Test de compilation**: Vérifier que tous les fichiers compilent
2. **Test de connexion**: Vérifier que la connexion BLE fonctionne
3. **Test de données**: Vérifier que les données sont correctement affichées
4. **Test de scan**: Vérifier que le scan découvre les appareils
5. **Test de compatibilité**: Vérifier que les extensions fonctionnent

## Rollback Plan

Si la migration échoue:
1. Conserver les anciens fichiers avec un suffixe `.legacy`
2. Utiliser des feature flags pour basculer entre l'ancien et le nouveau système
3. Implémenter une période de transition où les deux systèmes coexistent

## Estimation du Temps

- Phase 1 (Infrastructure): 2-4 heures ✅
- Phase 2 (Composants principaux): 8-12 heures
- Phase 3 (Écrans de configuration): 6-8 heures
- Phase 4 (Services): 4-6 heures
- Phase 5 (Compose): 4-6 heures
- Phase 6 (Nettoyage): 2-4 heures

**Total estimé:** 26-40 heures

## Recommandations

1. **Faire des commits fréquents** - Chaque fichier migré doit être commité
2. **Tester après chaque étape** - Vérifier que l'application compile et fonctionne
3. **Utiliser les extensions de compatibilité** - Pour faciliter la transition
4. **Documenter les changements** - Garder une trace de ce qui a été modifié
5. **Prioriser les composants bloquants** - MainActivity, ScanActivity, MainPageAdapter en premier
