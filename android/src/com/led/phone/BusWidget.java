package com.led.phone;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import org.json.*;
import java.io.*;

public class BusWidget extends AppWidgetProvider {
    static String read(Context context, String path) throws Exception {
        try (InputStream in=context.getAssets().open(path); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192]; int count;
            while ((count=in.read(bytes))!=-1) out.write(bytes,0,count);
            return out.toString("UTF-8");
        }
    }
    static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("com.led.phone.PhoneActivity",0);
    }
    static SharedPreferences state(Context context) {
        return context.getSharedPreferences("desktopPlayback",0);
    }
    static String route(Context context) { return settings(context).getString("lastRoute","3020A"); }
    static int direction(Context context) { return settings(context).getInt("lastDirection",0); }
    static JSONArray stations(Context context) throws Exception {
        JSONArray routes=new JSONArray(read(context,"phone/routes.json"));
        for (int i=0;i<routes.length();i++) {
            JSONObject route=routes.getJSONObject(i);
            if (route.getString("id").equals(route(context)))
                return route.getJSONObject("directions").getJSONObject(""+direction(context)).getJSONArray("stations");
        }
        throw new IOException("找不到路線資料");
    }
    static String key(Context context) { return route(context)+"/"+direction(context); }
    static int resource(Context context,String name,String type) {
        return context.getResources().getIdentifier(name,type,context.getPackageName());
    }
    static PendingIntent command(Context context,String action) {
        Intent intent=new Intent(context,WidgetPlaybackService.class).setAction(action);
        return PendingIntent.getForegroundService(context,action.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void refresh(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,BusWidget.class));
        if(ids.length==0)return;
        RemoteViews view=new RemoteViews(context.getPackageName(),resource(context,"bus_widget","layout"));
        SharedPreferences saved=state(context);
        boolean same=key(context).equals(saved.getString("key",""));
        int index=same?saved.getInt("index",0):0;
        boolean playing=same&&saved.getBoolean("playing",false);
        boolean automatic=same&&saved.getBoolean("automatic",false);
        String route=route(context);
        String label=route;
        try {
            JSONArray entries=new JSONArray(read(context,"phone/routes.json"));
            for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).getString("id").equals(route)){label=entries.getJSONObject(i).optString("label",route);break;}
        }catch(Exception ignored){}
        view.setTextViewText(resource(context,"widget_route","id"),"公車報站&導乘 · "+label+" · "+(direction(context)==0?"去程":"返程")+"  ›");
        try {
            JSONArray stops=stations(context);
            index=Math.max(0,Math.min(index,stops.length()-1));
            view.setTextViewText(resource(context,"widget_station","id"),stops.getString(index));
            view.setTextViewText(resource(context,"widget_status","id"),"第 "+(index+1)+" / "+stops.length()+" 站 · "+(same?saved.getString("status","就緒"):"就緒"));
            view.setBoolean(resource(context,"widget_previous","id"),"setEnabled",!playing&&!automatic&&index>0);
            view.setBoolean(resource(context,"widget_next","id"),"setEnabled",!playing&&!automatic&&index<stops.length()-1);
        } catch(Exception e) {
            view.setTextViewText(resource(context,"widget_station","id"),"請開啟公車報站&導乘選擇路線");
        }
        view.setTextViewText(resource(context,"widget_auto","id"),(playing||automatic)?"停止":"自動播報");
        view.setOnClickPendingIntent(resource(context,"widget_previous","id"),command(context,"previous"));
        view.setOnClickPendingIntent(resource(context,"widget_next","id"),command(context,"next"));
        view.setOnClickPendingIntent(resource(context,"widget_auto","id"),command(context,"toggle"));
        Intent open=new Intent(context,PhoneActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        view.setOnClickPendingIntent(resource(context,"widget_route","id"),PendingIntent.getActivity(context,90,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(ids,view);
    }
    public void onUpdate(Context context,AppWidgetManager manager,int[] ids) { refresh(context); }
    public void onReceive(Context context,Intent intent) {
        super.onReceive(context,intent);
        if("com.led.phone.REFRESH_WIDGET".equals(intent.getAction()))refresh(context);
    }
    public void onDisabled(Context context) {
        context.stopService(new Intent(context,WidgetPlaybackService.class));
    }
}

