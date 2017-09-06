package in.arjsna.voicerecorder.recording;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import in.arjsna.voicerecorder.AppConstants;
import in.arjsna.voicerecorder.R;
import in.arjsna.voicerecorder.activities.MainActivity;
import io.reactivex.functions.Consumer;
import java.util.Locale;

public class AudioRecordService extends Service {
  private static final String LOG_TAG = "RecordingService";

  private long mStartingTimeMillis = 0;
  private long mElapsedMillis = 0;
  private long mElapsedSeconds = 0;

  private AudioRecorder audioRecorder;
  private AudioRecordingDbmHandler handler;
  private ServiceBinder mIBinder;
  private NotificationManager mNotificationManager;
  private static int NOTIFY_ID = 100;
  private AudioRecorder.RecordTime lastUpdated;
  private boolean mIsClientBound = false;

  @Override public IBinder onBind(Intent intent) {
    mIsClientBound = true;
    return mIBinder;
  }

  public boolean isRecording() {
    return audioRecorder.isRecording();
  }

  @Override public void onCreate() {
    super.onCreate();
    mIBinder = new ServiceBinder();
    audioRecorder = new AudioRecorder();
    handler = new AudioRecordingDbmHandler();
    handler.addRecorder(audioRecorder);
    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
  }

  public AudioRecordingDbmHandler getHandler() {
    return handler;
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent.getAction() != null) {
      switch (intent.getAction()) {
        case AppConstants.ACTION_PAUSE:
          pauseRecord();
          break;
        case AppConstants.ACTION_RESUME:
          resumeRecord();
          break;
        case AppConstants.ACTION_STOP:
          if (!mIsClientBound) {
            stopSelf();
          }
      }
      if (mIsClientBound) {
        intent.putExtra(AppConstants.ACTION_IN_SERVICE, intent.getAction());
        intent.setAction(AppConstants.ACTION_IN_SERVICE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
      }
    } else {
      startRecording();
      startForeground(NOTIFY_ID, createNotification(new AudioRecorder.RecordTime()));
    }
    return START_STICKY;
  }

  @Override public void onDestroy() {
    super.onDestroy();
    if (isRecording()) {
      stopRecodingAndRelease();
    }
  }

  private void stopRecodingAndRelease() {
    audioRecorder.finishRecord();
    handler.stop();
  }

  public void startRecording() {
    audioRecorder.startRecord();
    handler.startDbmThread();
    audioRecorder.subscribeTimer(this::updateNotification);
  }

  public void subscribeForTimer(Consumer<AudioRecorder.RecordTime> timerConsumer) {
    audioRecorder.subscribeTimer(timerConsumer);
  }

  private void updateNotification(AudioRecorder.RecordTime recordTime) {
    mElapsedMillis = recordTime.millis;
    mNotificationManager.notify(NOTIFY_ID, createNotification(recordTime));
  }

  private Notification createNotification(AudioRecorder.RecordTime recordTime) {
    lastUpdated = recordTime;
    NotificationCompat.Builder mBuilder =
        new NotificationCompat.Builder(getApplicationContext()).setSmallIcon(
            R.drawable.ic_launcher_background)
            .setContentTitle(getString(R.string.notification_recording))
            .setContentText(String.format(Locale.getDefault(), "%02d:%02d:%02d", recordTime.hours,
                recordTime.minutes,
                recordTime.seconds))
            .addAction(R.drawable.ic_media_stop, getString(R.string.stop_recording),
                getActionIntent(AppConstants.ACTION_STOP))
            .setOngoing(true);
    if (audioRecorder.isPaused()) {
      mBuilder.addAction(R.drawable.ic_media_record, getString(R.string.resume_recording_button),
          getActionIntent(AppConstants.ACTION_RESUME));
    } else {
      mBuilder.addAction(R.drawable.ic_media_pause, getString(R.string.pause_recording_button),
          getActionIntent(AppConstants.ACTION_PAUSE));
    }
    mBuilder.setContentIntent(PendingIntent.getActivities(getApplicationContext(), 0,
        new Intent[] {new Intent(getApplicationContext(), MainActivity.class)}, 0));

    return mBuilder.build();
  }

  public void pauseRecord() {
    audioRecorder.pauseRecord();
    updateNotification(lastUpdated);
  }

  public boolean isPaused() {
    return audioRecorder.isPaused();
  }

  public void resumeRecord() {
    audioRecorder.resumeRecord();
  }

  public long getElapsedTime() {
    return mElapsedMillis;
  }

  public class ServiceBinder extends Binder {
    public AudioRecordService getService() {
      return AudioRecordService.this;
    }
  }

  private PendingIntent getActionIntent(String action) {
    Intent pauseIntent = new Intent(this, AudioRecordService.class);
    pauseIntent.setAction(action);
    return PendingIntent.getService(this, 100, pauseIntent, 0);
  }

  @Override public boolean onUnbind(Intent intent) {
    mIsClientBound = false;
    return true;
  }

  @Override public void onRebind(Intent intent) {
    mIsClientBound = true;
  }
}
