package com.cooper.wheellog.di

import com.cooper.wheellog.ble.EucBleManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val bleModule = module {
    single { EucBleManager(androidContext()) }
}
