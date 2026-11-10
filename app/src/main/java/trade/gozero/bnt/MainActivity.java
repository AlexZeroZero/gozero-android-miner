package trade.gozero.bnt;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private int BG,PANEL,BORDER,PURPLE,TEXT,MUTED,GREEN,SURFACE,SELECTED,FIELD,WARNING;
    private boolean lightTheme,followLog=true;
    private LinearLayout logPanel;
    private ScrollView logScroll;
    private TextView logSummary;
    private Button followButton;
    private void loadTheme(){
        lightTheme=getSharedPreferences("app",0).getBoolean("lightTheme",false);
        setTheme(lightTheme?R.style.AppThemeLight:R.style.AppTheme);
        BG=lightTheme?0xfff5f3fa:0xff0d0b12;PANEL=lightTheme?0xffffffff:0xff18141f;
        BORDER=lightTheme?0xffded8e9:0xff30263e;PURPLE=lightTheme?0xff6840ab:0xffbc9bff;
        TEXT=lightTheme?0xff21192e:0xfff2edf8;MUTED=lightTheme?0xff70657e:0xff9c91ad;
        GREEN=lightTheme?0xff167153:0xff7bd8b5;SURFACE=lightTheme?0xffede7f5:0xff251c34;
        SELECTED=lightTheme?0xffe5d9f6:0xff30223f;FIELD=lightTheme?0xfff5f2f9:0xff100d17;
        WARNING=lightTheme?0xffa14516:0xffffb086;
    }
    private void switchTheme(boolean light){
        if(lightTheme==light)return;
        rebuildAppearance(()->getSharedPreferences("app",0).edit().putBoolean("lightTheme",light).apply());
    }
    private void rebuildAppearance(Runnable change){
        // Preserve the in-progress form without saving mining settings.
        String[] draft=page==1?new String[]{pool.getText().toString(),wallet.getText().toString(),worker.getText().toString()}:null;
        int[] choices=page==1?new int[]{threads.getSelectedItemPosition(),temperature.getSelectedItemPosition(),displayMode.getSelectedItemPosition(),jtmMode==null?0:jtmMode.getSelectedItemPosition(),jtmMaxPads==null?0:jtmMaxPads.getSelectedItemPosition()}:null;
        boolean plugged=page==1&&charging.isChecked();int scroll=contentScroll.getScrollY();
        change.run();
        loadTheme();pipEnabled=null;build();
        if(draft!=null){pool.setText(draft[0]);wallet.setText(draft[1]);worker.setText(draft[2]);threads.setSelection(choices[0]);temperature.setSelection(choices[1]);displayMode.setSelection(choices[2]);charging.setChecked(plugged);if(jtmMode!=null){jtmMode.setSelection(choices[3]);jtmMaxPads.setSelection(choices[4]);}contentScroll.post(()->contentScroll.scrollTo(0,scroll));}
    }
    private LinearLayout root,body,nav,actionDock,coinBar,header,device;
    private Coin selected; private Coin pendingCoin; private TextView algorithm;
    private Spinner displayMode;
    private ScrollView contentScroll;
    private TextView hash,status,detail,shares,uptime,temp,cpuTemp,memory,battery,last,log,avg,resources,memoryHint;
    private MiniView mini;
    private Boolean pipEnabled;
    private boolean supportsMini(){return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);}
    private PictureInPictureParams miniParams(boolean enabled){
        PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(2,1));
        Rect source=new Rect();
        if(hash!=null&&((View)hash.getParent().getParent()).getGlobalVisibleRect(source))b.setSourceRectHint(source);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MiningService.class).setAction(MiningService.STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        b.setActions(Collections.singletonList(new RemoteAction(android.graphics.drawable.Icon.createWithResource(this,android.R.drawable.ic_media_pause),tr("停止挖矿"),tr("停止挖矿"),stop)));
        if(Build.VERSION.SDK_INT>=31)b.setAutoEnterEnabled(enabled).setSeamlessResizeEnabled(false);
        return b.build();
    }
    private void updateMini(){
        if(!supportsMini())return;
        boolean enabled=MinerState.active&&new Settings(this,MinerState.coin).mini;
        if(pipEnabled==null||pipEnabled!=enabled){try{setPictureInPictureParams(miniParams(enabled));pipEnabled=enabled;}catch(IllegalStateException ignored){}}
        if(mini!=null)mini.invalidate();
    }
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(Build.VERSION.SDK_INT<31&&MinerState.active&&new Settings(this,MinerState.coin).mini&&supportsMini()){try{enterPictureInPictureMode(miniParams(true));}catch(IllegalStateException ignored){}}}
    @Override public void onPictureInPictureModeChanged(boolean small,android.content.res.Configuration config){
        super.onPictureInPictureModeChanged(small,config);root.setVisibility(small?View.GONE:View.VISIBLE);mini.setVisibility(small?View.VISIBLE:View.GONE);h.removeCallbacks(tick);tick.run();
    }
    @Override protected void onStop(){h.removeCallbacks(tick);super.onStop();}
    @Override protected void onDestroy(){h.removeCallbacks(tick);super.onDestroy();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);compactChrome(config);
    }
    private void compactChrome(android.content.res.Configuration config){
        int visibility=page==2||config.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE?View.GONE:View.VISIBLE;
        if(header!=null)header.setVisibility(visibility);
        if(device!=null)device.setVisibility(visibility);
    }
    private Button action,save;
    private Chart chart;
    private EditText pool,wallet,worker;
    private Spinner threads,temperature,jtmMode,jtmMaxPads;
    private Switch charging;
    private int page=0;
    private int logFilter=0, paintedLogFilter=-1;
    private final ArrayList<Button> logFilters=new ArrayList<>();
    private final Handler h=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refresh();h.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle b){L10n.init(this);loadTheme();super.onCreate(b);selected=MinerState.active?MinerState.coin:Settings.selected(this);if(!MinerState.active)MinerState.select(selected);build();}
    @Override protected void onResume(){super.onResume();h.removeCallbacks(tick);tick.run();}
    @Override protected void onPause(){if(!isInPictureInPictureMode())h.removeCallbacks(tick);super.onPause();}
    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(tr(s));t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("tnum");t.setIncludeFontPadding(false);return t;}
    TextView bold(String s,int size,int color){TextView t=text(s,size,color);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    GradientDrawable outline(int color){GradientDrawable g=bg(color,13);g.setStroke(dp(1),BORDER);return g;}
    LinearLayout card(){LinearLayout l=col();l.setPadding(dp(17),dp(17),dp(17),dp(17));l.setBackground(outline(PANEL));return l;}
    void add(LinearLayout parent,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);}
    void weight(LinearLayout parent,View v){parent.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    Button button(String s,boolean primary){Button b=new Button(this);b.setStateListAnimator(null);b.setElevation(0);b.setText(tr(s));b.setTextSize(14);b.setAllCaps(false);b.setMaxLines(2);b.setAutoSizeTextTypeUniformWithConfiguration(9,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);b.setTextColor(primary?BG:PURPLE);b.setTypeface(Typeface.DEFAULT_BOLD);b.setMinHeight(dp(50));b.setMinimumHeight(dp(50));b.setPadding(dp(14),dp(8),dp(14),dp(8));b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33777799),bg(primary?PURPLE:SURFACE,14),null));return b;}
    void build(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        int flags=getWindow().getDecorView().getSystemUiVisibility();int lightFlags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(lightTheme?flags|lightFlags:flags&~lightFlags);
        root=col();root.setBackgroundColor(BG);root.setPadding(dp(16),0,dp(16),0);
        root.setOnApplyWindowInsetsListener((v,in)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets sys=in.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(dp(16),sys.top,dp(16),sys.bottom);}else{v.setPadding(dp(16),in.getSystemWindowInsetTop(),dp(16),in.getSystemWindowInsetBottom());}return in;});
        FrameLayout frame=new FrameLayout(this);frame.addView(root,new FrameLayout.LayoutParams(-1,-1));mini=new MiniView();mini.setVisibility(View.GONE);frame.addView(mini,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
        header=row();header.setPadding(0,dp(14),0,dp(14));
        LinearLayout brand=col();brand.addView(bold("GOZERO.",20,TEXT));TextView subtitle=text("MOBILE MINER",9,MUTED);subtitle.setLetterSpacing(.16f);add(brand,subtitle,3);weight(header,brand);
        Button aboutButton=compactButton("1.1.0",false);aboutButton.setOnClickListener(v->about());header.addView(aboutButton);root.addView(header);
        coinBar=row();coinBar.setPadding(dp(4),dp(4),dp(4),dp(4));coinBar.setBackground(outline(FIELD));
        for(Coin coin:new Coin[]{Coin.JTM,Coin.BNT}){
            LinearLayout item=row();item.setPadding(dp(10),dp(7),dp(10),dp(7));item.setMinimumHeight(dp(48));item.setContentDescription(tr("切换到 "+coin));item.setFocusable(true);
            ImageView logo=new ImageView(this);logo.setImageResource(coin==Coin.JTM?R.drawable.ic_jtm:R.drawable.coin_bnt);item.addView(logo,new LinearLayout.LayoutParams(dp(27),dp(27)));
            LinearLayout label=col();label.setPadding(dp(8),0,0,0);label.addView(bold(coin.name(),14,TEXT));add(label,text(coin.title,10,MUTED),1);weight(item,label);
            item.setTag(coin);item.setOnClickListener(v->chooseCoin(coin));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(2),0,dp(2),0);coinBar.addView(item,cp);
        }
        root.addView(coinBar);
        device=row();device.setPadding(0,dp(10),0,dp(10));weight(device,text(Build.MODEL+" · ARM64",10,MUTED));algorithm=text("",10,MUTED);device.addView(algorithm);root.addView(device);
        ScrollView sc=new ScrollView(this);contentScroll=sc;sc.setFillViewport(true);sc.setClipToPadding(false);sc.setVerticalScrollBarEnabled(false);body=col();body.setPadding(0,0,0,dp(22));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        logPanel=col();logPanel.setPadding(0,dp(8),0,dp(10));root.addView(logPanel,new LinearLayout.LayoutParams(-1,0,1));
        actionDock=col();actionDock.setPadding(0,dp(8),0,dp(8));root.addView(actionDock,new LinearLayout.LayoutParams(-1,-2));
        nav=row();nav.setBackground(outline(PANEL));nav.setPadding(dp(4),dp(4),dp(4),dp(4));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(54));np.bottomMargin=dp(10);root.addView(nav,np);
        String[] names={"算力","配置","日志"};for(int i=0;i<3;i++){final int target=i;Button b=button(names[i],false);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(2),0,dp(2),0);nav.addView(b,p);b.setOnClickListener(v->{page=target;show();});}
        compactChrome(getResources().getConfiguration());show();
    }
    private void show(){
        if(coinBar!=null){for(int i=0;i<coinBar.getChildCount();i++){View v=coinBar.getChildAt(i);v.setBackground(v.getTag()==selected?outline(SELECTED):bg(FIELD,10));}algorithm.setText(tr(selected==Coin.JTM?"TowerWalk · CPU":"Seine · CPU"));}
        body.removeAllViews();logPanel.removeAllViews();logScroll=null;logSummary=null;followButton=null;
        contentScroll.setVisibility(page==2?View.GONE:View.VISIBLE);logPanel.setVisibility(page==2?View.VISIBLE:View.GONE);
        compactChrome(getResources().getConfiguration());actionDock.removeAllViews();actionDock.setVisibility(View.VISIBLE);hash=status=detail=shares=uptime=temp=cpuTemp=memory=battery=last=log=avg=resources=memoryHint=null;action=save=null;chart=null;
        for(int i=0;i<nav.getChildCount();i++){Button b=(Button)nav.getChildAt(i);b.setTextColor(i==page?PURPLE:MUTED);b.setBackground(bg(i==page?SELECTED:PANEL,11));}
        if(page==0)dashboard();else if(page==1)settings();else logs();actionDock.setVisibility(page==2?View.GONE:View.VISIBLE);refresh();contentScroll.scrollTo(0,0);
    }
    private void heading(String title,String description){add(body,bold(title,22,TEXT),5);add(body,text(description,12,MUTED),8);}
    private void dashboard(){
        Settings config=new Settings(this,selected);
        LinearLayout hero=compactCard();LinearLayout top=row();weight(top,text("本机实时算力",12,MUTED));status=bold("未启动",11,GREEN);top.addView(status);hero.addView(top);
        LinearLayout rate=row();rate.setGravity(Gravity.BOTTOM);hash=bold("0.00",42,TEXT);rate.addView(hash);TextView unit=text(" H/s",14,MUTED);unit.setPadding(dp(5),0,0,dp(6));rate.addView(unit);add(hero,rate,10);
        avg=text("会话均值 —",11,MUTED);add(hero,avg,4);detail=text("准备就绪",10,MUTED);detail.setMaxLines(2);add(hero,detail,5);
        chart=new Chart();add(hero,chart,12);chart.getLayoutParams().height=dp(65);LinearLayout axis=row();weight(axis,text("−5 分钟",10,MUTED));axis.addView(text("现在",10,MUTED));add(hero,axis,4);
        resources=bold("0 / "+config.threads+" 线程 · 0 MiB",12,PURPLE);add(hero,resources,12);
        LinearLayout items=row();cpuTemp=metric(items,"CPU 温度","采样中");cpuTemp.setOnClickListener(v->cpuDetails());cpuTemp.setContentDescription(tr("CPU 最高核心温度，点击查看各核心"));temp=metric(items,"电池温度","—");memory=metric(items,"可用内存","—");battery=metric(items,"电量","—");add(hero,items,12);
        LinearLayout stats=row();shares=metric(stats,"接受 / 拒绝","0 / 0");uptime=metric(stats,"本次运行","00:00:00");add(hero,stats,14);add(body,hero,0);
        LinearLayout connection=compactCard();LinearLayout line=row();weight(line,text("当前矿池",11,MUTED));String host=config.pool;try{host=new java.net.URI(config.pool).getHost();}catch(Exception ignored){}TextView hostLabel=text(host,11,TEXT);hostLabel.setMaxWidth(dp(230));line.addView(hostLabel);connection.addView(line);
        Button payout=compactButton(selected+"  ·  "+shortAddress(config.wallet)+"  ›",false);payout.setOnClickListener(v->{page=1;show();});add(connection,payout,9);add(body,connection,10);
        TextView hint=text(selected==Coin.BNT?"每线程约 2 GiB · 内存显示核心实际 RSS":"ARM64 / NEON + PMULL · 内存显示核心实际 RSS",10,MUTED);add(body,hint,9);
        last=text("等待核心启动",10,MUTED);last.setMaxLines(2);add(body,last,8);
        action=button("开始挖 "+selected,true);action.setOnClickListener(v->toggle());add(actionDock,action,0);
    }
    private static String shortAddress(String v){return v.length()>18?v.substring(0,8)+"…"+v.substring(v.length()-6):v;}
    private void chooseCoin(Coin next){
        if(next==selected||pendingCoin!=null)return;
        if(MinerState.active){new LocalizedDialog(this).setTitle("从 "+selected+" 切换到 "+next).setMessage("先停止当前核心并释放内存，再载入 "+next+" 的独立配置。切换后由你点击开始。").setNegativeButton("继续当前币种",null).setPositiveButton("停止并切换",(d,w)->{pendingCoin=next;startService(new Intent(this,MiningService.class).setAction(MiningService.STOP));refresh();}).show();}
        else completeSwitch(next);
    }
    private void completeSwitch(Coin next){selected=next;pendingCoin=null;getSharedPreferences("app",0).edit().putString("coin",next.name()).apply();MinerState.select(next);show();}
    private TextView metric(LinearLayout parent,String name,String value){LinearLayout c=col();c.addView(text(name,10,MUTED));TextView t=bold(value,16,TEXT);add(c,t,9);weight(parent,c);return t;}
    private LinearLayout compactCard(){LinearLayout c=card();c.setPadding(dp(12),dp(12),dp(12),dp(12));return c;}
    private Button compactButton(String title,boolean primary){Button b=button(title,primary);b.setTextSize(12);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setPadding(dp(12),dp(6),dp(12),dp(6));return b;}
    private EditText field(LinearLayout c,String title,String value,boolean multiline){
        add(c,text(title,11,MUTED),10);EditText e=new EditText(this);e.setTextSize(12);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setPadding(dp(10),dp(7),dp(10),dp(7));e.setMinHeight(dp(48));e.setBackground(bg(FIELD,9));e.setSingleLine(!multiline);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        if(multiline){e.setMinLines(2);e.setMaxLines(3);e.setGravity(Gravity.TOP);}e.setText(value);add(c,e,4);return e;
    }
    private Spinner select(LinearLayout c,String title,String[] options,int position){
        add(c,text(title,11,MUTED),10);Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,options){
            @Override public View getView(int position,View convertView,ViewGroup parent){TextView t=text(getItem(position)+"  ▾",12,TEXT);t.setPadding(dp(9),dp(7),dp(6),dp(7));t.setGravity(Gravity.CENTER_VERTICAL);t.setMinHeight(dp(48));return t;}
            @Override public View getDropDownView(int position,View convertView,ViewGroup parent){TextView t=text(getItem(position),13,TEXT);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER_VERTICAL);t.setBackgroundColor(PANEL);return t;}
        };
        s.setAdapter(adapter);s.setSelection(Math.max(0,position));s.setContentDescription(tr(title));s.setBackground(bg(FIELD,9));add(c,s,4);return s;
    }
    private void settings(){
        Settings s=new Settings(this,selected);LinearLayout title=row();weight(title,bold(selected+" 配置",18,TEXT));Button about=compactButton("使用说明",false);about.setOnClickListener(v->about());title.addView(about);add(body,title,2);
        add(body,text("各币种独立保存 · 停止后可修改",11,MUTED),3);
        LinearLayout appearance=compactCard();LinearLayout themeRow=row();weight(themeRow,bold("外观主题",12,TEXT));
        Button dark=smallButton("深色",!lightTheme),light=smallButton("浅色",lightTheme);dark.setOnClickListener(v->switchTheme(false));light.setOnClickListener(v->switchTheme(true));
        themeRow.addView(dark,new LinearLayout.LayoutParams(dp(68),dp(44)));LinearLayout.LayoutParams themeMargin=new LinearLayout.LayoutParams(dp(68),dp(44));themeMargin.leftMargin=dp(6);themeRow.addView(light,themeMargin);appearance.addView(themeRow);Button language=compactButton("语言 · "+L10n.name(),false);language.setOnClickListener(v->chooseLanguage());add(appearance,language,6);add(body,appearance,8);
        LinearLayout c=compactCard();c.addView(bold("矿池与收款",13,TEXT));pool=field(c,"矿池地址",s.pool,false);wallet=field(c,selected+" 收款地址",s.wallet,true);worker=field(c,"矿工名称",s.worker,false);add(body,c,10);
        LinearLayout limits=compactCard();limits.addView(bold("性能与温控",13,TEXT));LinearLayout options=row();options.setGravity(Gravity.TOP);LinearLayout left=col(),right=col();
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=dp(5);options.addView(left,lp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(5);options.addView(right,rp);
        threads=select(left,selected==Coin.JTM?"线程（自适应时为上限）":"线程 / 内存",Settings.threadOptions(selected),s.threads-1);
        temperature=select(right,"电池温度上限",new String[]{"40 °C","43 °C（默认）","45 °C","48 °C","50 °C"},Arrays.asList(40,43,45,48,50).indexOf(s.temperature));add(limits,options,0);
        memoryHint=text("",10,MUTED);add(limits,memoryHint,6);
        jtmMode=selected==Coin.JTM?select(limits,"JTM 计算策略",new String[]{"按机型优化","标准 · 单工作区","交错计算 · 双工作区","自适应 · 实测线程与内存"},s.jtmMode):null;
        jtmMaxPads=selected==Coin.JTM?select(limits,"自适应工作区上限",new String[]{"512 KiB / 线程","1 MiB / 线程","2 MiB / 线程"},s.jtmMaxPads==4?2:s.jtmMaxPads==2?1:0):null;
        if(selected==Coin.JTM)add(limits,text("自适应会边挖边比较线程、工作区和预取，发热时减载；每次启动重新实测。增加内存不一定更快。",10,MUTED),4);
        threads.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){refresh();}public void onNothingSelected(AdapterView<?> p){}});
        charging=new Switch(this);charging.setText(tr("仅充电时挖矿"));charging.setTextSize(12);charging.setTextColor(TEXT);charging.setChecked(s.chargingOnly);charging.setMinHeight(dp(48));charging.setPadding(0,dp(4),0,dp(4));add(limits,charging,4);
        displayMode=select(limits,"后台显示",new String[]{"小窗 + 通知栏","仅通知栏"},s.mini?0:1);
        add(limits,text("电池降温 2 °C、系统降至中度或以下，稳定 3 秒自动恢复；每秒检查。电池温度不代表 CPU 温度。",10,MUTED),2);add(body,limits,8);
        add(body,text(FeePolicy.description(selected),12,MUTED),8);
        save=compactButton("保存配置",true);save.setOnClickListener(v->saveSettings());add(actionDock,save,0);
    }
    private void chooseLanguage(){
        AlertDialog dialog=new LocalizedDialog(this).setTitle("界面语言")
            .setSingleChoiceItems(L10n.NAMES,L10n.index(),(d,index)->{
                d.dismiss();if(index!=L10n.index())rebuildAppearance(()->L10n.set(this,index));
            }).setNegativeButton("取消",null).create();dialog.show();
    }
    private static String tr(String value){return L10n.text(value);}
    private void saveSettings(){
        if(MinerState.active){toast("请先停止挖矿再保存");return;}
        String p=pool.getText().toString().trim(),w=wallet.getText().toString().trim(),n=worker.getText().toString().trim();String error=Settings.validate(selected,p,w,n);
        if(error!=null){new LocalizedDialog(this).setTitle("检查配置").setMessage(error).setPositiveButton("知道了",null).show();return;}
        Settings.prefs(this,selected).edit().putString("pool",p).putString("wallet",w).putString("worker",n).putInt("threads",threads.getSelectedItemPosition()+1).putInt("temperature",new int[]{40,43,45,48,50}[temperature.getSelectedItemPosition()]).putBoolean("chargingOnly",charging.isChecked()).putBoolean("mini",displayMode.getSelectedItemPosition()==0).apply();
        if(selected==Coin.JTM&&jtmMode!=null)Settings.prefs(this,selected).edit().putInt("jtmMode",jtmMode.getSelectedItemPosition()).putInt("jtmMaxPads",new int[]{1,2,4}[jtmMaxPads.getSelectedItemPosition()]).apply();toast("已保存");
    }
    private Button smallButton(String title,boolean primary){
        Button b=button(title,primary);b.setTextSize(12);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(8),0,dp(8),0);return b;
    }
    private void logs(){
        LinearLayout title=row();weight(title,bold(selected+" 日志",18,TEXT));
        Button copy=smallButton("复制诊断",false);copy.setOnClickListener(v->{String diagnostic=Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n"+MinerState.logText().replaceAll("[1-9A-HJ-NP-Za-km-z]{80,120}|j1[0-9a-z]{58}","[收款地址]");((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(selected+" diagnostic",diagnostic));toast("诊断已复制，完整地址已隐藏");});title.addView(copy);add(logPanel,title,0);
        logSummary=text("最近 240 行",10,MUTED);add(logPanel,logSummary,4);
        LinearLayout filters=row();logFilters.clear();paintedLogFilter=-1;String[] labels={"全部","份额","警告"};
        for(int i=0;i<labels.length;i++){final int filter=i;Button b=smallButton(labels[i],false);b.setOnClickListener(v->{logFilter=filter;refresh();if(followLog)scrollLatest();});logFilters.add(b);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.rightMargin=dp(4);filters.addView(b,lp);}
        followButton=smallButton("跟随最新",false);followButton.setOnClickListener(v->{followLog=!followLog;updateFollowButton();if(followLog)scrollLatest();});filters.addView(followButton,new LinearLayout.LayoutParams(dp(88),dp(44)));add(logPanel,filters,8);
        logScroll=new ScrollView(this);logScroll.setFillViewport(true);logScroll.setBackground(outline(PANEL));logScroll.setPadding(dp(10),dp(8),dp(10),dp(8));logScroll.setClipToPadding(true);logScroll.setClipToOutline(true);
        log=text("尚无日志",11,TEXT);log.setTypeface(Typeface.MONOSPACE);log.setTextIsSelectable(true);log.setLineSpacing(dp(2),1f);
        logScroll.addView(log,new ScrollView.LayoutParams(-1,-2));LinearLayout.LayoutParams console=new LinearLayout.LayoutParams(-1,0,1);console.topMargin=dp(8);logPanel.addView(logScroll,console);
        View.OnTouchListener browse=(v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){followLog=false;updateFollowButton();}return false;};logScroll.setOnTouchListener(browse);log.setOnTouchListener(browse);updateFollowButton();
    }
    private void updateFollowButton(){if(followButton!=null){followButton.setText(tr(followLog?"跟随最新":"查看历史"));followButton.setTextColor(followLog?BG:PURPLE);followButton.setBackground(bg(followLog?PURPLE:SURFACE,10));}}
    private void scrollLatest(){if(logScroll!=null)logScroll.post(()->{if(logScroll!=null&&followLog)logScroll.fullScroll(View.FOCUS_DOWN);});}
    private static boolean logWarning(String line){String lower=line.toLowerCase(Locale.ROOT);return lower.contains("error")||lower.contains("warn")||line.contains("异常")||line.contains("错误")||line.contains("暂停")||line.contains("重试")||java.util.regex.Pattern.compile("(?:refused|rejected)[= :]+[1-9]",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line).find();}
    private void refreshLogs(){
        android.text.SpannableStringBuilder filtered=new android.text.SpannableStringBuilder();int count=0;
        for(String line:MinerState.logText().split("\n")){
            String lower=line.toLowerCase(Locale.ROOT);boolean share=line.contains("份额")||lower.contains("share");boolean warning=logWarning(line);
            if(line.isEmpty()||!(logFilter==0||logFilter==1&&share||logFilter==2&&warning))continue;
            if(filtered.length()>0)filtered.append('\n');int start=filtered.length();filtered.append(tr(line));count++;
            if(warning||share)filtered.setSpan(new android.text.style.ForegroundColorSpan(warning?WARNING:GREEN),start,filtered.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        CharSequence shown=filtered.length()==0?tr("当前筛选暂无日志"):filtered;
        if(!shown.toString().contentEquals(log.getText())){log.setText(shown);if(followLog)scrollLatest();}
        logSummary.setText(tr(MinerState.status+" · "+count+" 行 / 最多 240 行 · 接受 "+MinerState.accepted+" / 拒绝 "+MinerState.rejected));
        if(paintedLogFilter!=logFilter){paintedLogFilter=logFilter;for(int i=0;i<logFilters.size();i++){logFilters.get(i).setTextColor(i==logFilter?BG:PURPLE);logFilters.get(i).setBackground(bg(i==logFilter?PURPLE:SURFACE,10));}}
    }
    private void toggle(){
        if(MinerState.active){MinerState.status="停止中";MinerState.detail="正在释放内存";startService(new Intent(this,MiningService.class).setAction(MiningService.STOP));refresh();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);return;}
        if(!Settings.prefs(this,selected).getBoolean("confirmed",false)||Settings.prefs(this,selected).getInt("feeConsentVersion",0)<FeePolicy.VERSION){
            new LocalizedDialog(this).setTitle("开始在本机挖 "+selected).setMessage((selected==Coin.BNT?"BNT 约 2 GiB / 线程。":"JTM 使用 ARM64 TowerWalk 核心。")+"\n\n"+FeePolicy.description(selected)+"\n\n请核对收款地址。后台继续运行，可从通知栏停止；温控和仅充电选项按当前配置执行。").setNegativeButton("检查配置",(d,w)->{page=1;show();}).setPositiveButton("开始挖矿",(d,w)->{Settings.prefs(this,selected).edit().putBoolean("confirmed",true).putInt("feeConsentVersion",FeePolicy.VERSION).apply();startMining();}).show();
        } else startMining();
    }
    private void startMining(){startForegroundService(new Intent(this,MiningService.class));h.postDelayed(this::refresh,200);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==41){if(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)toggle();else toast("需要通知权限，便于查看后台状态和停止挖矿");}}
    private void about(){
        String license="";try(java.io.InputStream in=getAssets().open(selected==Coin.BNT?"SEINE-LICENSE.txt":"JTM-LICENSE.txt")){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);license=out.toString("UTF-8");}catch(Exception ignored){}
        new LocalizedDialog(this).setTitle("Gozero · 1.1.0").setMessage("Android 原生 BNT / JTM 矿工。两种币的地址、矿池和线程分别保存；同一时间运行一个核心。\n\nBNT：Seine 0.3.1。JTM：towerminer 0.3.1，ARM64 / NEON + PMULL。\n1% 开发费用\n\n内存为核心 RSS；算力与收益不同，收益以矿池为准。退出到桌面可显示小窗，关闭小窗不停止挖矿。\n\n"+license).setPositiveButton("关闭",null).show();
    }
    private void toast(String s){Toast.makeText(this,tr(s),Toast.LENGTH_SHORT).show();}
    private void cpuDetails(){
        CpuTemperature.Sample s=CpuTemperature.snapshot();long now=SystemClock.elapsedRealtime();
        StringBuilder message=new StringBuilder("主页显示最高核心温度；每 5 秒读取一次手机温度传感器，与电池温度分开显示。\n\n");
        if(s.fresh(now)){for(Map.Entry<String,Float> e:s.cores.entrySet())message.append(String.format(Locale.US,"%s  ·  %.1f °C\n",e.getKey(),e.getValue()));message.append("\n来源：系统 Thermal HAL 当前读数");}
        else if("未授权".equals(s.missing))message.append("需要通过 USB 调试授予一次诊断读取权限；授权后无需连接电脑。\n\nadb shell pm grant trade.gozero.bnt android.permission.DUMP");
        else message.append("系统未提供可读取的当前 CPU 温度，或读数已过期；不会使用电池温度或历史缓存替代。");
        new LocalizedDialog(this).setTitle("CPU 温度").setMessage(message).setPositiveButton("关闭",null).show();
    }
    private void refresh(){
        CpuTemperature.request(this);
        if(pendingCoin!=null&&!MinerState.active){completeSwitch(pendingCoin);return;}
        updateMini();
        if(hash!=null){resources.setText(tr(MinerState.runningThreads+" / "+new Settings(this,selected).threads+" 线程 · 核心 "+MinerState.memoryText()));hash.setText(tr(MinerState.rate>0?String.format(Locale.US,"%.2f",MinerState.rate):"—"));status.setText(tr(MinerState.status));status.setTextColor("挖矿中".equals(MinerState.status)?GREEN:PURPLE);detail.setText(tr(MinerState.detail));shares.setText(tr(MinerState.accepted+" / "+MinerState.rejected));
            long end=MinerState.stoppedAt==0?SystemClock.elapsedRealtime():MinerState.stoppedAt;
            long sec=MinerState.started==0?0:Math.max(0,end-MinerState.started)/1000;uptime.setText(tr(String.format(Locale.US,"%02d:%02d:%02d",sec/3600,sec/60%60,sec%60)));
            Intent b=MiningService.battery(this);float t=b==null?0:b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;temp.setText(tr(t>0?String.format(Locale.US,"%.1f°",t):"—"));battery.setText(tr(b==null?"—":b.getIntExtra(BatteryManager.EXTRA_LEVEL,0)+"%"));memory.setText(tr(String.format(Locale.US,"%.1f GiB",MiningService.memory(this).availMem/1073741824d)));
            cpuTemp.setText(tr(CpuTemperature.text()));CpuTemperature.Sample cpu=CpuTemperature.snapshot();cpuTemp.setTextColor(cpu.fresh(SystemClock.elapsedRealtime())&&cpu.maximum()>=85?WARNING:TEXT);
            avg.setText(tr(String.format(Locale.US,"会话均值 %.2f",MinerState.cumulative)));last.setText(tr(MinerState.last));chart.invalidate();action.setText(tr(pendingCoin!=null?"正在停止并切换…":MinerState.active?"停止挖 "+selected:"开始挖 "+selected));action.setEnabled(!"停止中".equals(MinerState.status));}
        if(log!=null)refreshLogs();
        if(memoryHint!=null){int count=threads.getSelectedItemPosition()+1;long available=MiningService.memory(this).availMem;
            String capacity=selected==Coin.BNT?String.format(Locale.US,"所选约 %d GiB · 当前可用 %.1f GiB",count*2,available/1073741824d):String.format(Locale.US,"%d 线程 · 工作区按核心分配 · 当前可用 %.1f GiB",count,available/1073741824d);
            String note=!MinerState.active&&available<Settings.requiredMemory(selected,count)?"当前内存不足，启动时将等待内存释放。":(selected==Coin.BNT?"每线程约 2 GiB，启动另预留 768 MiB。":"JTM 启动预留系统余量，实际占用见算力页。");
            memoryHint.setText(tr(capacity+"\n"+note));}
        if(save!=null){boolean enabled=!MinerState.active;save.setEnabled(enabled);save.setAlpha(enabled?1:.45f);pool.setEnabled(enabled);wallet.setEnabled(enabled);worker.setEnabled(enabled);threads.setEnabled(enabled);temperature.setEnabled(enabled);charging.setEnabled(enabled);displayMode.setEnabled(enabled);}
    }
    private final class MiniView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MiniView(){super(MainActivity.this);setContentDescription(tr("Gozero mini 实时算力、运行线程和核心内存"));}
        private void label(Canvas c,String value,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));value=tr(value);float maxWidth=y<50&&x<190?170:282-x;if(p.measureText(value)>maxWidth)p.setTextSize(size*maxWidth/p.measureText(value));c.drawText(value,x,y,p);}
        @Override protected void onDraw(Canvas canvas){
            canvas.drawColor(BG);float scale=Math.min(getWidth()/300f,getHeight()/150f);canvas.save();canvas.translate((getWidth()-300*scale)/2,(getHeight()-150*scale)/2);canvas.scale(scale,scale);
            p.setColor(PANEL);canvas.drawRoundRect(5,5,295,145,15,15,p);
            label(canvas,MinerState.coin+"  /  "+MinerState.status,18,32,14,PURPLE);
            label(canvas,"CPU "+CpuTemperature.text(),192,32,12,MUTED);
            label(canvas,String.format(Locale.US,"%.2f",MinerState.rate),18,84,42,TEXT);label(canvas,"H/s",177,81,19,PURPLE);
            label(canvas,MinerState.runningThreads+" 线程  ·  "+MinerState.memoryText()+" RSS",18,119,16,MUTED);
            canvas.restore();
        }
    }
    private final class Chart extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Chart(){super(MainActivity.this);setContentDescription(tr("最近五分钟算力曲线"));}
        @Override protected void onDraw(Canvas c){float w=getWidth(),height=getHeight();p.setColor(BORDER);p.setStrokeWidth(dp(.5f));for(int i=1;i<=3;i++)c.drawLine(0,height*i/3,w,height*i/3,p);
            float[] a=MinerState.chart();if(a.length<2){p.setColor(MUTED);p.setTextSize(dp(11));c.drawText(tr("首个采样到达后显示曲线"),0,height/2,p);return;}
            float max=1;for(float v:a)max=Math.max(max,v);Path line=new Path();for(int i=0;i<a.length;i++){float x=i*w/(a.length-1),y=height-dp(5)-a[i]/max*(height-dp(12));if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(PURPLE);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
        }
    }
}
