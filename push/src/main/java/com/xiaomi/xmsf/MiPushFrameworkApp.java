package com.xiaomi.xmsf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.elvishew.xlog.XLog;
import com.oasisfeng.condom.CondomOptions;
import com.oasisfeng.condom.CondomProcess;
import com.xiaomi.channel.commonutils.logger.LoggerInterface;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.mipush.sdk.Logger;
import com.xiaomi.xmsf.push.control.PushControllerUtils;
import com.xiaomi.xmsf.push.control.XMOutbound;
import com.xiaomi.xmsf.push.notification.NotificationController;
import com.xiaomi.xmsf.push.service.MiuiPushActivateService;
import com.xiaomi.xmsf.utils.LogUtils;

import rx_activity_result2.RxActivityResult;
import top.trumeet.common.Constants;
import top.trumeet.common.push.PushServiceAccessibility;
import top.trumeet.mipush.provider.DatabaseUtils;

import static com.xiaomi.xmsf.push.control.PushControllerUtils.isAppMainProc;
import static com.xiaomi.xmsf.push.notification.NotificationController.CHANNEL_WARN;
import static top.trumeet.common.Constants.TAG_CONDOM;

public class MiPushFrameworkApp extends Application {
    private com.elvishew.xlog.Logger logger;

    private static final String MIPUSH_EXTRA = "mipush_extra";

    @Override
    public void attachBaseContext(Context context) {
        super.attachBaseContext(context);
        DatabaseUtils.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RxActivityResult.register(this);

        LogUtils.init(this);
        logger = XLog.tag(MiPushFrameworkApp.class.getSimpleName()).build();
        logger.i("App starts at " + System.currentTimeMillis());

        initMiSdkLogger();

        CondomOptions options = XMOutbound.create(this, TAG_CONDOM + "_PROCESS",
                false);
        CondomProcess.installExceptDefaultProcess(this, options);

        initPushLogger();

        PushControllerUtils.setAllEnable(true, this);

        long currentTimeMillis = System.currentTimeMillis();
        long lastStartupTime = getLastStartupTime();
        if (isAppMainProc(this)) {
            if ((currentTimeMillis - lastStartupTime > 300000 || currentTimeMillis - lastStartupTime < 0)) {
                setStartupTime(currentTimeMillis);
                MiuiPushActivateService.awakePushActivateService(PushControllerUtils.wrapContext(this)
                        , "com.xiaomi.xmsf.push.SCAN");
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationController.deleteOldNotificationChannelGroup(this);
        }

        try {
            if (!PushServiceAccessibility.isInDozeWhiteList(this)) {
                NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_WARN,
                            getString(R.string.wizard_title_doze_whitelist),
                            NotificationManager.IMPORTANCE_HIGH);

                    NotificationChannelGroup notificationChannelGroup = new NotificationChannelGroup(CHANNEL_WARN, CHANNEL_WARN);
                    manager.createNotificationChannelGroup(notificationChannelGroup);
                    channel.setGroup(notificationChannelGroup.getId());
                    manager.createNotificationChannel(channel);
                }

                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent()
                        .setComponent(new ComponentName(Constants.SERVICE_APP_NAME,
                                Constants.REMOVE_DOZE_COMPONENT_NAME)), PendingIntent.FLAG_UPDATE_CURRENT);
                Notification notification = new NotificationCompat.Builder(this,
                        CHANNEL_WARN)
                        .setContentInfo(getString(R.string.wizard_title_doze_whitelist))
                        .setContentTitle(getString(R.string.wizard_title_doze_whitelist))
                        .setContentText(getString(R.string.wizard_descr_doze_whitelist))
                        .setTicker(getString(R.string.wizard_descr_doze_whitelist))
                        .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(pendingIntent)
                        .setShowWhen(true)
                        .setAutoCancel(true)
                        .build();
                manager.notify(getClass().getSimpleName(), 100, notification);  // Use tag to avoid conflict with push notifications.
            }
        } catch (RuntimeException e) {
            logger.e(e.getMessage(), e);
        }


    }

    /**
     * The only purpose is to make sure Logger is created after the XLog is configured.
     */
    private LoggerInterface buildMiSDKLogger() {
        return new LoggerInterface() {
            private static final String TAG = "PushCore";
            private com.elvishew.xlog.Logger logger = XLog.tag(TAG).build();

            @Override
            public void setTag(String tag) {
                logger = XLog.tag(TAG + "-" + tag).build();
            }

            @Override
            public void log(String content, Throwable t) {
                if (t == null) {
                    logger.d(content);
                } else {
                    logger.d(content, t);
                }
            }

            @Override
            public void log(String content) {
                logger.d(content);
            }
        };
    }

    private void initPushLogger() {
        Logger.setLogger(PushControllerUtils.wrapContext(this), buildMiSDKLogger());
    }

    private void initMiSdkLogger() {
        MyLog.setLogger(buildMiSDKLogger());
    }


    private long getLastStartupTime() {
        return getDefaultPreferences().getLong("xmsf_startup", 0);
    }

    private boolean setStartupTime(long j) {
        return getDefaultPreferences().edit().putLong("xmsf_startup", j).commit();
    }

    private SharedPreferences getDefaultPreferences() {
        return getSharedPreferences(MIPUSH_EXTRA, 0);
    }

}
