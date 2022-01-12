package com.better.alarm.model

import android.annotation.TargetApi
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.better.alarm.logger.Logger
import com.better.alarm.presenter.AlarmsListActivity
import java.util.*

/** Created by Yuriy on 24.06.2017. */
interface AlarmSetter {

  fun removeRTCAlarm()

  fun setUpRTCAlarm(id: Int, typeName: String, calendar: Calendar)

  fun fireNow(id: Int, typeName: String)

  fun removeInexactAlarm(id: Int)

  fun setInexactAlarm(id: Int, calendar: Calendar)

  class AlarmSetterImpl(
      private val log: Logger,
      private val am: AlarmManager,
      private val mContext: Context
  ) : AlarmSetter {
    private val setAlarmStrategy: ISetAlarmStrategy
    private val TAG = "AlarmSetterImpl"

    init {
      this.setAlarmStrategy = initSetStrategyForVersion()
    }

    override fun removeRTCAlarm() {
        Log.d(TAG, "removeRTCAlarm()")
      log.debug { "Removed all alarms" }
      val pendingAlarm =
          PendingIntent.getBroadcast(
              mContext,
              pendingAlarmRequestCode,
              Intent(ACTION_FIRED).apply {
                // must be here, otherwise replace does not work
                setClass(mContext, AlarmsReceiver::class.java)
              },
              PendingIntent.FLAG_UPDATE_CURRENT)
      am.cancel(pendingAlarm)
    }

    override fun setUpRTCAlarm(id: Int, typeName: String, calendar: Calendar) {
      log.debug { "setUpRTCAlarm( Set $id ($typeName) on ${AlarmsScheduler.DATE_FORMAT.format(calendar.time)}" }
      val pendingAlarm =
          Intent(ACTION_FIRED)
              .apply {
                setClass(mContext, AlarmsReceiver::class.java)
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_TYPE, typeName)
              }
              .let {
                PendingIntent.getBroadcast(
                    mContext, pendingAlarmRequestCode, it, PendingIntent.FLAG_UPDATE_CURRENT)
              }

      setAlarmStrategy.setRTCAlarm(calendar, pendingAlarm)
    }

    override fun fireNow(id: Int, typeName: String) {
      val intent =
          Intent(ACTION_FIRED).apply {
            setClass(mContext, AlarmsReceiver::class.java)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TYPE, typeName)
          }
      mContext.sendBroadcast(intent)
    }

    override fun setInexactAlarm(id: Int, calendar: Calendar) {
      log.debug {
        "setInexactAlarm id: $id on ${AlarmsScheduler.DATE_FORMAT.format(calendar.time)}"
      }
      val pendingAlarm =
          Intent(ACTION_INEXACT_FIRED)
              .apply {
                setClass(mContext, AlarmsReceiver::class.java)
                putExtra(EXTRA_ID, id)
              }
              .let {
                PendingIntent.getBroadcast(mContext, id, it, PendingIntent.FLAG_UPDATE_CURRENT)
              }

      setAlarmStrategy.setInexactAlarm(calendar, pendingAlarm)
    }

    override fun removeInexactAlarm(id: Int) {
      log.trace { "removeInexactAlarm id: $id" }
      val pendingAlarm =
          PendingIntent.getBroadcast(
              mContext,
              id,
              Intent(ACTION_INEXACT_FIRED).apply {
                // must be here, otherwise replace does not work
                setClass(mContext, AlarmsReceiver::class.java)
              },
              PendingIntent.FLAG_UPDATE_CURRENT)
      am.cancel(pendingAlarm)
    }

    private fun initSetStrategyForVersion(): ISetAlarmStrategy {
      return when {
        Build.VERSION.SDK_INT >= 26 -> {
            Log.d(TAG, "OreoSetter()")
            OreoSetter()

        }
        Build.VERSION.SDK_INT >= 23 -> {
            Log.d(TAG, "MarshmallowSetter()")
            MarshmallowSetter()
        }
        Build.VERSION.SDK_INT >= 19 -> {
            Log.d(TAG, "KitKatSetter()")
            KitKatSetter()
        }
        else -> {
            Log.d(TAG, "IceCreamSetter()")
            IceCreamSetter()
        }
      }
    }

    private inner class IceCreamSetter : ISetAlarmStrategy {
      override fun setRTCAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "IceCreamSetter setRTCAlarm am.set(")
        am.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
      }

      override fun setInexactAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "IceCreamSetter setInexactAlarm am.set(")
        am.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
      }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private inner class KitKatSetter : ISetAlarmStrategy {
      override fun setRTCAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "KitKatSetter setRTCAlarm am.setExact(")
        am.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
      }

      override fun setInexactAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "KitKatSetter setInexactAlarm am.setExact(")
        am.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
      }
    }

    @TargetApi(23)
    private inner class MarshmallowSetter : ISetAlarmStrategy {
      override fun setRTCAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "MarshmallowSetter setRTCAlarm am.setExactAndAllowWhileIdle")
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
      }
    }

    /** 8.0 */
    @TargetApi(Build.VERSION_CODES.O)
    private inner class OreoSetter : ISetAlarmStrategy {
      override fun setRTCAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "OreoSetter setRTCAlarm am.setAlarmClock(")
        val pendingShowList =
            PendingIntent.getActivity(
                mContext,
                100500,
                Intent(mContext, AlarmsListActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT)
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingShowList), pendingIntent)
      }

      override fun setInexactAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
          Log.d(TAG, "setInexactAlarm am.setExactAndAllowWhileIdle(")
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
      }
    }

    private interface ISetAlarmStrategy {
      fun setRTCAlarm(calendar: Calendar, pendingIntent: PendingIntent)
      fun setInexactAlarm(calendar: Calendar, pendingIntent: PendingIntent) {
        setRTCAlarm(calendar, pendingIntent)
      }
    }

    companion object {
      private val pendingAlarmRequestCode = 0
    }
  }

  companion object {
    const val ACTION_FIRED = AlarmsScheduler.ACTION_FIRED
    const val ACTION_INEXACT_FIRED = AlarmsScheduler.ACTION_INEXACT_FIRED
    const val EXTRA_ID = AlarmsScheduler.EXTRA_ID
    const val EXTRA_TYPE = AlarmsScheduler.EXTRA_TYPE
  }
}
