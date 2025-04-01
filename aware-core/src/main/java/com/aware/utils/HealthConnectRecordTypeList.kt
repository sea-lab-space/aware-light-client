package com.aware.utils

import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.Record
import kotlin.reflect.KClass

/**
 * Stores all Health Connect data types that support synchronization.
 * Used for permission requests and general synchronization logic.
 */
object HealthConnectRecordTypeList {

    // Supported Record Types for Synchronization
    val supportedRecordTypes: List<KClass<out Record>> = listOf(
        ActiveCaloriesBurnedRecord::class,
        BasalBodyTemperatureRecord::class,
        BasalMetabolicRateRecord::class,
        BloodGlucoseRecord::class,
        BloodPressureRecord::class,
        BodyFatRecord::class,
        BodyTemperatureRecord::class,
        BodyWaterMassRecord::class,
        BoneMassRecord::class,
        CervicalMucusRecord::class,
        ExerciseSessionRecord::class,
        DistanceRecord::class,
        ElevationGainedRecord::class,
        FloorsClimbedRecord::class,
        HeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        HeightRecord::class,
        HydrationRecord::class,
        IntermenstrualBleedingRecord::class,
        LeanBodyMassRecord::class,
        MenstruationFlowRecord::class,
        NutritionRecord::class,
        OvulationTestRecord::class,
        OxygenSaturationRecord::class,
        PowerRecord::class,
        RespiratoryRateRecord::class,
        RestingHeartRateRecord::class,
        SexualActivityRecord::class,
        SkinTemperatureRecord::class,
        SleepSessionRecord::class,
        SpeedRecord::class,
        StepsRecord::class,
        TotalCaloriesBurnedRecord::class,
        Vo2MaxRecord::class,
        WeightRecord::class,
        WheelchairPushesRecord::class
    )

}