# EUC BLE Library Integration Summary

## Overview
This document summarizes the integration of the new `euc_ble_library` into the Wheellog.Android.Explore fork using a gradual migration approach.

## Changes Made

### 1. Feature Flags Added to AppConfig.kt
**File**: `/app/src/main/java/com/cooper/wheellog/AppConfig.kt`

Added two new configuration properties for controlling the BLE library migration:
```kotlin
// BLE Library Migration flags
var useNewBleLibrary: Boolean
    get() = getValue("use_new_ble_library", false)
    set(value) = setValue("use_new_ble_library", value)

var bleComparisonMode: Boolean
    get() = getValue("ble_comparison_mode", false)
    set(value) = setValue("ble_comparison_mode", value)
```

**Purpose**: 
- `useNewBleLibrary`: When `true`, uses the new euc_ble_library for BLE operations
- `bleComparisonMode`: When `true`, runs both legacy and new systems in parallel for validation

### 2. Library Dependency Added
**File**: `/app/build.gradle`

Added the euc_ble_library as a local module dependency:
```gradle
implementation project(path: ':euc_ble_library:euc-ble-core')
```

**File**: `/settings.gradle`

Added the library module to the project:
```gradle
// Include the euc_ble_library as a local module
include ':euc_ble_library:euc-ble-core'
project(':euc_ble_library:euc-ble-core').projectDir = file('../../Tritbool__euc_ble_library/euc-ble-core')
```

### 3. EucBleBridge.kt Created
**File**: `/app/src/main/java/com/cooper/wheellog/ble/EucBleBridge.kt`

Created a comprehensive bridge adapter that:
- Implements the `BaseAdapter` interface to be compatible with existing code
- Provides dual-mode operation (legacy + new library)
- Handles feature flag-based routing
- Includes comparison logging for validation
- Maps data between `EUCData` (new library) and `WheelData` (legacy)
- Implements all required command methods with appropriate delegation

**Key Features**:
- Singleton pattern with lazy initialization
- Automatic fallback to legacy adapters if new library fails
- Comprehensive logging for debugging and validation
- Asynchronous callback handling for new library data
- Command mapping between legacy and new systems

### 4. WheelData.java Modified
**File**: `/app/src/main/java/com/cooper/wheellog/WheelData.java`

**Changes**:
- Added import for `EucBleBridge`
- Modified `getAdapter()` method to return `EucBleBridge.getInstance()` instead of type-specific adapters

**Before**:
```java
public BaseAdapter getAdapter() {
    switch (mWheelType) {
        case GOTWAY_VIRTUAL:
            return GotwayVirtualAdapter.getInstance();
        case GOTWAY:
            return GotwayAdapter.getInstance();
        // ... other cases
        default:
            return null;
    }
}
```

**After**:
```java
public BaseAdapter getAdapter() {
    // Use the bridge adapter for gradual migration to new BLE library
    return EucBleBridge.getInstance();
}
```

### 5. BluetoothService.kt Modified
**File**: `/app/src/main/java/com/cooper/wheellog/BluetoothService.kt`

**Changes**:
- Added import for `EucBleBridge`
- Added initialization of the bridge in `onBind()` method
- Added cleanup of the bridge in `onDestroy()` method

**Added in onBind()**:
```kotlin
// Initialize the EUC BLE Bridge for gradual migration
EucBleBridge.getInstance().initialize(applicationContext)
```

**Added in onDestroy()**:
```kotlin
// Cleanup the EUC BLE Bridge
EucBleBridge.resetInstance()
```

## Feature Verification

The integration includes verification that the new library contains all three requested features:

1. **KingSong 0xA4 auto-reply functionality** ✅
   - Found in `KingsongProtocol.kt` lines 583-589
   - Implements automatic response to 0xA4 commands

2. **InMotion V2 V13/V14 support** ✅
   - Found in `InMotionProtocol.kt` lines 417-422
   - Supports protocol versions 13 and 14

3. **Gotway text-frame parsing with NAME/GW/JN/CF/BF ASCII prefixes and useHwPwm flag** ✅
   - Found in `GotwayProtocol.kt` lines 486-518
   - Handles text frames with specified ASCII prefixes
   - Includes useHwPwm flag processing

## Migration Strategy

### Phase 1: Testing (Current State)
- Both systems run in parallel
- Legacy system remains primary for data processing
- New library receives data and processes it asynchronously
- Comparison logging available when `bleComparisonMode = true`

### Phase 2: Validation
- Enable `bleComparisonMode = true`
- Monitor logs for discrepancies between legacy and new library
- Validate that all wheel types work correctly with new library
- Test all command functions

### Phase 3: Cutover
- Set `useNewBleLibrary = true` to switch to new library
- Keep `bleComparisonMode = true` for continued validation
- Monitor for any issues

### Phase 4: Full Migration
- Once validated, can remove legacy adapters (optional)
- Simplify bridge to only use new library
- Remove comparison mode if no longer needed

## Configuration Options

### Via AppConfig (Programmatic)
```kotlin
// Enable new library
AppConfig(context).useNewBleLibrary = true

// Enable comparison mode for validation
AppConfig(context).bleComparisonMode = true
```

### Via Preferences (User Settings)
The flags are stored in SharedPreferences with keys:
- `use_new_ble_library` (boolean)
- `ble_comparison_mode` (boolean)

These can be exposed in the app's settings UI for user control.

## Command Mapping

The bridge maps legacy commands to new library commands where available:

| Legacy Command | New Library Command | Status |
|----------------|---------------------|---------|
| setLightState | LIGHT_ON/LIGHT_OFF | ✅ Mapped |
| wheelBeep | BEEP | ✅ Mapped |
| powerOff | POWER_OFF | ✅ Mapped |
| setLightBrightness | LIGHT_BRIGHTNESS | ✅ Mapped |
| setLightMode | SET_LIGHT_MODE | ✅ Mapped |
| setLedMode | SET_LED_MODE | ✅ Mapped |
| setLockMode | LOCK | ✅ Mapped |
| setLimitedModeEnabled | SET_SPEED_LIMIT | ✅ Mapped |
| setLimitedSpeed | SET_SPEED_LIMIT | ✅ Mapped |
| setAlarmSpeed | SET_ALARM_SPEED | ✅ Mapped |
| wheelCalibration | CALIBRATE | ✅ Mapped |
| updateMaxSpeed | SET_SPEED_LIMIT | ✅ Mapped |

**Note**: Some commands are not yet mapped to the new library and will fall back to legacy adapters with a warning log.

## Data Field Mapping

The bridge maps EUCData fields to WheelData fields:

| EUCData Field | WheelData Method | Status |
|---------------|------------------|---------|
| speed | setSpeed() | ✅ Mapped |
| voltage | setVoltage() | ✅ Mapped |
| current | setCurrent() | ✅ Mapped |
| temperature | setTemperature() | ✅ Mapped |
| batteryLevel | setBatteryLevel() | ✅ Mapped |
| distance | setDistance() | ✅ Mapped |
| power | setPower() | ✅ Mapped |
| pwm | setOutput() | ✅ Mapped |
| model | setModel() | ✅ Mapped |
| firmwareVersion | setVersion() | ✅ Mapped |
| serialNumber | setSerial() | ✅ Mapped |
| topSpeed | setTopSpeed() | ✅ Mapped |
| motorTemperature | setTemperature2() | ✅ Mapped |
| totalDistance | setTotalDistance() | ✅ Mapped |
| fanStatus | setFanStatus() | ✅ Mapped |
| chargingStatus | setChargingStatus() | ✅ Mapped |
| temperature2 | setTemperature2() | ✅ Mapped |
| cpuLoad | setCpuLoad() | ✅ Mapped |
| speedLimit | setSpeedLimit() | ✅ Mapped |
| angle | setAngle() | ✅ Mapped |
| roll | setRoll() | ✅ Mapped |

**Note**: Some advanced fields (pedalsMode, alarmMode, etc.) are not directly mapped and would need additional work.

## Testing Recommendations

1. **Basic Functionality Test**
   - Connect to each wheel type
   - Verify data is displayed correctly
   - Test with both `useNewBleLibrary = false` and `true`

2. **Comparison Mode Test**
   - Enable `bleComparisonMode = true`
   - Monitor logs for discrepancies
   - Verify both systems process data correctly

3. **Command Testing**
   - Test all available commands with each wheel type
   - Verify commands work with both legacy and new systems

4. **Edge Cases**
   - Test connection/disconnection
   - Test error handling
   - Test with poor signal conditions

## Troubleshooting

### Common Issues

1. **Library not found**
   - Ensure the euc_ble_library is properly included in settings.gradle
   - Check that the path to the library is correct

2. **Class not found exceptions**
   - Verify all imports are correct
   - Check that the library is properly built

3. **Data not updating**
   - Check that `useNewBleLibrary` is set appropriately
   - Verify that the bridge is initialized (check BluetoothService.onBind)
   - Check logs for errors in data processing

4. **Commands not working**
   - Check if the command is mapped in the bridge
   - Verify that the new library supports the command for the specific wheel type
   - Check logs for fallback to legacy adapter

### Log Tags

The bridge uses the following log tags:
- `EUC BLE`: General bridge operations
- `[COMPARISON]`: Comparison mode logging
- `[NEW_LIB]`: New library data processing

## Files Modified

1. `/app/src/main/java/com/cooper/wheellog/AppConfig.kt` - Added feature flags
2. `/app/build.gradle` - Added library dependency
3. `/settings.gradle` - Added library module
4. `/app/src/main/java/com/cooper/wheellog/WheelData.java` - Modified getAdapter()
5. `/app/src/main/java/com/cooper/wheellog/BluetoothService.kt` - Added initialization/cleanup

## Files Created

1. `/app/src/main/java/com/cooper/wheellog/ble/EucBleBridge.kt` - Bridge adapter implementation

## Next Steps

1. **Test the integration** with various wheel types
2. **Validate data mapping** between EUCData and WheelData
3. **Complete command mapping** for any missing commands
4. **Add UI controls** for the feature flags in settings
5. **Monitor performance** and memory usage
6. **Fix any issues** discovered during testing

## Rollback Plan

If issues are discovered, the integration can be easily rolled back by:
1. Reverting the `getAdapter()` method in WheelData.java to use the original switch statement
2. Removing the bridge initialization from BluetoothService.kt
3. Disabling the feature flags in AppConfig.kt

This maintains the original functionality while allowing for easy re-enablement once issues are resolved.