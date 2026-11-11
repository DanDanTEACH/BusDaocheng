package com.led.phone;

import android.app.*;
import android.os.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PhoneActivity extends Activity {
    static final int WHITE=0xffffffff, PURPLE=0xffbd629d, BLUE=0xff6aadbd;
    static final String[] IDS={"3020A","2230A","3022","EB0111003500","EB0111003520","EB0111005500","EB0111005520","EB0112002700","EB0114002000","EB0100012900","EB0151000600","EB0151000700","EB0151000800","EB0151000820","EB0151000900","EB0151000920","EB0151001400","EB0151002100","EB0151002200","EB0151002300","EB0151002500","EB0151002600","EB0151002800","EB0151003600","EB0100021600","EB0100021800","EB0100021820","EB0100021830","EB0152001600","EB0152002000","EB0152003200","EB0152000B00","EB0100023000","EB0153000600","EB0153001500","EB0100026590","EB0100026520","EB0161000900","EB0100028800","EB0100052900","EB0100055000","EB0100055100","EB0100060200","EB0400073100","EB0400083900"};
    static final String[] LABELS={"302","223","302 區間車","紅35","紅35區","紅55","紅55區","藍27","棕20","129","小6","小7","小8","小8區","小9(台灣好行-北投竹子湖)","小9區","小14","小21","小22","小23","小25","小26","小28","小36","216","218","218區","218直","內科通勤專車16","內科通勤專車20","南軟通勤專車北投線","內科快線2","230","市民小巴6","市民小巴15","265經明德路","265區","承德幹線","288","529","550","551","602","731","839"};
    static final String[] MAPS={"3020A","2230A","3022","11350","11352","11550","11552","12270","14200","1290","21060","21070","21080","21082","21090","21092","21140","21210","21220","21230","21250","21260","21280","21360","2162","2180","2182","2183","22016A","22020A","22032A","22110C","2300","23060","23150","2659","2652A","2660","2882","5290","5500","5510","6020B","7310","8390"};
    final Handler handler=new Handler(Looper.getMainLooper());
    JSONObject database=new JSONObject(), audioFiles=new JSONObject();
    android.media.MediaPlayer player;
    boolean speaking=false, autoAnnouncing=false;
    Button autoButton,externalButton,bellButton;
    FrameLayout bellOverlay;
    Dialog bellDialog;
    int bellState=0;
    boolean bellAudioPlaying=false;
    HardwareView indicatorView,normalBellView,canonBellView;
    TextView bellStatus;

    int autoDelaySeconds=1;
    final Runnable autoAdvance = new Runnable() {
        public void run() {
            if (!autoAnnouncing || !playbackActive || !visible || speaking) return;
            if (index >= stops.size()-1) {
                setAutomatic(false);
                return;
            }
            move(1);
        }
    };
    void setAutomatic(boolean enabled) {
        autoAnnouncing=enabled;
        handler.removeCallbacks(autoAdvance);
        if (autoButton!=null) autoButton.setText(enabled?"停止":"自動播報");
        if (enabled && !speaking) announce();
        if(showing)refresh();
    }
    void showAutomaticSettings() {
        LinearLayout content=column();
        content.setPadding(dp(20),dp(8),dp(20),dp(8));
        content.addView(text("每站音檔完整播完後，等待幾秒再切下一站？",16,WHITE));
        EditText secondsInput=new EditText(this);
        secondsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        secondsInput.setSingleLine(true);
        secondsInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        secondsInput.setHint("0～3600 秒");
        secondsInput.setText(String.valueOf(getPreferences(0).getInt("autoDelaySeconds",1)));
        secondsInput.selectAll();
        content.addView(secondsInput);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("自動播報設定")
            .setView(content)
            .setPositiveButton("確認",null)
            .setNegativeButton("取消",null)
            .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int seconds;
            try { seconds=Integer.parseInt(secondsInput.getText().toString().trim()); }
            catch(NumberFormatException e){secondsInput.setError("請輸入 0～3600 的整數");return;}
            if(seconds<0||seconds>3600){secondsInput.setError("請輸入 0～3600 的整數");return;}
            autoDelaySeconds=seconds;
            getPreferences(0).edit().putInt("autoDelaySeconds",seconds).apply();
            dialog.dismiss();
            setAutomatic(true);
        }));
        dialog.show();
    }
    void afterAnnouncement() {
        if (!autoAnnouncing) return;
        if (index >= stops.size()-1) setAutomatic(false);
        else handler.postDelayed(autoAdvance,autoDelaySeconds*1000L);
    }

    int mdSurface(){return preference("appTheme",0)==1?0xfffffbfe:preference("appTheme",0)==2?0xff192738:0xff211f26;}
    int mdSurfaceHigh(){return preference("appTheme",0)==1?0xffeeebf3:preference("appTheme",0)==2?0xff243448:0xff2b2931;}
    int mdSecondary(){return preference("appTheme",0)==1?0xff605c68:0xffc7c3d0;}
    int mdOutline(){return preference("appTheme",0)==1?0xffd3cedb:0xff49454f;}
    int blend(int a,int b,float amount){return Color.rgb((int)(Color.red(a)*(1-amount)+Color.red(b)*amount),(int)(Color.green(a)*(1-amount)+Color.green(b)*amount),(int)(Color.blue(a)*(1-amount)+Color.blue(b)*amount));}
    int mdContainer(){return blend(mdSurface(),accent(),preference("appTheme",0)==1?.12f:.20f);}
    int mdOnPrimary(){return preference("appTheme",0)==1?Color.WHITE:0xff10222c;}
    boolean materialUi(){return !showing||bellOverlay!=null;}
    GradientDrawable rounded(int color,float radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    android.graphics.drawable.Drawable mdButtonBackground(boolean primary){
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},rounded(blend(mdSurface(),appForeground(),.10f),28));
        states.addState(new int[]{},rounded(primary?accent():mdContainer(),28));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(primary?0x33ffffff:0x223f7799),states,rounded(Color.WHITE,28));
    }
    void mdPrimary(Button button){button.setBackground(mdButtonBackground(true));button.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{mdSecondary(),mdOnPrimary()}));button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));}
    void materialize(View view){
        if(view instanceof Spinner){Spinner field=(Spinner)view;field.setBackground(new FieldSurface(true));field.setPadding(dp(12),dp(4),dp(36),dp(4));field.setPopupBackgroundDrawable(rounded(mdSurfaceHigh(),16));}
        else if(view instanceof EditText){EditText field=(EditText)view;field.setBackground(new FieldSurface(false));field.setPadding(dp(16),dp(8),dp(16),dp(8));field.setTextColor(appForeground());field.setHintTextColor(mdSecondary());field.setTextSize(16);field.setSingleLine(true);}
        else if(view instanceof ListView){ListView list=(ListView)view;list.setDivider(null);list.setDividerHeight(dp(8));list.setSelector(rounded(0x183f90c0,20));list.setPadding(0,dp(4),0,dp(8));list.setClipToPadding(false);}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)materialize(group.getChildAt(i));}
    }
    class FieldSurface extends android.graphics.drawable.Drawable {
        final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);final boolean arrow;float focus=0;android.animation.ValueAnimator focusMotion;
        public boolean isStateful(){return true;}
        protected boolean onStateChange(int[] states){boolean active=false;for(int state:states)if(state==android.R.attr.state_focused||state==android.R.attr.state_pressed)active=true;float target=active?1:0;if(focusMotion!=null)focusMotion.cancel();if(motionEnabled()&&focus!=target){focusMotion=android.animation.ValueAnimator.ofFloat(focus,target);focusMotion.setDuration(150);focusMotion.setInterpolator(mdStandard);focusMotion.addUpdateListener(value->{focus=(float)value.getAnimatedValue();invalidateSelf();});focusMotion.start();}else{focus=target;invalidateSelf();}return true;}
        FieldSurface(boolean chevron){arrow=chevron;}
        public void draw(Canvas canvas){Rect b=getBounds();brush.setStyle(Paint.Style.FILL);brush.setColor(mdSurfaceHigh());canvas.drawRoundRect(new RectF(b),dp(14),dp(14),brush);if(arrow){brush.setColor(mdSecondary());brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(dp(2));brush.setStrokeCap(Paint.Cap.ROUND);Path path=new Path();float x=b.right-dp(20),y=b.exactCenterY();path.moveTo(x-dp(4),y-dp(2));path.lineTo(x,y+dp(2));path.lineTo(x+dp(4),y-dp(2));canvas.drawPath(path,brush);brush.setStyle(Paint.Style.FILL);}if(focus>0){brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(dp(2));brush.setColor(accent());brush.setAlpha((int)(255*focus));canvas.drawRoundRect(new RectF(b.left+dp(1),b.top+dp(1),b.right-dp(1),b.bottom-dp(1)),dp(14),dp(14),brush);brush.setAlpha(255);brush.setStyle(Paint.Style.FILL);}}
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    LinearLayout mdCard(){LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(rounded(mdSurface(),24));return card;}
    void addCard(LinearLayout root,LinearLayout card){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(16);root.addView(card,params);}
    void mdLabel(LinearLayout root,String label){TextView text=text(label,13,mdSecondary());text.setTypeface(Typeface.create("sans-serif-medium",0));text.setPadding(dp(2),dp(10),0,dp(8));root.addView(text);}
    void pageHeader(LinearLayout root,String label,String description,View artwork,Runnable backAction){
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.TOP);LinearLayout title=column();
        TextView name=text("公車報站&導乘",13,accent());name.setTypeface(Typeface.create("sans-serif-medium",0));name.setSingleLine(true);name.setPadding(0,0,0,dp(6));title.addView(name,new LinearLayout.LayoutParams(-1,-2));
        TextView headline=text(label,32,WHITE);headline.setPadding(0,dp(2),0,dp(4));headline.setSingleLine(true);headline.setEllipsize(android.text.TextUtils.TruncateAt.END);title.addView(headline,new LinearLayout.LayoutParams(-1,-2));
        TextView hint=text(description,14,mdSecondary());hint.setSingleLine(true);hint.setEllipsize(android.text.TextUtils.TruncateAt.END);hint.setPadding(0,0,0,dp(8));title.addView(hint,new LinearLayout.LayoutParams(-1,-2));header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(artwork!=null){artwork.setBackground(rounded(mdSurface(),16));artwork.setClipToOutline(true);artwork.setPadding(dp(4),dp(4),dp(4),dp(4));header.addView(artwork,new LinearLayout.LayoutParams(dp(132),dp(76)));}
        if(backAction!=null){Button back=button("←",backAction);back.setContentDescription("返回設定");back.setBackgroundColor(Color.TRANSPARENT);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(20);root.addView(header,params);
    }
    void styleSelection(LinearLayout root){
        LinearLayout heading=(LinearLayout)root.getChildAt(0);Button search=null;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child instanceof Button&&((Button)child).getText().toString().equals("搜尋路線"))search=(Button)child;}
        root.removeAllViews();heading.removeAllViews();pageHeader(root,"播放","選擇路線與方向",routePreview,null);
        LinearLayout form=mdCard();mdLabel(form,"分類");form.addView(category,new LinearLayout.LayoutParams(-1,dp(52)));mdLabel(form,"路線");form.addView(route,new LinearLayout.LayoutParams(-1,dp(56)));if(search!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(12);form.addView(search,p);}mdLabel(form,"方向");form.addView(direction,new LinearLayout.LayoutParams(-1,dp(56)));addCard(root,form);
        summary.setVisibility(View.GONE);mdPrimary(confirm);root.addView(confirm,new LinearLayout.LayoutParams(-1,dp(56)));
    }
    void styleSections(LinearLayout root,String[] titles){
        ArrayList<View> children=new ArrayList<>();for(int i=0;i<root.getChildCount();i++)children.add(root.getChildAt(i));root.removeAllViews();LinearLayout section=null;
        for(int i=0;i<children.size();i++){View child=children.get(i);String label=child instanceof TextView?((TextView)child).getText().toString():"";boolean heading=!(child instanceof Button)&&Arrays.asList(titles).contains(label);
            if(i==0&&child instanceof TextView){TextView title=(TextView)child;title.setTextSize(32);title.setTypeface(Typeface.create("sans-serif",0));title.setPadding(0,dp(8),0,dp(24));root.addView(title);continue;}
            if(heading){section=mdCard();addCard(root,section);TextView title=(TextView)child;title.setTextColor(appForeground());title.setTextSize(20);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setPadding(0,0,0,dp(12));section.addView(child);continue;}
            if(section!=null){if(child instanceof Button){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(48));params.topMargin=dp(8);params.bottomMargin=dp(4);section.addView(child,params);if(label.equals("儲存播放間隔")||label.equals("我要下車"))mdPrimary((Button)child);}else{section.addView(child);if(child instanceof TextView&&!(child instanceof Spinner)&&!(child instanceof EditText)&&!(child instanceof CompoundButton)){TextView body=(TextView)child;if(body.getTextSize()/getResources().getDisplayMetrics().scaledDensity<=16){body.setTextColor(mdSecondary());body.setLineSpacing(dp(3),1);}}}}
            else{root.addView(child);if(child instanceof TextView){TextView context=(TextView)child;context.setTextSize(15);context.setTextColor(mdSecondary());context.setPadding(dp(2),0,0,dp(16));}}
        }
    }
    View materialListRow(String label){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(76));row.setPadding(dp(16),dp(14),dp(12),dp(14));row.setBackground(rounded(mdSurface(),20));
        FrameLayout badge=new FrameLayout(this);badge.setBackground(rounded(mdContainer(),14));GlyphView icon=new GlyphView(1,accent());FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);badge.addView(icon,iconParams);row.addView(badge,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=text(label,16,WHITE);title.setTypeface(Typeface.create("sans-serif-medium",0));title.setPadding(dp(14),0,dp(12),0);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=text("›",26,mdSecondary());arrow.setPadding(0,0,0,0);row.addView(arrow,new LinearLayout.LayoutParams(dp(16),-2));pressMotion(row);return row;
    }
    class MdSwitch extends CompoundButton {
        final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);
        float progress=0;android.animation.ValueAnimator thumbMotion;
        MdSwitch(){super(PhoneActivity.this);setClickable(true);setFocusable(true);setButtonDrawable((android.graphics.drawable.Drawable)null);setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x183f90c0),rounded(Color.TRANSPARENT,16),rounded(Color.WHITE,16)));setMinimumHeight(dp(56));setTextSize(16);progress=isChecked()?1:0;}
        public void setChecked(boolean checked){boolean changed=checked!=isChecked();super.setChecked(checked);if(!changed)return;if(thumbMotion!=null)thumbMotion.cancel();float target=checked?1:0;if(brush!=null&&isLaidOut()&&motionEnabled()){thumbMotion=android.animation.ValueAnimator.ofFloat(progress,target);thumbMotion.setDuration(200);thumbMotion.setInterpolator(mdStandard);thumbMotion.addUpdateListener(value->{progress=(float)value.getAnimatedValue();invalidate();});thumbMotion.start();}else{progress=target;invalidate();}}
        public CharSequence getAccessibilityClassName(){return "android.widget.Switch";}
        protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        protected void onDetachedFromWindow(){if(thumbMotion!=null)thumbMotion.cancel();super.onDetachedFromWindow();}
        protected void onDraw(Canvas canvas){float p=progress,x=getWidth()-getPaddingRight()-dp(54),y=getHeight()/2f;int track=blend(mdSurfaceHigh(),accent(),p);if(!isEnabled())track=blend(mdSurface(),appForeground(),.10f);brush.setStyle(Paint.Style.FILL);brush.setColor(track);canvas.drawRoundRect(new RectF(x,y-dp(16),x+dp(52),y+dp(16)),dp(16),dp(16),brush);if(p<1){brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(dp(2));brush.setColor(isEnabled()?mdSecondary():mdOutline());brush.setAlpha((int)(255*(1-p)));canvas.drawRoundRect(new RectF(x+dp(1),y-dp(15),x+dp(51),y+dp(15)),dp(15),dp(15),brush);brush.setAlpha(255);brush.setStyle(Paint.Style.FILL);}float center=x+dp(16)+dp(20)*p;brush.setColor(isEnabled()?blend(mdSecondary(),mdOnPrimary(),p):mdOutline());canvas.drawCircle(center,y,dp(8)+dp(4)*p,brush);if(p>0){brush.setColor(accent());brush.setAlpha((int)(255*p));brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(dp(2));brush.setStrokeCap(Paint.Cap.ROUND);Path check=new Path();check.moveTo(center-dp(4),y);check.lineTo(center-dp(1),y+dp(3));check.lineTo(center+dp(5),y-dp(3));canvas.drawPath(check,brush);brush.setStyle(Paint.Style.FILL);brush.setAlpha(255);}brush.setTextSize(getTextSize());brush.setTypeface(Typeface.create("sans-serif",0));brush.setColor(isEnabled()?appForeground():mdSecondary());Paint.FontMetrics metrics=brush.getFontMetrics();canvas.drawText(getText().toString(),getPaddingLeft()+dp(2),y-(metrics.ascent+metrics.descent)/2,brush);}
    }
    class GlyphView extends View {
        final int kind;int tint;final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);
        GlyphView(int type,int color){super(PhoneActivity.this);kind=type;tint=color;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas canvas){canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);brush.setColor(tint);brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(1.8f);brush.setStrokeCap(Paint.Cap.ROUND);brush.setStrokeJoin(Paint.Join.ROUND);Path path=new Path();if(kind==0){canvas.drawCircle(12,12,9,brush);path.moveTo(10,8);path.lineTo(16,12);path.lineTo(10,16);path.close();canvas.drawPath(path,brush);}else if(kind==1){path.moveTo(3,5);path.lineTo(9,3);path.lineTo(15,6);path.lineTo(21,4);path.lineTo(21,19);path.lineTo(15,21);path.lineTo(9,18);path.lineTo(3,20);path.close();canvas.drawPath(path,brush);canvas.drawLine(9,3,9,18,brush);canvas.drawLine(15,6,15,21,brush);}else if(kind==2){canvas.drawLine(4,6,20,6,brush);canvas.drawLine(4,12,20,12,brush);canvas.drawLine(4,18,20,18,brush);brush.setStyle(Paint.Style.FILL);canvas.drawCircle(9,6,2.8f,brush);canvas.drawCircle(16,12,2.8f,brush);canvas.drawCircle(8,18,2.8f,brush);}else{for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*9.5f,12+(float)Math.sin(a)*9.5f,brush);}canvas.drawCircle(12,12,7,brush);canvas.drawCircle(12,12,2.5f,brush);}canvas.restore();}
    }
    class MotionDialog extends Dialog {
        boolean closing=false;
        MotionDialog(int style){super(PhoneActivity.this,style);}
        View surface(){View view=findViewById(android.R.id.content);return view instanceof ViewGroup&&((ViewGroup)view).getChildCount()>0?((ViewGroup)view).getChildAt(0):view;}
        public void show(){if(getWindow()!=null)getWindow().setWindowAnimations(0);super.show();closing=false;View content=surface();if(content!=null&&motionEnabled()){content.setAlpha(0);content.setScaleX(.96f);content.setScaleY(.96f);content.animate().alpha(1).scaleX(1).scaleY(1).setDuration(300).setInterpolator(mdEnter).start();}}
        public void dismiss(){View content=surface();if(!isShowing()||content==null||!motionEnabled()){super.dismiss();return;}if(closing)return;closing=true;content.animate().cancel();content.animate().alpha(0).scaleX(.98f).scaleY(.98f).setDuration(150).setInterpolator(mdExit).withEndAction(()->{super.dismiss();closing=false;}).start();}
    }
    final android.view.animation.Interpolator mdStandard=new android.view.animation.PathInterpolator(.2f,0f,0f,1f);
    final android.view.animation.Interpolator mdEnter=new android.view.animation.PathInterpolator(.05f,.7f,.1f,1f);
    final android.view.animation.Interpolator mdExit=new android.view.animation.PathInterpolator(.3f,0f,.8f,.15f);
    LinearLayout materialShell;
    FrameLayout materialStage,materialNav;
    View pageContent,navIndicator;
    int renderedTab=-1,navTarget=0;
    String shellAppearance="";
    int appliedAccent=0;
    android.animation.ValueAnimator indicatorMotion;
    final TextView[] navLabels=new TextView[4];
    final GlyphView[] navGlyphs=new GlyphView[4];
    final View[] navCells=new View[4];
    final ArrayList<android.animation.Animator> navEffects=new ArrayList<>();
    boolean motionEnabled(){return android.animation.ValueAnimator.areAnimatorsEnabled();}
    void pressMotion(View view){
        android.animation.StateListAnimator states=new android.animation.StateListAnimator();
        android.animation.ObjectAnimator downX=android.animation.ObjectAnimator.ofFloat(view,"scaleX",.98f),downY=android.animation.ObjectAnimator.ofFloat(view,"scaleY",.98f);
        android.animation.AnimatorSet down=new android.animation.AnimatorSet();down.playTogether(downX,downY);down.setDuration(100);down.setInterpolator(mdStandard);
        android.animation.AnimatorSet up=new android.animation.AnimatorSet();up.playTogether(android.animation.ObjectAnimator.ofFloat(view,"scaleX",1f),android.animation.ObjectAnimator.ofFloat(view,"scaleY",1f));up.setDuration(150);up.setInterpolator(mdEnter);
        states.addState(new int[]{android.R.attr.state_pressed,android.R.attr.state_enabled},down);states.addState(new int[]{},up);view.setStateListAnimator(states);
    }
    void tintNavigation(int tab,boolean animate){
        for(android.animation.Animator effect:navEffects)effect.cancel();navEffects.clear();
        for(int i=0;i<4;i++){final int position=i;navCells[i].setSelected(i==tab);int destination=i==tab?accent():mdSecondary();
            if(animate&&motionEnabled()){android.animation.ValueAnimator tint=android.animation.ValueAnimator.ofArgb(navLabels[i].getCurrentTextColor(),destination);tint.setDuration(200);tint.setInterpolator(mdStandard);tint.addUpdateListener(value->{int color=(int)value.getAnimatedValue();navLabels[position].setTextColor(color);navGlyphs[position].tint=color;navGlyphs[position].invalidate();});navEffects.add(tint);tint.start();}
            else{navLabels[i].setTextColor(destination);navGlyphs[i].tint=destination;navGlyphs[i].invalidate();}
        }
    }
    void moveNavigation(int tab,boolean animate){
        navTarget=tab;if(materialNav==null||materialNav.getWidth()==0)return;
        float width=materialNav.getWidth()/4f,target=width*tab+(width-dp(64))/2f;
        if(indicatorMotion!=null)indicatorMotion.cancel();
        if(animate&&motionEnabled()){indicatorMotion=android.animation.ValueAnimator.ofFloat(navIndicator.getTranslationX(),target);indicatorMotion.setDuration(300);indicatorMotion.setInterpolator(mdStandard);indicatorMotion.addUpdateListener(value->navIndicator.setTranslationX((float)value.getAnimatedValue()));indicatorMotion.start();}
        else navIndicator.setTranslationX(target);
    }
    View materialNavigation(int tab){
        boolean compact=showing;materialNav=new FrameLayout(this);materialNav.setBackgroundColor(mdSurfaceHigh());materialNav.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(compact?64:80)));
        navIndicator=new View(this);navIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);navIndicator.setBackground(rounded(mdContainer(),16));FrameLayout.LayoutParams marker=new FrameLayout.LayoutParams(dp(64),dp(32));marker.topMargin=dp(compact?5:11);materialNav.addView(navIndicator,marker);
        LinearLayout row=new LinearLayout(this);String[] labels={"播放","路線圖","設備","設定"};
        for(int i=0;i<labels.length;i++){final int page=i;LinearLayout cell=column();cell.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);cell.setPadding(0,dp(compact?5:11),0,dp(4));cell.setClickable(true);cell.setFocusable(true);cell.setContentDescription(labels[i]+"分頁");cell.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x224e8ca8),null,rounded(Color.WHITE,20)));cell.setOnClickListener(v->showTab(page));pressMotion(cell);
            FrameLayout iconSpace=new FrameLayout(this);GlyphView icon=new GlyphView(i,mdSecondary());iconSpace.addView(icon,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));cell.addView(iconSpace,new LinearLayout.LayoutParams(dp(64),dp(32)));TextView label=text(labels[i],12,mdSecondary());label.setTypeface(Typeface.create("sans-serif-medium",0));label.setPadding(0,dp(4),0,0);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);cell.addView(label,new LinearLayout.LayoutParams(-2,-2));navCells[i]=cell;navLabels[i]=label;navGlyphs[i]=icon;row.addView(cell,new LinearLayout.LayoutParams(0,-1,1));}
        materialNav.addView(row,new FrameLayout.LayoutParams(-1,-1));materialNav.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{if(right-left!=oldRight-oldLeft)moveNavigation(navTarget,false);});navTarget=tab;tintNavigation(tab,false);return materialNav;
    }
    void mountShell(View content,int tab){
        shellTab=tab;if(!showing)materialize(content);
        String appearance=preference("appTheme",0)+":"+preference("appAccent",0)+":"+Integer.toHexString(accent())+":"+showing;
        appliedAccent=accent();
        boolean rebuild=materialShell==null||!appearance.equals(shellAppearance)||materialShell.getParent()==null;
        int previous=renderedTab;View outgoing=pageContent;
        if(rebuild){
            if(indicatorMotion!=null)indicatorMotion.cancel();if(outgoing!=null)outgoing.animate().cancel();for(android.animation.Animator effect:navEffects)effect.cancel();navEffects.clear();
            materialShell=column();shellAppearance=appearance;if(Build.VERSION.SDK_INT>=29)materialShell.setForceDarkAllowed(false);
            materialShell.setOnApplyWindowInsetsListener((view,insets)->{view.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
            materialStage=new FrameLayout(this);materialShell.addView(materialStage,new LinearLayout.LayoutParams(-1,0,1));materialShell.addView(materialNavigation(tab));materialNav.setVisibility(showing?View.INVISIBLE:View.VISIBLE);setContentView(materialShell);outgoing=null;
        }
        applyAppBackground(materialShell);
        for(int i=materialStage.getChildCount()-1;i>=0;i--){View stale=materialStage.getChildAt(i);stale.animate().cancel();if(stale!=outgoing)materialStage.removeView(stale);}
        materialStage.addView(content,new FrameLayout.LayoutParams(-1,-1));pageContent=content;renderedTab=tab;
        boolean animate=motionEnabled()&&previous>=0;
        if(animate){
            final View old=outgoing;boolean axis=previous!=tab&&!showing;float direction=tab>=previous?1:-1;
            content.setAlpha(0f);content.setTranslationX(axis?dp(30)*direction:0);content.setScaleX(axis?1f:.98f);content.setScaleY(axis?1f:.98f);
            if(old!=null){old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);old.animate().alpha(0f).translationX(axis?-dp(16)*direction:0).setDuration(100).setInterpolator(mdExit).withEndAction(()->{if(old.getParent()==materialStage)materialStage.removeView(old);}).start();}
            content.animate().alpha(1).translationX(0).scaleX(1).scaleY(1).setStartDelay(old==null?0:70).setDuration(300).setInterpolator(mdEnter).withEndAction(()->{content.setAlpha(1);content.setTranslationX(0);content.setScaleX(1);content.setScaleY(1);}).start();
        }else{if(outgoing!=null)materialStage.removeView(outgoing);content.setAlpha(1);content.setTranslationX(0);content.setScaleX(1);content.setScaleY(1);}
        moveNavigation(tab,!rebuild&&previous!=tab);tintNavigation(tab,!rebuild&&previous!=tab);
    }

    int shellTab=0;
    int settingsSection=-1;
    static final String[] SETTINGS_SECTIONS={"播放顯示","音檔與自動播放","應用程式外觀"};
    boolean playbackActive=false;
    Bitmap userBackground;
    View bellHost;
    TextView equipmentStatus,equipmentStation;
    JSONObject externalFiles=new JSONObject();
    int externalChoice=0,externalWay=0;
    Spinner externalRouteSpinner,externalDirectionSpinner;
    static final String[] PLAYBACK_THEMES={"原版黑底","深藍","明亮"};
    static final String[] APP_THEMES={"經典深色","明亮","深藍"};
    static final int[] ACCENTS={0xff54bde5,0xffbd78d8,0xff38b596,0xffffaa44,0xffff6262};
    int preference(String key,int fallback){return getPreferences(0).getInt(key,fallback);}
    int appForeground(){return preference("appTheme",0)==1?0xff18202c:WHITE;}
    int appBackgroundColor(){int theme=preference("appTheme",0);if(showing)return theme==1?0xfff4f6fa:theme==2?0xff0c1d36:Color.BLACK;return theme==1?0xfff7f5fa:theme==2?0xff101c2b:0xff141218;}
    boolean followsSystemAccent(){return Build.VERSION.SDK_INT>=31&&preference("appAccent",0)==5;}
    int accent(){
        if(followsSystemAccent())return getResources().getColor(preference("appTheme",0)==1?android.R.color.system_accent1_600:android.R.color.system_accent1_200,null);
        int selection=preference("appAccent",0);int index=selection>=0&&selection<ACCENTS.length?selection:0;int[] light={0xff007ca8,0xff84449b,0xff13765b,0xff9d5700,0xffb33535};return preference("appTheme",0)==1?light[index]:ACCENTS[index];
    }
    int playbackBackground(){int theme=preference("playbackTheme",0);return theme==1?0xff071f38:theme==2?0xfff4f6fa:Color.BLACK;}
    int playbackText(){return preference("playbackTheme",0)==2?0xff18202c:WHITE;}
    int playbackCurrent(){return preference("playbackTheme",0)==1?0xffffb34d:0xffbd629d;}
    int playbackLine(){return preference("playbackTheme",0)==2?0xff267b9b:BLUE;}
    int playbackClockColor(){return preference("playbackTheme",0)==2?0xff14745a:0xff3fab8b;}
    void applyAppBackground(View view){
        if(!getPreferences(0).getBoolean("customBackground",false)){view.setBackgroundColor(appBackgroundColor());return;}
        if(userBackground==null)userBackground=BitmapFactory.decodeFile(new File(getFilesDir(),"app-background.jpg").getPath());
        if(userBackground==null){view.setBackgroundColor(appBackgroundColor());return;}
        final Bitmap image=userBackground;final boolean light=preference("appTheme",0)==1;
        view.setBackground(new android.graphics.drawable.Drawable(){
            final Paint brush=new Paint(Paint.FILTER_BITMAP_FLAG);
            public void draw(Canvas canvas){Rect bounds=getBounds();float scale=Math.max(bounds.width()/(float)image.getWidth(),bounds.height()/(float)image.getHeight());float w=image.getWidth()*scale,h=image.getHeight()*scale;canvas.drawBitmap(image,null,new RectF(bounds.centerX()-w/2,bounds.centerY()-h/2,bounds.centerX()+w/2,bounds.centerY()+h/2),brush);canvas.drawColor(light?0xcceff3fa:0xbb000000);}
            public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}
        });
    }
    void prepareShell(int tab){
        if(!playbackActive){setAutomatic(false);stopAudio();handler.removeCallbacksAndMessages(null);}else handler.removeCallbacks(blink);showing=false;
        shellTab=tab;externalButton=null;bellButton=null;equipmentStatus=null;equipmentStation=null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(preference("appTheme",0)==1?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        getWindow().setStatusBarColor(appBackgroundColor());getWindow().setNavigationBarColor(appBackgroundColor());
    }
    void showTab(int tab){
        if(tab==shellTab&&(tab!=0||showing))return;
        handler.removeCallbacks(blink);showing=false;
        if(tab==0){if(playbackActive)renderPlayback();else selection();}
        else if(tab==1)showMapsPage();else if(tab==2)showEquipment();else {settingsSection=-1;showSettings();}
    }
    JSONObject externalInfo(){JSONObject route=externalFiles.optJSONObject(IDS[externalChoice]);return route==null?new JSONObject():route.optJSONObject(""+externalWay)==null?new JSONObject():route.optJSONObject(""+externalWay);}
    void updateEquipmentControls(){
        if(externalRouteSpinner!=null)externalRouteSpinner.setEnabled(!speaking);
        if(externalDirectionSpinner!=null)externalDirectionSpinner.setEnabled(!speaking);
        if(equipmentStation!=null&&playbackActive&&!stops.isEmpty())equipmentStation.setText("目前站名："+stops.get(index));
        if(externalButton!=null){boolean ready=!speaking&&hasExternal();externalButton.setEnabled(ready);externalButton.setAlpha(ready?1f:.4f);}
        if(bellButton!=null){bellButton.setEnabled(!speaking);bellButton.setAlpha(speaking?.4f:1f);}
        if(equipmentStatus!=null){JSONObject info=externalInfo();equipmentStatus.setText(speaking?"播放中，請等候音檔結束。":info.optBoolean("available")?"對外廣播目的地："+info.optString("destination"):info.optString("reason","此路線尚未提供對外廣播音檔"));}
        updateBellPage();
    }
    void showEquipment(){
        prepareShell(2);LinearLayout root=column();root.setPadding(dp(24),dp(16),dp(24),dp(20));
        pageHeader(root,"設備","下車鈴與對外廣播",null,null);root.addView(text("播放頁路線："+LABELS[choice]+" · "+(way==0?"去程":"返程")+" · 往 "+data(choice,way).optString("to"),18,WHITE));
        if(playbackActive&&!stops.isEmpty()){equipmentStation=text("目前站名："+stops.get(index),16,WHITE);root.addView(equipmentStation);}
        root.addView(text("下車鈴",21,WHITE));bellButton=button("我要下車",()->showBellMenu());root.addView(bellButton,new LinearLayout.LayoutParams(-1,dp(52)));
        root.addView(text("一般下車鈴及卡農下車鈴，含下站停車指示器。",14,WHITE));
        root.addView(text("對外廣播",21,WHITE));
        root.addView(text("廣播路線",13,mdSecondary()));externalRouteSpinner=new Spinner(this);items(externalRouteSpinner,LABELS);externalRouteSpinner.setSelection(externalChoice);root.addView(externalRouteSpinner,new LinearLayout.LayoutParams(-1,dp(56)));
        root.addView(text("廣播方向",13,mdSecondary()));externalDirectionSpinner=new Spinner(this);root.addView(externalDirectionSpinner,new LinearLayout.LayoutParams(-1,dp(56)));
        externalRouteSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(externalChoice!=position){externalChoice=position;saveExternalSelection();updateExternalDirections();}}});
        updateExternalDirections();externalButton=button("對外廣播",()->announceExternal());externalButton.setContentDescription("播放對外廣播");root.addView(externalButton,new LinearLayout.LayoutParams(-1,dp(52)));
        equipmentStatus=null;
        styleSections(root,new String[]{"下車鈴","對外廣播"});ScrollView scroll=new ScrollView(this);scroll.addView(root);mountShell(scroll,2);updateEquipmentControls();
    }
    void saveExternalSelection(){getPreferences(0).edit().putString("externalRoute",IDS[externalChoice]).putInt("externalDirection",externalWay).apply();}
    void updateExternalDirections(){
        if(externalDirectionSpinner==null)return;externalDirectionSpinner.setOnItemSelectedListener(null);String[] labels=new String[2];
        for(int d=0;d<2;d++)labels[d]=(d==0?"去程":"返程")+"　往 "+data(externalChoice,d).optString("to");
        items(externalDirectionSpinner,labels);externalDirectionSpinner.setSelection(externalWay);
        externalDirectionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){externalWay=position;saveExternalSelection();updateEquipmentControls();}});updateEquipmentControls();
    }

    LinearLayout settingsRow(LinearLayout parent,String title,String summary,Runnable action){
        LinearLayout row=column();row.setBackgroundColor(mdSurface());row.setPadding(dp(24),dp(16),dp(24),dp(16));row.setMinimumHeight(dp(72));row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(title,18,WHITE));if(summary!=null&&!summary.isEmpty()){TextView detail=text(summary,14,WHITE);detail.setTextColor(mdSecondary());detail.setPadding(0,dp(4),0,0);row.addView(detail);}
        if(action!=null){row.setClickable(true);row.setFocusable(true);row.setContentDescription(title);row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x224e8ca8),new android.graphics.drawable.ColorDrawable(mdSurface()),null));row.setOnClickListener(v->action.run());}
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));settingsDivider(parent);return row;
    }
    void distinguishDialog(LinearLayout body,Dialog dialog){
        body.setBackground(rounded(mdSurfaceHigh(),28));
        GradientDrawable border=new GradientDrawable();border.setColor(Color.TRANSPARENT);border.setCornerRadius(dp(28));border.setStroke(dp(1),mdSecondary());body.setForeground(border);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attributes=dialog.getWindow().getAttributes();attributes.dimAmount=.65f;dialog.getWindow().setAttributes(attributes);
    }
    void preferenceChoice(LinearLayout root,String label,String key,String[] options,int fallback,boolean redraw){
        int saved=Math.max(0,Math.min(options.length-1,preference(key,fallback)));
        settingsRow(root,label,options[saved],()->{
            Dialog dialog=new MotionDialog(android.R.style.Theme_Material_NoActionBar);LinearLayout body=column();body.setPadding(dp(8),dp(16),dp(8),dp(16));distinguishDialog(body,dialog);
            TextView heading=text(label,24,WHITE);heading.setPadding(dp(16),0,dp(16),dp(12));body.addView(heading);
            for(int i=0;i<options.length;i++){final int value=i;LinearLayout option=settingsRow(body,(i==saved?"◉  ":"○  ")+options[i],null,()->{if(key.equals("audioBrand")&&value==1)return;getPreferences(0).edit().putInt(key,value).apply();dialog.dismiss();showSettings();});if(key.equals("audioBrand")&&i==1){option.setEnabled(false);option.setAlpha(.45f);}}
            dialog.setContentView(body);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(48),dp(480)),-2);dialog.show();dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(48),dp(480)),-2);
        });
    }
    void settingsDivider(LinearLayout root){View line=new View(this);line.setBackgroundColor(mdOutline());line.setAlpha(.45f);root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));}
    void settingsSwitch(LinearLayout root,String title,String key,boolean enabled){
        MdSwitch toggle=new MdSwitch();toggle.setText(title);toggle.setTextColor(appForeground());toggle.setTextSize(18);toggle.setPadding(dp(24),dp(8),dp(24),dp(8));toggle.setChecked(getPreferences(0).getBoolean(key,true));toggle.setEnabled(enabled);toggle.setBackgroundColor(mdSurface());root.addView(toggle,new LinearLayout.LayoutParams(-1,dp(72)));settingsDivider(root);toggle.setOnCheckedChangeListener((b,checked)->{getPreferences(0).edit().putBoolean(key,checked).apply();if(key.equals("showClock"))showSettings();});
    }
    void showIntervalSettings(){
        Dialog dialog=new MotionDialog(android.R.style.Theme_Material_NoActionBar);LinearLayout body=column();body.setPadding(dp(24),dp(24),dp(24),dp(24));distinguishDialog(body,dialog);body.addView(text("自動播放間隔",24,WHITE));body.addView(text("每站音檔播完後，等待指定秒數再切換下一站；桌面小工具也沿用此設定。",14,WHITE));
        EditText input=new EditText(this);input.setTextColor(appForeground());input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);input.setText(String.valueOf(preference("autoDelaySeconds",1)));input.setHint("0～3600 秒");body.addView(input);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END);actions.addView(button("取消",()->dialog.dismiss()),new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(button("確認",()->{int value;try{value=Integer.parseInt(input.getText().toString().trim());}catch(Exception e){input.setError("請輸入 0～3600 的整數");return;}if(value<0||value>3600){input.setError("請輸入 0～3600 的整數");return;}autoDelaySeconds=value;getPreferences(0).edit().putInt("autoDelaySeconds",value).apply();dialog.dismiss();showSettings();}),new LinearLayout.LayoutParams(0,dp(48),1));body.addView(actions);dialog.setContentView(body);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.show();dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(48),dp(480)),-2);
    }
    void showSettings(){
        prepareShell(3);LinearLayout root=column();root.setPadding(dp(24),dp(16),dp(24),dp(24));
        String[] descriptions={"時鐘、秒數與播放畫面主題","站名音檔與自動播放間隔","主題、強調色與背景照片"};
        pageHeader(root,settingsSection<0?"設定":SETTINGS_SECTIONS[settingsSection],settingsSection<0?"調整播放與應用程式偏好":descriptions[settingsSection],null,settingsSection<0?null:()->{settingsSection=-1;showSettings();});
        LinearLayout settings=column();settings.setBackground(rounded(mdSurface(),24));settings.setClipToOutline(true);root.addView(settings,new LinearLayout.LayoutParams(-1,-2));
        if(settingsSection<0){String[] details={"時鐘、秒數與播放畫面主題","站名音檔廠牌與自動播放間隔","主題、強調色與背景照片"};for(int i=0;i<SETTINGS_SECTIONS.length;i++){final int section=i;LinearLayout row=settingsRow(settings,SETTINGS_SECTIONS[i],details[i],()->{settingsSection=section;showSettings();});row.setContentDescription("開啟"+SETTINGS_SECTIONS[i]);}}
        else if(settingsSection==0){settingsSwitch(settings,"播放時顯示時鐘","showClock",true);settingsSwitch(settings,"時鐘顯示秒數","showSeconds",getPreferences(0).getBoolean("showClock",true));preferenceChoice(settings,"播放畫面主題","playbackTheme",PLAYBACK_THEMES,0,false);}
        else if(settingsSection==1){preferenceChoice(settings,"站名音檔廠牌","audioBrand",new String[]{"立皓","寶錄（尚未啟用）"},0,false);settingsRow(settings,"自動播放間隔",preference("autoDelaySeconds",1)+" 秒",()->showIntervalSettings());}
        else{preferenceChoice(settings,"應用程式主題","appTheme",APP_THEMES,0,true);preferenceChoice(settings,"強調色","appAccent",Build.VERSION.SDK_INT>=31?new String[]{"天空藍","紫色","綠色","橙色","紅色","跟隨系統"}:new String[]{"天空藍","紫色","綠色","橙色","紅色"},0,true);
            LinearLayout follow=settingsRow(settings,"跟隨系統",Build.VERSION.SDK_INT>=31?"使用系統動態強調色":"需要 Android 12 以上版本",Build.VERSION.SDK_INT>=31?()->{getPreferences(0).edit().putInt("appAccent",5).apply();showSettings();}:null);follow.setContentDescription("啟用系統動態強調色");
            settingsRow(settings,"背景照片",getPreferences(0).getBoolean("customBackground",false)?"已使用自訂背景照片":"尚未設定",()->{android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);intent.setType("image/*");startActivityForResult(intent,501);});settingsRow(settings,"清除背景照片",null,()->{getPreferences(0).edit().putBoolean("customBackground",false).apply();userBackground=null;showSettings();});
        }
        for(int i=0;i<settings.getChildCount();i+=2){View item=settings.getChildAt(i);boolean first=i==0,last=i>=settings.getChildCount()-2;float top=first?dp(24):0,bottom=last?dp(24):0;GradientDrawable surface=new GradientDrawable();surface.setColor(mdSurface());surface.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});item.setBackground(item.isClickable()?new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x224e8ca8),surface,null):surface);}if(settings.getChildCount()>0)settings.getChildAt(settings.getChildCount()-1).setVisibility(View.GONE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);mountShell(scroll,3);
    }
    protected void onActivityResult(int request,int result,android.content.Intent data){
        super.onActivityResult(request,result,data);if(request!=501||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            android.net.Uri uri=data.getData();BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}if(options.outWidth<1||options.outHeight<1)throw new IOException("圖片格式不支援");options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap photo;try(InputStream in=getContentResolver().openInputStream(uri)){photo=BitmapFactory.decodeStream(in,null,options);}if(photo==null)throw new IOException("無法讀取圖片");
            int orientation=1;try(InputStream in=getContentResolver().openInputStream(uri)){orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
            Matrix matrix=new Matrix();if(orientation==3)matrix.postRotate(180);else if(orientation==6)matrix.postRotate(90);else if(orientation==8)matrix.postRotate(270);else if(orientation==2)matrix.postScale(-1,1);if(!matrix.isIdentity())photo=Bitmap.createBitmap(photo,0,0,photo.getWidth(),photo.getHeight(),matrix,true);
            try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"app-background.jpg"))){if(!photo.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("無法儲存圖片");}userBackground=photo;getPreferences(0).edit().putBoolean("customBackground",true).apply();showSettings();Toast.makeText(this,"背景照片已匯入",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"無法匯入背景照片，請選擇其他圖片",Toast.LENGTH_LONG).show();}
    }

    Spinner category,route,direction;
    static final String[] CATEGORIES={"一般數字路線","小型路線","捷運接駁路線","其他"};
    int selectedCategory=0;
    final ArrayList<Integer> routeChoices=new ArrayList<>();
    int categoryOf(int position){
        String label=LABELS[position];
        if(label.startsWith("小")||label.startsWith("市民小巴"))return 1;
        if(label.startsWith("紅")||label.startsWith("藍")||label.startsWith("棕"))return 2;
        if(label.contains("幹線")||label.contains("通勤")||label.contains("快線"))return 3;
        return 0;
    }
    void updateRouteChoices(){
        route.setOnItemSelectedListener(null);
        routeChoices.clear();ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<IDS.length;i++)if(categoryOf(i)==selectedCategory){routeChoices.add(i);labels.add(LABELS[i]);}
        int selected=routeChoices.indexOf(choice);
        if(selected<0){selected=0;choice=routeChoices.get(0);}
        items(route,labels.toArray(new String[0]));route.setSelection(selected);
        final ArrayList<Integer> options=new ArrayList<>(routeChoices);
        route.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> a){}
            public void onItemSelected(AdapterView<?> a,View v,int position,long id){if(position>=0&&position<options.size()){choice=options.get(position);updateDirections();}}
        });
        updateDirections();
    }
    ImageView routePreview;
    TextView summary;
    Button confirm,previous,next;
    int choice=0,way=0,index=0;
    boolean showing=false,mapMode=false,visible=false,lit=true;
    ArrayList<String> stops=new ArrayList<>();
    String destination="";
    Bitmap map,number;
    Screen screen;
    final Runnable carousel=new Runnable(){public void run(){
        if(!showing||!visible)return;
        mapMode=!mapMode;refresh();handler.postDelayed(this,5000);
    }};
    final Runnable blink=new Runnable(){public void run(){
        if(!showing||!visible)return;
        lit=!lit;if(bellOverlay!=null)redrawBellTree(bellOverlay);else screen.invalidate();handler.postDelayed(this,500);
    }};
    protected void onCreate(Bundle state){super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        try(InputStream in=getAssets().open("phone/routes.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            JSONArray data=new JSONArray(out.toString("UTF-8"));for(int i=0;i<data.length();i++){JSONObject r=data.getJSONObject(i);database.put(r.getString("id"),r.getJSONObject("directions"));}
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("無法讀取路線資料："+e.getMessage()).setPositiveButton("確定",null).show();}
        try {audioFiles=new JSONObject(BusWidget.read(this,"phone/audio.json"));externalFiles=new JSONObject(BusWidget.read(this,"phone/external.json"));}catch(Exception ignored){}
        if(Build.VERSION.SDK_INT<31&&preference("appAccent",0)==5)getPreferences(0).edit().putInt("appAccent",0).apply();
        String saved=getPreferences(0).getString("lastRoute","3020A");for(int i=0;i<IDS.length;i++)if(IDS[i].equals(saved))choice=i;
        way=Math.max(0,Math.min(1,getPreferences(0).getInt("lastDirection",0)));
        String savedExternal=getPreferences(0).getString("externalRoute",IDS[choice]);externalChoice=choice;for(int i=0;i<IDS.length;i++)if(IDS[i].equals(savedExternal))externalChoice=i;externalWay=Math.max(0,Math.min(1,getPreferences(0).getInt("externalDirection",way)));selection();
    }
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTypeface(Typeface.create("sans-serif",0));t.setIncludeFontPadding(false);t.setTextColor(color==WHITE?appForeground():color);t.setPadding(0,dp(8),0,dp(8));return t;}
    GradientDrawable box(){GradientDrawable d=new GradientDrawable();d.setColor((appBackgroundColor()&0x00ffffff)|0xdd000000);d.setStroke(dp(1),accent());return d;}
    Button button(String title,Runnable action){Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setTextSize(14);button.setTextColor(appForeground());button.setBackground(materialUi()?mdButtonBackground(false):box());button.setPadding(dp(16),0,dp(16),0);button.setMinHeight(0);button.setMinimumHeight(0);button.setMinWidth(0);button.setMinimumWidth(0);if(materialUi()){pressMotion(button);button.setTypeface(Typeface.create("sans-serif-medium",0));button.setLetterSpacing(.01f);button.setTextColor(accent());}button.setOnClickListener(view->action.run());return button;}
    void items(Spinner spinner,String[] labels){ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,labels){public View getView(int position,View recycled,ViewGroup parent){TextView view=(TextView)super.getView(position,recycled,parent);view.setTextColor(appForeground());return view;}public View getDropDownView(int position,View recycled,ViewGroup parent){TextView view=(TextView)super.getDropDownView(position,recycled,parent);view.setTextColor(appForeground());view.setBackgroundColor(appBackgroundColor());return view;}};adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);}
    JSONObject data(int c,int d){JSONObject r=database.optJSONObject(IDS[c]);JSONObject v=r==null?null:r.optJSONObject(""+d);return v==null?new JSONObject():v;}
    JSONArray names(JSONObject r){JSONArray a=r.optJSONArray("stations");return a==null?new JSONArray():a;}
    String station(JSONArray a,int n){Object o=a.opt(n);return o instanceof JSONObject?((JSONObject)o).optString("name"):o==null?"":o.toString();}
    void selection(){
        playbackActive=false;prepareShell(0);
        bellOverlay=null;bellState=0;
        setAutomatic(false);
        stopAudio();
        handler.removeCallbacksAndMessages(null);showing=false;mapMode=false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(preference("appTheme",0)==1?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        LinearLayout root=column();root.setBackgroundColor(Color.TRANSPARENT);root.setPadding(dp(24),dp(16),dp(24),dp(20));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(root);
        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("車內到站顯示",24,WHITE));
        routePreview=new ImageView(this);
        routePreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(0,dp(64),1);
        previewParams.setMargins(dp(12),0,0,0);
        heading.addView(routePreview,previewParams);
        root.addView(heading);
        selectedCategory=categoryOf(choice);
        root.addView(text("分類",16,WHITE));category=new Spinner(this);items(category,CATEGORIES);root.addView(category,new LinearLayout.LayoutParams(-1,dp(54)));
        root.addView(text("路線",16,WHITE));route=new Spinner(this);root.addView(route,new LinearLayout.LayoutParams(-1,dp(54)));root.addView(button("搜尋路線",()->showRouteSearch()),new LinearLayout.LayoutParams(-1,dp(40)));
        root.addView(text("方向",16,WHITE));direction=new Spinner(this);root.addView(direction,new LinearLayout.LayoutParams(-1,dp(54)));
        summary=text("",15,WHITE);root.addView(summary);confirm=button("確認",()->start());root.addView(confirm,new LinearLayout.LayoutParams(-1,dp(48)));
        direction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int p,long n){way=p;updateSummary();}});
        category.setSelection(selectedCategory);
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> a){}
            public void onItemSelected(AdapterView<?> a,View v,int position,long id){if(position!=selectedCategory){selectedCategory=position;updateRouteChoices();}}
        });
        updateRouteChoices();styleSelection(root);mountShell(scroll,0);
    }

    void showRouteSearch() {
        Dialog dialog=new MotionDialog(android.R.style.Theme_Material_NoActionBar);
        LinearLayout root=column();applyAppBackground(root);root.setPadding(dp(20),dp(16),dp(20),dp(16));
        root.addView(text(CATEGORIES[selectedCategory]+"（"+routeChoices.size()+" 條）",24,WHITE));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(appForeground());input.setHintTextColor(0xffaaaaaa);input.setHint("輸入路線名稱或號碼");root.addView(input,new LinearLayout.LayoutParams(-1,dp(54)));
        ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> indices=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels){
            public View getView(int position,View recycled,ViewGroup parent){return materialListRow(getItem(position));}
        };
        ListView list=new ListView(this);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Runnable filter=()->{labels.clear();indices.clear();String query=input.getText().toString().trim();for(int i=0;i<LABELS.length;i++)if(categoryOf(i)==selectedCategory&&LABELS[i].contains(query)){labels.add(LABELS[i]);indices.add(i);}adapter.notifyDataSetChanged();};
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(android.text.Editable s){}});
        list.setOnItemClickListener((parent,view,position,id)->{route.setSelection(routeChoices.indexOf(indices.get(position)));((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);dialog.dismiss();});
        root.addView(button("取消",()->dialog.dismiss()),new LinearLayout.LayoutParams(-1,dp(44)));
        View activityContent=((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);activityContent.setVisibility(View.INVISIBLE);
        dialog.setOnDismissListener(ignored->{activityContent.setVisibility(View.VISIBLE);activityContent.postDelayed(()->redrawBellTree(activityContent),250);});
        filter.run();materialize(root);dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    void showRouteLibrary(){showTab(1);}
    void showMapsPage() {
        prepareShell(1);
        final ArrayList<JSONObject> catalog=new ArrayList<>();
        try {
            JSONArray entries=new JSONArray(BusWidget.read(this,"phone/catalog.json"));
            for(int i=0;i<entries.length();i++)catalog.add(entries.getJSONObject(i));
        } catch(Exception e) {
            Toast.makeText(this,"無法讀取路線圖庫",Toast.LENGTH_LONG).show();return;
        }
        LinearLayout root=column();root.setBackgroundColor(Color.TRANSPARENT);
        root.setPadding(dp(24),dp(16),dp(24),dp(20));
        pageHeader(root,"路線圖","分類搜尋與查看路線圖",null,null);
        root.addView(text("此圖庫保留匯入版本；逐站顯示使用官方現行站序。",14,mdSecondary()));
        LinearLayout mapFilters=new LinearLayout(this);mapFilters.setGravity(Gravity.CENTER_VERTICAL);root.addView(mapFilters,new LinearLayout.LayoutParams(-1,dp(56)));
        Spinner mapCategory=new Spinner(this);items(mapCategory,CATEGORIES);mapCategory.setContentDescription("路線圖分類");mapFilters.addView(mapCategory,new LinearLayout.LayoutParams(0,-1,1));
        EditText search=new EditText(this);search.setSingleLine(true);
        search.setTextColor(appForeground());search.setHintTextColor(0xffaaaaaa);
        search.setHint("搜尋路線或檔案編號，例如：218、紅35");
        LinearLayout.LayoutParams searchParams=new LinearLayout.LayoutParams(0,-1,2);searchParams.leftMargin=dp(12);mapFilters.addView(search,searchParams);
        ListView list=new ListView(this);list.setBackgroundColor(Color.TRANSPARENT);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        final ArrayList<JSONObject> filtered=new ArrayList<>();
        final ArrayList<String> labels=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) {
            public View getView(int position,View recycled,ViewGroup parent) {
                return materialListRow(getItem(position));
            }
        };
        list.setAdapter(adapter);
        Runnable filter=()->{
            filtered.clear();labels.clear();
            String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for(JSONObject item:catalog) {
                String label=item.optString("label")+"  ·  "+item.optString("id");
                int group=3;String routeLabel=item.optString("label");String mapId=item.optString("id");for(int i=0;i<MAPS.length;i++)if(mapId.equals(MAPS[i])){routeLabel=LABELS[i];break;}if(routeLabel.startsWith("小")||routeLabel.startsWith("市民小巴"))group=1;else if(routeLabel.startsWith("紅")||routeLabel.startsWith("藍")||routeLabel.startsWith("棕"))group=2;else if(routeLabel.contains("幹線")||routeLabel.contains("通勤")||routeLabel.contains("快線"))group=3;else if(routeLabel.matches("^[0-9].*"))group=0;
                if(group==mapCategory.getSelectedItemPosition()&&label.toLowerCase(Locale.ROOT).contains(query)) {filtered.add(item);labels.add(label);}
            }
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}
            public void afterTextChanged(android.text.Editable s){}
        });
        list.setOnItemClickListener((parent,view,position,id)->{
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);
            showLibraryMap(filtered.get(position),root);
        });
        mapCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> a){}public void onItemSelected(AdapterView<?> a,View v,int position,long id){filter.run();}});filter.run();mountShell(root,1);
    }
    void showLibraryMap(JSONObject item,View libraryContent) {
        Bitmap image;
        try(InputStream in=getAssets().open("phone/catalog/"+item.optString("file"))) {
            image=BitmapFactory.decodeStream(in);
        }catch(Exception e){Toast.makeText(this,"無法開啟路線圖",Toast.LENGTH_LONG).show();return;}
        if(image==null)return;
        libraryContent.setVisibility(View.INVISIBLE);
        final int orientation=getRequestedOrientation();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        Dialog dialog=new MotionDialog(android.R.style.Theme_Material_NoActionBar_Fullscreen);
        FrameLayout root=new FrameLayout(this);applyAppBackground(root);
        RouteMapView view=new RouteMapView(image);
        view.setContentDescription(item.optString("label")+"，路線圖 "+item.optString("id")+"，可雙指縮放與拖曳");
        root.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackground(rounded(mdSurface(),20));controls.setPadding(dp(8),dp(4),dp(8),dp(4));
        TextView title=text(item.optString("label")+" · "+item.optString("id")+"　雙指縮放／拖曳",13,WHITE);title.setTextColor(appForeground());
        controls.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(button("還原大小",()->view.reset()),new LinearLayout.LayoutParams(dp(92),dp(36)));
        controls.addView(button("返回圖庫",()->dialog.dismiss()),new LinearLayout.LayoutParams(dp(92),dp(36)));
        root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored->{setRequestedOrientation(orientation);libraryContent.setVisibility(View.VISIBLE);libraryContent.postDelayed(()->redrawBellTree(libraryContent),250);});
        dialog.show();dialog.getWindow().setLayout(-1,-1);
        dialog.getWindow().getDecorView().setSystemUiVisibility(5894);
    }
    class RouteMapView extends View {
        final Bitmap image;
        final Matrix transform=new Matrix();
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final ScaleGestureDetector scaleDetector;
        float zoom=1,lastX,lastY;
        boolean dragging=false;
        RouteMapView(Bitmap bitmap) {
            super(PhoneActivity.this);image=bitmap;
            scaleDetector=new ScaleGestureDetector(PhoneActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                public boolean onScale(ScaleGestureDetector detector) {
                    float next=Math.max(1,Math.min(8,zoom*detector.getScaleFactor()));
                    float factor=next/zoom;zoom=next;
                    transform.postScale(factor,factor,detector.getFocusX(),detector.getFocusY());invalidate();return true;
                }
            });
        }
        void reset(){zoom=1;transform.reset();float scale=Math.min(getWidth()/(float)image.getWidth(),getHeight()/(float)image.getHeight());transform.postScale(scale,scale);transform.postTranslate((getWidth()-image.getWidth()*scale)/2,(getHeight()-image.getHeight()*scale)/2);invalidate();}
        protected void onSizeChanged(int w,int h,int oldw,int oldh){reset();}
        protected void onDraw(Canvas canvas){canvas.drawColor(Color.BLACK);canvas.drawBitmap(image,transform,paint);}
        public boolean onTouchEvent(android.view.MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();dragging=true;}
            else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&event.getPointerCount()==1&&dragging&&!scaleDetector.isInProgress()) {
                if(zoom>1){transform.postTranslate(event.getX()-lastX,event.getY()-lastY);invalidate();}
                lastX=event.getX();lastY=event.getY();
            }else if(event.getActionMasked()==MotionEvent.ACTION_POINTER_UP){dragging=false;}
            else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;performClick();}
            return true;
        }
        public boolean performClick(){super.performClick();return true;}
    }

    void updateDirections(){String[] labels=new String[2];for(int d=0;d<2;d++){JSONObject r=data(choice,d);JSONArray a=names(r);String end=r.optString("to",a.length()>0?station(a,a.length()-1):"尚缺站名資料");labels[d]=(d==0?"去程":"返程")+"　往 "+end;}items(direction,labels);direction.setSelection(way);updateSummary();}
    void updateSummary(){
        String[] previewIds={"3020","2230","3022"};
        Bitmap preview=bitmap(choice<3?previewIds[choice]+way+"a.png":MAPS[choice]+".png");
        routePreview.setImageDrawable(null);
        if(preview!=null){
            android.graphics.drawable.BitmapDrawable drawable=new android.graphics.drawable.BitmapDrawable(getResources(),preview);
            drawable.setFilterBitmap(false);
            routePreview.setImageDrawable(drawable);
        }
        routePreview.setContentDescription(LABELS[choice]+(way==0?"去程":"返程")+"路線圖片");
        JSONObject r=data(choice,way);JSONArray a=names(r);boolean ok=a.length()>0;int recorded=0;for(int i=0;i<a.length();i++)if(!audioFiles.optString(station(a,i)).isEmpty())recorded++;summary.setText(ok?"共 "+a.length()+" 站，四語錄音 "+recorded+" 站\n缺錄音仍可跳站及自動播報。\n站序：大臺北公車（2026/11/10）\n路線圖為匯入版本，可能與現行站序不同。":"此方向尚缺完整站名資料");confirm.setEnabled(ok);confirm.setAlpha(ok?1f:.4f);}
    Bitmap bitmap(String file){try(InputStream in=getAssets().open("phone/maps/"+file)){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}}
    void start(){
        JSONObject r=data(choice,way);JSONArray a=names(r);if(a.length()==0)return;
        stops.clear();for(int i=0;i<a.length();i++)stops.add(station(a,i));destination=r.optString("to",stops.get(stops.size()-1));index=0;mapMode=false;lit=true;bellState=0;
        getPreferences(0).edit().putString("lastRoute",IDS[choice]).putInt("lastDirection",way).apply();BusWidget.refresh(this);
        String artwork=MAPS[choice];String reverse=artwork.endsWith("0A")?artwork.substring(0,artwork.length()-1)+"B":artwork;map=bitmap((way==1?reverse:artwork)+".png");if(map==null)map=bitmap(artwork+".png");number=bitmap((way==1?reverse:artwork)+"_1.png");if(number==null)number=bitmap(artwork+"_1.png");playbackActive=true;
        renderPlayback();
    }
    void renderPlayback(){
        showing=true;shellTab=0;externalButton=null;bellButton=null;equipmentStatus=null;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);applyAppBackground(root);screen=new Screen();root.addView(screen,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(3),dp(8),dp(3));
        previous=button("上一站",()->move(-1));next=button("下一站",()->move(1));Button back=button("選擇路線",()->selection());
        autoButton=button("自動播報",()->{if(autoAnnouncing){setAutomatic(false);stopAudio();refresh();}else{autoDelaySeconds=preference("autoDelaySeconds",1);setAutomatic(true);}});
        row.setPadding(dp(8),dp(3),dp(8),dp(3));
        Button[] controls={previous,next,autoButton,back};
        for(int i=0;i<controls.length;i++){
            Button button=controls[i];button.setSingleLine(true);button.setMaxLines(1);button.setHorizontallyScrolling(false);button.setGravity(Gravity.CENTER);button.setPadding(dp(8),0,dp(8),0);button.setTypeface(Typeface.create("sans-serif-medium",0));button.setLetterSpacing(0);button.setAutoSizeTextTypeUniformWithConfiguration(12,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);button.setBackground(mdButtonBackground(false));button.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{mdSecondary(),accent()}));pressMotion(button);if(button==autoButton)mdPrimary(button);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(36),1);if(i>0)params.leftMargin=dp(6);row.addView(button,params);
        }
        root.addView(row,new FrameLayout.LayoutParams(dp(370),-2,Gravity.BOTTOM|Gravity.RIGHT));
        root.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{ViewGroup.LayoutParams params=row.getLayoutParams();int width=Math.min(dp(370),Math.max(1,right-left-dp(16)));if(params.width!=width){params.width=width;row.setLayoutParams(params);}});mountShell(root,0);screen.slideTo(index);refresh();schedule();
    }
    void move(int delta){if(speaking)return;bellState=0;index=Math.max(0,Math.min(stops.size()-1,index+delta));lit=true;if(screen!=null)screen.slideTo(index);refresh();if(delta>0)announce();}
    void refresh(){updateEquipmentControls();if(!showing)return;autoButton.setText(autoAnnouncing?"停止":"自動播報");previous.setEnabled(!speaking&&!autoAnnouncing&&index>0);previous.setAlpha(index>0?1f:.4f);next.setEnabled(!speaking&&!autoAnnouncing&&index<stops.size()-1);next.setAlpha(index<stops.size()-1?1f:.4f);screen.setContentDescription("站名顯示，"+LABELS[choice]+"，"+(way==0?"去程":"返程")+"，第 "+(index+1)+" / "+stops.size()+" 站，"+stops.get(index)+"，時鐘"+(getPreferences(0).getBoolean("showClock",true)?(getPreferences(0).getBoolean("showSeconds",true)?"顯示秒數":"不顯示秒數"):"隱藏")+"，主題"+PLAYBACK_THEMES[preference("playbackTheme",0)]);screen.invalidate();}
    void stopAudio(){bellAudioPlaying=false;if(player!=null){player.release();player=null;}speaking=false;}
    boolean hasExternal(){return externalInfo().optBoolean("available",false);}
    void announceExternal(){if(speaking)return;if(!hasExternal()){Toast.makeText(this,externalInfo().optString("reason","此路線尚未提供對外廣播音檔"),Toast.LENGTH_LONG).show();return;}playAuxiliaryAudio("phone/external/"+externalInfo().optString("file"),"對外廣播");}

    void showBellMenu(){
        if(bellOverlay!=null)return;
        View parent=((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0);bellHost=parent;parent.setVisibility(View.INVISIBLE);
        final int previousOrientation=getRequestedOrientation();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        bellOverlay=new FrameLayout(this);if(Build.VERSION.SDK_INT>=29)bellOverlay.setForceDarkAllowed(false);bellOverlay.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        bellOverlay.setBackgroundColor(appBackgroundColor());
        LinearLayout page=column();
        page.setPadding(dp(16),dp(8),dp(16),dp(8));
        LinearLayout heading=new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("下車鈴",24,WHITE),new LinearLayout.LayoutParams(0,-2,1));
        Button reset=button("重設",()->{
            if(bellAudioPlaying){stopAudio();afterAnnouncement();refresh();}
            bellState=0;updateBellPage();
        });
        heading.addView(reset,new LinearLayout.LayoutParams(dp(80),dp(40)));
        Button back=button("返回",()->closeBellPage());
        LinearLayout.LayoutParams backParams=new LinearLayout.LayoutParams(dp(80),dp(40));
        backParams.setMargins(dp(12),0,0,0);
        heading.addView(back,backParams);
        page.addView(heading);
        bellStatus=text("",14,WHITE);page.addView(bellStatus);
        LinearLayout cards=new LinearLayout(this);
        indicatorView=new HardwareView(0);
        normalBellView=new HardwareView(1);
        canonBellView=new HardwareView(2);
        normalBellView.setContentDescription("一般下車鈴");
        canonBellView.setContentDescription("卡農下車鈴");
        normalBellView.setOnClickListener(v->ringBell(1));
        canonBellView.setOnClickListener(v->ringBell(2));
        HardwareView[] views={indicatorView,normalBellView,canonBellView};
        String[] labels={"下車鈴指示器","一般下車鈴","卡農下車鈴"};
        for(int i=0;i<views.length;i++){
            LinearLayout card=column();
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(rounded(mdSurface(),24));
            card.addView(views[i],new LinearLayout.LayoutParams(-1,0,1));
            TextView label=text(labels[i],16,WHITE);label.setGravity(Gravity.CENTER);
            card.addView(label,new LinearLayout.LayoutParams(-1,-2));
            cards.addView(card,new LinearLayout.LayoutParams(0,-1,1));
        }
        page.addView(cards,new LinearLayout.LayoutParams(-1,0,1));
        bellOverlay.addView(page,new FrameLayout.LayoutParams(-1,-1));
        bellDialog=new MotionDialog(android.R.style.Theme_Material_NoActionBar);
        bellDialog.setContentView(bellOverlay);
        bellDialog.setOnDismissListener(dialog->{bellOverlay=null;bellDialog=null;setRequestedOrientation(previousOrientation);if(bellHost!=null){bellHost.setVisibility(View.VISIBLE);redrawBellTree(bellHost);}});
        bellDialog.show();
        bellDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(appBackgroundColor()));
        bellDialog.getWindow().setLayout(-1,-1);
        bellDialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        updateBellPage();
    }
    void closeBellPage(){if(bellDialog!=null)bellDialog.dismiss();}
    void ringBell(int type){
        if(speaking)return;
        bellState=type;
        bellAudioPlaying=true;
        playAuxiliaryAudio("phone/bells/"+(type==1?"normal.m4a":"canon.m4a"),"下車鈴");
        updateBellPage();
    }
    void updateBellPage(){
        if(bellOverlay==null)return;
        String state=bellState==0?"尚未按鈴":bellState==1?"已按一般下車鈴 · 下站停車":"已按卡農下車鈴 · 下站停車";
        bellStatus.setText(state+(speaking?" · 播放中":""));
        normalBellView.setEnabled(!speaking);canonBellView.setEnabled(!speaking);
        indicatorView.setContentDescription("下車鈴指示器："+state);
        indicatorView.invalidate();normalBellView.invalidate();canonBellView.invalidate();redrawBellTree(bellOverlay);
    }

    void redrawBellTree(View view){view.invalidate();if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)redrawBellTree(group.getChildAt(i));}}
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
    void playAuxiliaryAudio(String assetPath,String label){
        handler.removeCallbacks(autoAdvance);
        try{
            File local=new File(getCacheDir(),"auxiliary-audio");
            try(InputStream in=getAssets().open(assetPath);FileOutputStream out=new FileOutputStream(local)){
                byte[] bytes=new byte[8192];int count;
                while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            }
            speaking=true;refresh();
            player=new android.media.MediaPlayer();
            player.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(local.getPath());
            player.setOnCompletionListener(m->{stopAudio();afterAnnouncement();refresh();});
            player.setOnErrorListener((m,a,b)->{stopAudio();afterAnnouncement();refresh();Toast.makeText(this,label+"播放失敗",Toast.LENGTH_LONG).show();return true;});
            player.prepare();player.start();
        }catch(Exception e){
            stopAudio();afterAnnouncement();refresh();
            Toast.makeText(this,label+"音檔無法播放",Toast.LENGTH_LONG).show();
        }
    }
    void announce(){
        try{
            if(audioFiles.length()==0){try(InputStream in=getAssets().open("phone/audio.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);audioFiles=new JSONObject(out.toString("UTF-8"));}}
            String file=audioFiles.optString(stops.get(index));
            if(file.isEmpty()){afterAnnouncement();refresh();Toast.makeText(this,"此站尚缺完整四語錄音",Toast.LENGTH_LONG).show();return;}
            speaking=true;refresh();player=new android.media.MediaPlayer();
            try(InputStream in=getAssets().open("phone/audio/"+file);FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"announcement.mp3"))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            player.setDataSource(new File(getCacheDir(),"announcement.mp3").getPath());
            player.setOnCompletionListener(m->{stopAudio();afterAnnouncement();refresh();});
            player.setOnErrorListener((m,a,b)->{stopAudio();afterAnnouncement();refresh();Toast.makeText(this,"音檔播放失敗",Toast.LENGTH_LONG).show();return true;});
            player.prepare();player.start();
        }catch(Exception e){stopAudio();afterAnnouncement();refresh();Toast.makeText(this,"無法播放此站音檔",Toast.LENGTH_LONG).show();}
    }
    void schedule(){handler.removeCallbacks(blink);if(showing&&visible){handler.postDelayed(blink,500);}}
    protected void onResume(){super.onResume();stopService(new android.content.Intent(this,WidgetPlaybackService.class));BusWidget.refresh(this);visible=true;if(followsSystemAccent()&&appliedAccent!=accent()){if(showing)renderPlayback();else if(shellTab==0)selection();else if(shellTab==1)showMapsPage();else if(shellTab==2)showEquipment();else showSettings();}schedule();}
    protected void onPause(){setAutomatic(false);stopAudio();visible=false;handler.removeCallbacksAndMessages(null);super.onPause();}
    protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    public void onBackPressed(){if(bellOverlay!=null)closeBellPage();else if(showing)selection();else if(shellTab==3&&settingsSection>=0){settingsSection=-1;showSettings();}else if(shellTab!=0)showTab(0);else super.onBackPressed();}

    class Screen extends View {
        final SimpleDateFormat clock=new SimpleDateFormat("HH:mm:ss",Locale.TAIWAN);final Paint p=new Paint(3);final SimpleDateFormat date=new SimpleDateFormat("yyyy年MM月dd日",Locale.TAIWAN);Canvas c;
                float startColumn, targetColumn;
        long slideStarted, lastFrame, textMillis;
        Screen(){super(PhoneActivity.this);}
        float firstColumn(long now){
            float t=Math.min(1f,(now-slideStarted)/320f);
            return startColumn+(targetColumn-startColumn)*t*t*(3-2*t);
        }
        void slideTo(int stop){
            long now=SystemClock.uptimeMillis();
            startColumn=firstColumn(now);
            targetColumn=Math.max(0,Math.min(stop-2,stops.size()-6));
            slideStarted=now;
        }
        void words(String s,float x,float y,float size,int color){p.setColor(color);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(size);c.drawText(s,x,y,p);}
        void fit(String s,float x,float y,float size,float width,int color){p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(size);float w=p.measureText(s);words(s,x,y,w>width?size*width/w:size,color);}
        void image(Bitmap b,float x,float y,float w,float h){if(b!=null)c.drawBitmap(b,null,new RectF(x,y,x+w,y+h),p);}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);c=canvas;c.drawColor(playbackBackground());if(mapMode&&map!=null){float ratio=Math.min((float)getWidth()/map.getWidth(),(float)getHeight()/map.getHeight());float w=map.getWidth()*ratio,h=map.getHeight()*ratio;image(map,(getWidth()-w)/2,(getHeight()-h)/2,w,h);return;}float scale=Math.min(getWidth()/1920f,(getHeight()-dp(42))/560f);c.save();c.translate((getWidth()-1920*scale)/2,(getHeight()-dp(42)-560*scale)/2);c.scale(scale,scale);

            if(number!=null)image(number,0,60,700,190);else fit(LABELS[choice],10,210,110,680,0xffff3333);
            words("終點站：",10,330,60,playbackText());fit(destination,310,330,60,380,playbackText());
            date.setTimeZone(TimeZone.getDefault());clock.setTimeZone(TimeZone.getDefault());Date currentTime=new Date();words(date.format(currentTime),10,505,60,playbackClockColor());if(getPreferences(0).getBoolean("showClock",true)){clock.applyPattern(getPreferences(0).getBoolean("showSeconds",true)?"HH:mm:ss":"HH:mm");words(clock.format(currentTime),10,555,48,playbackClockColor());}
                        long now=SystemClock.uptimeMillis();
            if(lastFrame>0 && now-lastFrame<150)textMillis+=now-lastFrame;
            lastFrame=now;
            float column=firstColumn(now);
            int first=(int)Math.floor(column);
            int count=Math.min((int)Math.ceil(column+6)-first,stops.size()-first);
            boolean animate=column!=targetColumn;
            c.save();c.clipRect(716,0,1860,560);float slot=(1920f-700-16-60)/6f;
            p.setColor(playbackLine());c.drawRect(716,27,1860,32,p);
            for(int i=0;i<count;i++){int at=first+i;float x=716+slot*(at-column+.5f);int color=at==index?playbackCurrent():playbackText();
                p.setColor(Color.BLACK);c.drawCircle(x,29,12.5f,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(at==index&&!lit?Color.BLACK:color);c.drawCircle(x,29,12.5f,p);p.setStyle(Paint.Style.FILL);
                String name=stops.get(at).replace('(', '︵').replace(')', '︶').replace('（', '︵').replace('）', '︶').replace('-', '｜');
                                float size=42f, height=name.length()*size;
                float offset=0;
                c.save();c.clipRect(x-slot/2,46,x+slot/2,550);
                if(height>504){
                    animate=true;
                    float cycle=height+size;
                    offset=(textMillis*0.06f)%cycle;
                    for(int j=0;j<name.length();j++)
                        words(name.substring(j,j+1),x-size/2,82+j*size-offset+cycle,size,color);
                }
                for(int j=0;j<name.length();j++)
                    words(name.substring(j,j+1),x-size/2,82+j*size-offset,size,color);
                c.restore();
            }
            c.restore();c.restore();if(animate&&visible)postInvalidateOnAnimation(); } } }
















