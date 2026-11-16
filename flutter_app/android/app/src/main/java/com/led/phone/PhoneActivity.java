package com.led.phone;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.*;
import io.flutter.plugin.platform.*;

public class PhoneActivity extends FlutterActivity {
    static final int WHITE=0xffffffff;
    int bellState=0;
    MethodChannel nativeChannel;
    MediaPlayer player;
    MethodChannel.Result pendingPlay,pendingPhoto;
    final ArrayList<HardwareView> hardware=new ArrayList<>();
    android.content.SharedPreferences prefs(){return getSharedPreferences("com.led.phone.PhoneActivity",0);}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    byte[] bytes(String path)throws Exception{try(InputStream in=getAssets().open("flutter_assets/assets/"+path);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    int accent(){return Build.VERSION.SDK_INT>=31?getResources().getColor(prefs().getInt("appTheme",0)==1?android.R.color.system_accent1_600:android.R.color.system_accent1_200,null):0xffd0bcff;}
    Map<String,Object> snapshot(){stopService(new Intent(this,WidgetPlaybackService.class));BusWidget.refresh(this);Map<String,Object> data=new HashMap<>();data.put("preferences",prefs().getAll());data.put("accent",accent());data.put("index",BusWidget.key(this).equals(BusWidget.state(this).getString("key",""))?BusWidget.state(this).getInt("index",0):0);return data;}
    @Override public void configureFlutterEngine(FlutterEngine engine){super.configureFlutterEngine(engine);
        engine.getPlatformViewsController().getRegistry().registerViewFactory("bus/hardware",new PlatformViewFactory(StandardMessageCodec.INSTANCE){public PlatformView create(Context c,int id,Object args){HardwareView view=new HardwareView(((Number)((Map)args).get("kind")).intValue());hardware.add(view);if(view.kind>0){view.setContentDescription(view.kind==1?"按下一般下車鈴":"按下卡農下車鈴");view.setOnClickListener(v->{if(nativeChannel!=null&&pendingPlay==null)nativeChannel.invokeMethod("hardwareTap",view.kind);});}return new PlatformView(){public View getView(){return view;}public void dispose(){hardware.remove(view);}};}});
        (nativeChannel=new MethodChannel(engine.getDartExecutor().getBinaryMessenger(),"com.led.phone/bridge")).setMethodCallHandler((call,result)->{
            try{switch(call.method){
                case "init":{Map<String,Object> data=snapshot();for(String key:new String[]{"routes","catalog","audio","external"})data.put(key,new String(bytes("phone/"+key+".json"),"UTF-8"));result.success(data);break;}
                case "snapshot":result.success(snapshot());break;
                case "accent":result.success(accent());break;
                case "asset":try{result.success(bytes((String)call.arguments));}catch(Exception e){result.success(null);}break;
                case "save":{Map a=(Map)call.arguments;String k=(String)a.get("key");Object v=a.get("value");android.content.SharedPreferences.Editor edit=prefs().edit();if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Number)edit.putInt(k,((Number)v).intValue());else edit.putString(k,(String)v);edit.apply();BusWidget.refresh(this);result.success(null);break;}
                case "state":{Map a=(Map)call.arguments;prefs().edit().putString("lastRoute",(String)a.get("route")).putInt("lastDirection",((Number)a.get("direction")).intValue()).apply();BusWidget.state(this).edit().putString("key",BusWidget.key(this)).putInt("index",((Number)a.get("index")).intValue()).putBoolean("playing",false).putBoolean("automatic",false).apply();BusWidget.refresh(this);result.success(null);break;}
                case "play":stopPlayer();pendingPlay=result;try{File f=new File(getCacheDir(),"flutter-audio");try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes((String)call.arguments));}player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(f.getPath());player.setOnCompletionListener(p->finishPlay(true));player.setOnErrorListener((p,w,e)->{finishPlay(false);return true;});player.setOnPreparedListener(p->p.start());player.prepareAsync();}catch(Exception e){finishPlay(false);}break;
                case "stop":stopPlayer();result.success(null);break;
                case "bellState":bellState=((Number)call.arguments).intValue();for(HardwareView v:hardware)v.invalidate();result.success(null);break;
                case "keepScreen":if(Boolean.TRUE.equals(call.arguments))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);result.success(null);break;
                case "background":result.success(prefs().getBoolean("customBackground",false)?background():null);break;
                case "pickBackground":if(pendingPhoto!=null){result.error("busy","照片選單已開啟",null);break;}pendingPhoto=result;Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,501);break;
                default:result.notImplemented();
            }}catch(Exception e){result.error("native",e.getMessage(),null);}
        });
    }
    byte[] background()throws Exception{File f=new File(getFilesDir(),"app-background.jpg");if(!f.exists())return null;try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    void finishPlay(boolean ok){if(player!=null){player.release();player=null;}if(pendingPlay!=null){MethodChannel.Result r=pendingPlay;pendingPlay=null;r.success(ok);}}
    void stopPlayer(){finishPlay(false);}
    @Override protected void onActivityResult(int request,int status,Intent data){super.onActivityResult(request,status,data);if(request!=501||pendingPhoto==null)return;MethodChannel.Result result=pendingPhoto;pendingPhoto=null;if(status!=RESULT_OK||data==null){result.success(null);return;}try{BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(data.getData())){BitmapFactory.decodeStream(in,null,opt);}if(opt.outWidth<=0)throw new IOException("圖片格式不支援");opt.inSampleSize=1;while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>2048)opt.inSampleSize*=2;opt.inJustDecodeBounds=false;Bitmap photo;try(InputStream in=getContentResolver().openInputStream(data.getData())){photo=BitmapFactory.decodeStream(in,null,opt);}int orientation=1;try(InputStream in=getContentResolver().openInputStream(data.getData())){orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}Matrix m=new Matrix();if(orientation==3)m.postRotate(180);else if(orientation==6)m.postRotate(90);else if(orientation==8)m.postRotate(270);else if(orientation==2)m.postScale(-1,1);if(!m.isIdentity())photo=Bitmap.createBitmap(photo,0,0,photo.getWidth(),photo.getHeight(),m,true);try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"app-background.jpg"))){photo.compress(Bitmap.CompressFormat.JPEG,90,out);}prefs().edit().putBoolean("customBackground",true).apply();result.success(background());}catch(Exception e){result.error("photo",e.getMessage(),null);}}
    @Override protected void onPause(){stopPlayer();super.onPause();}
    @Override protected void onDestroy(){stopPlayer();super.onDestroy();}
    class HardwareView extends View {
        final int kind;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Canvas model;

        HardwareView(int kind) {
            super(PhoneActivity.this);
            this.kind=kind;
        }
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }
        void gradientBox(float left,float top,float right,float bottom,float radius,int... colors) {
            paint.setShader(new LinearGradient(left,top,right,bottom,colors,null,Shader.TileMode.CLAMP));
            model.drawRoundRect(left,top,right,bottom,radius,radius,paint);
            paint.setShader(null);
        }
        void ellipse(float left,float top,float right,float bottom,int... colors) {
            paint.setShader(new LinearGradient(left,top,right,bottom,colors,null,Shader.TileMode.CLAMP));
            model.drawOval(left,top,right,bottom,paint);
            paint.setShader(null);
        }
        void lettering(String text,float x,float baseline,float size,int color) {
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setTextAlign(Paint.Align.CENTER);
            model.drawText(text,x,baseline,paint);
        }
        void glow(float x,float y,float radius,int color) {
            paint.setShader(new RadialGradient(x,y,radius,new int[]{color,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
            model.drawCircle(x,y,radius,paint);
            paint.setShader(null);
        }
        void screw(float x,float y) {
            ellipse(x-9,y-9,x+9,y+9,0xff080c10,0xff67747c,0xff10151b);
            ellipse(x-6,y-6,x+6,y+6,0xffeef4f7,0xff77858c,0xffd0dce0);
            paint.setStrokeWidth(2);
            paint.setColor(0xff38434a);
            model.drawLine(x-4,y-1,x+4,y+1,paint);
        }
        void wheelchair(float x,float y,float scale,int color) {
            model.save();
            model.translate(x,y);
            model.scale(scale,scale);
            paint.setColor(color);
            model.drawCircle(21,9,6,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);
            paint.setStrokeCap(Paint.Cap.ROUND);
            model.drawArc(new RectF(3,26,39,62),35,285,false,paint);
            Path person=new Path();
            person.moveTo(20,22);
            person.lineTo(20,39);
            person.lineTo(41,39);
            person.lineTo(52,60);
            model.drawPath(person,paint);
            model.drawLine(21,27,37,27,paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
            model.restore();
        }
        void drawIndicator() {
            boolean blue=bellState==1, red=bellState==2;
            if(blue)glow(150,235,154,0x553c77ff);
            if(red)glow(150,144,140,0x55ff342b);
            ellipse(11,64,289,342,0xff171a1f,0xff040507);
            ellipse(9,54,291,336,0xff6b7179,0xff171d23,0xff07090c);
            ellipse(18,62,282,326,0xff10141b,0xff303943,0xff10141a);
            ellipse(28,72,272,316,0xff080a10,0xff030409);
            Path face=new Path();
            face.addCircle(150,194,114,Path.Direction.CW);
            model.save();
            model.clipPath(face);
            gradientBox(36,80,264,194,0,red?0xffff9983:0xff73292f,red?0xffff291d:0xff351017,red?0xffca080c:0xff5c1920);
            gradientBox(36,194,264,308,0,blue?0xff327cff:0xff161c56,blue?0xff0832db:0xff080e30,blue?0xff001a9e:0xff121633);
            if(red)glow(150,159,92,0x99ffb16f);
            if(blue)glow(150,227,105,0x995aabff);
            wheelchair(126,91,.85f,red?0xff302632:0xff171d2e);
            paint.setColor(0xff0b0e18);
            model.drawRect(35,187,265,193,paint);
            lettering("乘 客 區",150,225,17,blue?0xffdceaff:0xff737a98);
            lettering("下站停車",150,269,31,blue?WHITE:0xffa3abc4);
            paint.setShader(new LinearGradient(45,82,240,205,0x44ffffff,0x00ffffff,Shader.TileMode.CLAMP));
            Path shine=new Path();
            shine.moveTo(35,80);shine.lineTo(268,80);shine.lineTo(235,112);shine.lineTo(35,186);shine.close();
            model.drawPath(shine,paint);
            paint.setShader(null);
            model.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(0xff596370);
            model.drawCircle(150,194,116,paint);
            paint.setStyle(Paint.Style.FILL);
        }

        void drawNormalButton() {
            boolean lit=bellState==1;
            // Mounting ears extend beyond the compact cast-metal shell.
            ellipse(115,57,187,107,0xff0a0d10,0xffa9b4b4,0xff343b3c);
            ellipse(115,315,187,372,0xff383f3e,0xffb8c1b6,0xff151b1c);
            ellipse(135,66,165,90,0xff080b0d,0xff424a4b);
            ellipse(135,340,165,364,0xff171c1c,0xff58625e);
            gradientBox(39,91,278,344,59,0xff0b0e0f,0xff4e5754,0xff151b1c);
            gradientBox(26,82,263,334,59,0xffd1d6ca,0xff777e76,0xffdce0ce,0xff555c53);
            Path shell=new Path();
            shell.addRoundRect(new RectF(30,87,259,330),55,55,Path.Direction.CW);
            model.save();
            model.clipPath(shell);
            paint.setShader(new LinearGradient(30,140,259,320,
                new int[]{0xffd5d9cb,0xff72796f,0xffb2b9a6,0xff646b61},null,Shader.TileMode.CLAMP));
            model.drawRect(30,87,259,330,paint);
            paint.setShader(null);
            java.util.Random texture=new java.util.Random(2241);
            for(int i=0;i<2300;i++){
                float x=30+texture.nextFloat()*229;
                float y=87+texture.nextFloat()*243;
                paint.setColor(i%3==0?0x887c8479:i%3==1?0x9970786d:0x99edf0df);
                model.drawCircle(x,y,.4f+texture.nextFloat()*.8f,paint);
            }
            // A narrow front rim catches the light along the cast housing.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setShader(new LinearGradient(28,185,259,327,0xffe7e9da,0xff333c35,Shader.TileMode.CLAMP));
            model.drawRoundRect(30,87,259,330,55,55,paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            model.restore();

            Path lens=new Path();
            lens.moveTo(31,204);
            lens.lineTo(34,137);
            lens.cubicTo(37,92,56,82,143,80);
            lens.cubicTo(229,80,255,91,260,136);
            lens.lineTo(264,204);
            lens.close();
            paint.setColor(0xff050913);
            model.drawPath(lens,paint);
            model.save();model.clipPath(lens);
            paint.setShader(new LinearGradient(35,84,258,203,
                new int[]{0xff070b1a,0xff13182e,0xff02050e,0xff161a34},null,Shader.TileMode.CLAMP));
            model.drawPath(lens,paint);
            paint.setShader(null);
            if(lit){
                glow(148,156,120,0x99d62837);
                for(float[] point:new float[][]{{92,159},{132,124},{187,145},{155,185}}){
                    glow(point[0],point[1],22,0xc9ff2733);
                    ellipse(point[0]-3,point[1]-3,point[0]+3,point[1]+3,0xffffecce,0xffff8d61);
                }
            }
            paint.setShader(new LinearGradient(43,85,204,184,0x552b3d6a,0x00142543,Shader.TileMode.CLAMP));
            Path reflection=new Path();
            reflection.moveTo(39,138);reflection.cubicTo(42,95,64,88,145,86);
            reflection.cubicTo(196,86,217,90,238,102);
            reflection.cubicTo(171,92,99,106,39,138);reflection.close();
            model.drawPath(reflection,paint);paint.setShader(null);
            paint.setColor(0x447784a9);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.4f);
            model.drawPath(lens,paint);paint.setStyle(Paint.Style.FILL);
            model.restore();
            paint.setColor(0xff161c1a);model.drawRect(31,204,264,208,paint);

            // The pale yellow-green key sits in a black recessed opening.
            gradientBox(76,239,225,316,16,0xff252c23,0xff030604,0xffd3d8ad);
            float depression=isPressed()?3:0;
            gradientBox(80,243+depression,220,312+depression,13,0xffdce1ac,0xff8f975c,0xff6d7545);
            gradientBox(84,246+depression,216,305+depression,10,0xffd6dba5,0xffb8c18a,0xff909c61);
            paint.setColor(0x99f6f4ce);
            model.drawRoundRect(91,247+depression,209,250+depression,1.5f,1.5f,paint);
            paint.setColor(0x44747f4b);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);
            model.drawRoundRect(85,247+depression,215,305+depression,10,10,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        void drawCanonButton() {
            gradientBox(70,24,238,382,17,0xff080b10,0xff07090b);
            gradientBox(61,13,229,370,17,0xffffed69,0xffecc11b,0xffb78304);
            gradientBox(67,19,222,362,13,0xffffe342,0xffffcf0a,0xffe2ac05);
            screw(145,47);screw(145,334);
            gradientBox(79,116,211,263,11,0xff8f6e02,0xffc58c00,0xffffed59);
            float depression=isPressed()?4:0;
            gradientBox(83,120+depression,207,258+depression,9,0xff3a75ec,0xff0739a0,0xff02266d);
            gradientBox(88,124+depression,202,247+depression,7,0xff3269dc,0xff164aaa);
            wheelchair(111,137+depression,1.24f,0xfff1f6ff);
            paint.setColor(0x44ffffff);
            model.drawRoundRect(93,127+depression,196,131+depression,2,2,paint);
            if(bellState==2)glow(146,184,78,0x33ff4550);
        }
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            model=canvas;
            float scale=Math.min(getWidth()/300f,getHeight()/400f);
            model.save();
            model.translate((getWidth()-300*scale)/2,(getHeight()-400*scale)/2);
            model.scale(scale,scale);
            if(kind==0)drawIndicator();
            else if(kind==1)drawNormalButton();
            else drawCanonButton();
            model.restore();
        }
    }

}
