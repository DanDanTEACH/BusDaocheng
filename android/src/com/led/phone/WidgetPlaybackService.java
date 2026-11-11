package com.led.phone;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.*;
import java.io.*;

public class WidgetPlaybackService extends Service {
    final Handler handler=new Handler(Looper.getMainLooper());
    JSONArray stops;
    JSONObject audioFiles;
    MediaPlayer player;
    boolean playing,automatic;
    int index;
    String routeKey="";
    PowerManager.WakeLock wakeLock;
    final Runnable advance=()->{
        if(!automatic||playing)return;
        if(index>=stops.length()-1){finishAutomatic();return;}
        index++;announce();
    };
    public void onCreate() {
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("desktop_playback","桌面報站播放",NotificationManager.IMPORTANCE_LOW));
        wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DaoCheng:DesktopPlayback");
        wakeLock.setReferenceCounted(false);
    }
    Notification notification(String status) {
        Intent open=new Intent(this,PhoneActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,91,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"desktop_playback")
            .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("公車報站&導乘桌面報站")
            .setContentText(status).setContentIntent(pending).setOngoing(playing||automatic)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause,"停止",BusWidget.command(this,"stop")).build()).build();
    }
    public int onStartCommand(Intent intent,int flags,int startId) {
        startForeground(13,notification("準備播放"));
        try {
            String key=BusWidget.key(this);
            if(!key.equals(routeKey)) {
                cancel();routeKey=key;stops=BusWidget.stations(this);
                index=key.equals(BusWidget.state(this).getString("key",""))?BusWidget.state(this).getInt("index",0):0;
                index=Math.max(0,Math.min(index,stops.length()-1));
                audioFiles=new JSONObject(BusWidget.read(this,"phone/audio.json"));
            }
            String action=intent==null?"stop":intent.getAction();
            if("stop".equals(action)||("toggle".equals(action)&&(playing||automatic))) {
                cancel();publish("已停止");
            } else if("toggle".equals(action)) {
                automatic=true;wakeLock.acquire();announce();
            } else if(!playing&&!automatic) {
                if("next".equals(action)&&index<stops.length()-1){index++;announce();}
                else if("previous".equals(action)){index=Math.max(0,index-1);publish("就緒");}
            }
            retireIfIdle();
        } catch(Exception e) {
            cancel();publish("無法讀取報站資料");retireIfIdle();
        }
        return START_NOT_STICKY;
    }
    void publish(String status) {
        BusWidget.state(this).edit().putString("key",routeKey).putInt("index",index)
            .putBoolean("playing",playing).putBoolean("automatic",automatic).putString("status",status).apply();
        BusWidget.refresh(this);
        if(playing||automatic)getSystemService(NotificationManager.class).notify(13,notification(status));
    }
    void announce() {
        try {
            String name=stops.getString(index);
            String file=audioFiles.optString(name);
            if(file.isEmpty()){complete("缺音，繼續下一站");return;}
            playing=true;
            if(!wakeLock.isHeld())wakeLock.acquire();
            publish("播報中");
            File local=new File(getCacheDir(),"widget-announcement.mp3");
            try(InputStream in=getAssets().open("phone/audio/"+file);FileOutputStream out=new FileOutputStream(local)) {
                byte[] bytes=new byte[8192];int count;
                while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            }
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(local.getPath());
            player.setOnCompletionListener(p->complete("播報完成"));
            player.setOnErrorListener((p,a,b)->{complete("播放失敗，繼續下一站");return true;});
            player.prepare();player.start();
        } catch(Exception e){complete("缺音或音檔無法播放");}
    }
    void releasePlayer() { if(player!=null){player.release();player=null;}playing=false; }
    void complete(String status) {
        releasePlayer();
        if(automatic&&index<stops.length()-1) {
            int seconds=BusWidget.settings(this).getInt("autoDelaySeconds",1);
            publish("等待 "+seconds+" 秒 · "+status);
            handler.removeCallbacks(advance);handler.postDelayed(advance,seconds*1000L);
        } else {automatic=false;publish(status);retireIfIdle();}
    }
    void finishAutomatic(){automatic=false;publish("已到終點");retireIfIdle();}
    void cancel(){handler.removeCallbacksAndMessages(null);automatic=false;releasePlayer();if(wakeLock.isHeld())wakeLock.release();}
    void retireIfIdle(){if(!playing&&!automatic){if(wakeLock.isHeld())wakeLock.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}}
    public void onDestroy(){cancel();publish("已停止");super.onDestroy();}
    public IBinder onBind(Intent intent){return null;}
}
