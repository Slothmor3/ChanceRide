package com.example.chanceride.drivingrestrictions

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Main-thread, foreground-scoped source of actual AAOS UX restrictions.
 * start()/stop() belong in Activity.onStart()/onStop(). No gear or speed inference.
 * Neither an old snapshot nor a failed connection is treated as unrestricted.
 */
class CarDrivingRestrictionsMonitor(
    context: Context,
    private val onChanged: (DrivingRestrictionsState) -> Unit
) {
    private val context = context // Keep this Activity's display association for multi-display AAOS.
    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var generation = 0
    private var subscriptionGeneration = 0
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    var state = DrivingRestrictionsState()
        private set

    fun start() {
        checkMainThread()
        if (running) return
        running = true
        val token = ++generation
        publish(DrivingRestrictionsState(RestrictionConnection.CONNECTING, message = "차량 UX 규제 연결 중"))
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            publish(DrivingRestrictionsState(RestrictionConnection.UNSUPPORTED, message = "Android Automotive 환경이 아닙니다."))
            return
        }
        try {
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                // Always post: createCar may invoke the lifecycle callback before returning.
                main.post {
                    if (!running || token != generation) return@post
                    if (!ready) {
                        subscriptionGeneration++
                        manager = null
                        publish(DrivingRestrictionsState(RestrictionConnection.DISCONNECTED, message = "차량 서비스 연결 끊김 · 카테고리 변경 잠금"))
                    } else {
                        attach(connectedCar, token)
                    }
                }
            }
        } catch (error: RuntimeException) {
            fail(error)
        } catch (error: LinkageError) {
            publish(DrivingRestrictionsState(RestrictionConnection.UNSUPPORTED, message = "android.car 라이브러리를 사용할 수 없습니다."))
        }
    }

    private fun attach(connectedCar: Car, token: Int) {
        try {
            val subscription = ++subscriptionGeneration
            val previous = manager
            manager = null
            previous?.unregisterListener()
            val uxManager = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                ?: error("CarUxRestrictionsManager unavailable")
            manager = uxManager
            uxManager.registerListener { restrictions ->
                if (running && token == generation && subscription == subscriptionGeneration && manager === uxManager) accept(restrictions)
            }
            accept(uxManager.currentCarUxRestrictions)
        } catch (error: RuntimeException) {
            fail(error)
        }
    }

    private fun accept(restrictions: CarUxRestrictions) {
        val snapshot = UxRestrictions(
            requiresDistractionOptimization = restrictions.isRequiresDistractionOptimization,
            activeRestrictions = restrictions.activeRestrictions,
            maxRestrictedStringLength = restrictions.maxRestrictedStringLength,
            maxCumulativeContentItems = restrictions.maxCumulativeContentItems,
            maxContentDepth = restrictions.maxContentDepth
        )
        publish(DrivingRestrictionsState(
            RestrictionConnection.AVAILABLE,
            snapshot,
            if (snapshot.requiresDistractionOptimization) "플랫폼 주행 UX 규제 활성"
            else "플랫폼 주행 UX 규제 해제"
        ))
    }

    /** Reconnect after an error, without allowing any locally simulated unlocked state. */
    fun retry() {
        stop()
        start()
    }

    fun stop() {
        checkMainThread()
        running = false
        generation++ // Discard events queued by a previous connection.
        val previousManager = manager
        val previousCar = car
        manager = null
        car = null
        try {
            previousManager?.unregisterListener()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unregister failed after disconnect", error)
        }
        try {
            previousCar?.disconnect()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Disconnect failed", error)
        }
        publish(DrivingRestrictionsState(RestrictionConnection.STOPPED, message = "주행 규제 구독 중지 · 상태 미확인"))
    }

    private fun fail(error: RuntimeException) {
        Log.w(TAG, "UX restrictions unavailable", error)
        publish(DrivingRestrictionsState(RestrictionConnection.ERROR, message = "주행 규제 조회 실패 · ${error.javaClass.simpleName}"))
    }

    private fun publish(value: DrivingRestrictionsState) {
        state = value
        Log.d(TAG, "connection=${value.connection} requiresDO=${value.restrictions?.requiresDistractionOptimization} flags=${value.restrictions?.activeRestrictions}")
        onChanged(value)
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Use this monitor on the main thread" }
    }

    private companion object { const val TAG = "ChanceRideUxRestrictions" }
}
