package trade.gozero.bnt;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.os.*;
import java.io.*;
import java.lang.Process;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class MiningService extends Service {
    static final String STOP="trade.gozero.bnt.STOP";
    private volatile boolean requested;
    private volatile Process process;
    private Thread worker;
    private PowerManager.WakeLock wake;
    private final ThermalGuard thermalGuard=new ThermalGuard();
    private Settings settings;
    private AutoTuner tuner;
    private int commandId;
    private JtmFeeLedger feeLedger;
    private SharedPreferences feePrefs;
    private void checkpointFee() throws IOException {
        if(feeLedger!=null&&feeLedger.observe(MinerState.nativeHashes)){
            if(!feePrefs.edit().putLong("balance",feeLedger.balance).commit())throw new IOException("手续费计算记录保存失败，请检查存储空间");
        }
    }
    private void deferFee() throws IOException {
        feeLedger.defer(System.currentTimeMillis());
        if(!feePrefs.edit().putLong("retryAt",feeLedger.retryAt).commit())throw new IOException("手续费重试记录保存失败");
    }
    private boolean adaptive(){return settings.coin==Coin.JTM&&settings.jtmMode==3;}
    private void tune(Process current) throws IOException {
        int capacity;long uptime,hashes,busy;int id;boolean ready;
        synchronized(MinerState.class){capacity=MinerState.nativeCapacity;uptime=MinerState.nativeUptime;hashes=MinerState.nativeHashes;busy=MinerState.nativeBusyNs;id=MinerState.configId;ready=MinerState.configReady;}
        if(capacity<1)return;
        if(tuner==null||tuner.maxThreads!=capacity)tuner=new AutoTuner(capacity,settings.jtmMaxPads,"RMX3823".equals(Build.MODEL),null);
        AutoTuner.Profile next=tuner.requestId==0?tuner.current:null;
        Intent b=battery(this);float temp=b==null?0:b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;
        int thermal=Build.VERSION.SDK_INT>=29?getSystemService(PowerManager.class).getCurrentThermalStatus():0;
        AutoTuner.Profile thermalProfile=tuner.temperature(SystemClock.elapsedRealtime(),temp,thermal,settings.temperature,memory(this).availMem);
        if(thermalProfile!=null)next=thermalProfile;
        if(next==null)next=tuner.observe(uptime,hashes,busy,id,ready);
        if(!tuner.resultLine.isEmpty()){MinerState.line(tuner.resultLine);tuner.resultLine="";}
        if(next!=null){
            int serial=++commandId;
            String json="{\"id\":"+serial+",\"threads\":"+next.threads+",\"pads\":"+next.pads+",\"prefetch\":"+next.prefetch+"}\n";
            current.getOutputStream().write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));current.getOutputStream().flush();tuner.commandSent(serial);
            MinerState.line("自动调节 · "+tuner.message+" · "+next.label());
        }
        MinerState.tuningDetail=tuner.message+" · "+tuner.current.label();
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("mining","挖矿状态",NotificationManager.IMPORTANCE_LOW));
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GozeroBNT:Mining");
        wake.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&STOP.equals(i.getAction())){requested=false;MinerState.status="停止中";if(worker==null)stopSelf();return START_NOT_STICKY;}
        if(worker!=null&&worker.isAlive())return START_NOT_STICKY;
        if(requested)return START_NOT_STICKY;
        settings=new Settings(this);
        if(Settings.prefs(this,settings.coin).getInt("feeConsentVersion",0)<FeePolicy.VERSION){MinerState.status="请确认费率";MinerState.detail="请从主页确认 1% 开发费后启动";stopSelf();return START_NOT_STICKY;}
        String error=Settings.validate(settings.coin,settings.pool,settings.wallet,settings.worker);
        if(error!=null){MinerState.status="配置无效";MinerState.detail=error;stopSelf();return START_NOT_STICKY;}
        if(settings.coin==Coin.JTM){feePrefs=getSharedPreferences("jtm_fee_v1",0);feeLedger=new JtmFeeLedger(feePrefs.getLong("balance",0));feeLedger.retryAt=Math.min(feePrefs.getLong("retryAt",0),System.currentTimeMillis()+JtmFeeLedger.RETRY_MS);}else{feePrefs=null;feeLedger=null;}
        MinerState.coin=settings.coin;requested=true;MinerState.active=true;MinerState.reset();
        MinerState.status="准备中";MinerState.detail="正在检查设备与核心";
        Notification n=notification();
        if(Build.VERSION.SDK_INT>=34)startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(7,n);
        worker=new Thread(this::run,"bnt-controller");worker.start();
        return START_NOT_STICKY;
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MiningService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String title=String.format(Locale.US,"%.2f H/s · %s",MinerState.rate,MinerState.status);
        String resources=MinerState.runningThreads+" 线程 · 核心内存 "+MinerState.memoryText()+" · CPU "+CpuTemperature.text();
        return new Notification.Builder(this,"mining").setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(resources).setSubText(settings.coin+" · 1% 开发费用"+(MinerState.feeActive?" · 手续费计算中":""))
            .setStyle(new Notification.BigTextStyle().bigText(resources+"\n有效份额 "+MinerState.accepted+" · "+MinerState.detail))
            .setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"停止挖矿",stop).build()).build();
    }
    private void publish(){CpuTemperature.request(this);if(requested)getSystemService(NotificationManager.class).notify(7,notification());}
    static ActivityManager.MemoryInfo memory(Context c){ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();((ActivityManager)c.getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(m);return m;}
    static Intent battery(Context c){return c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}
    private String guard(boolean starting){
        Intent b=battery(this);
        float temp=b==null?0:b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;
        boolean charging=b!=null&&b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        int thermal=Build.VERSION.SDK_INT>=29?getSystemService(PowerManager.class).getCurrentThermalStatus():0;
        String thermalReason=thermalGuard.reason(temp,thermal,settings.temperature,SystemClock.elapsedRealtime());
        if(thermalReason!=null)return thermalReason;
        if(settings.chargingOnly&&!charging)return "等待接通电源（仅充电时挖矿）";
        if(b!=null&&b.getIntExtra(BatteryManager.EXTRA_LEVEL,100)<15&&!charging)return "电量低于 15%，请接通电源";
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());
        if(cap==null||!cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return "等待网络连接";
        ActivityManager.MemoryInfo m=memory(this);
        if(m.lowMemory||m.availMem<384L*1024*1024)return "可用内存不足，等待系统释放内存";
        if(starting&&m.availMem<Settings.requiredMemory(settings.coin,settings.threads))return settings.coin==Coin.BNT?"启动需约 "+(settings.threads*2)+" GiB + 768 MiB 可用内存，请关闭其他应用":"可用内存不足，JTM 启动需预留约 832 MiB";
        return null;
    }
    private void halt(){
        Process p=process;
        if(p!=null){
            boolean interrupted=false;
            // Android Process.destroy closes stdout before the final accounting
            // event can be read. JTM stops over stdin while keeping stdout open.
            if(settings.coin==Coin.JTM&&p.isAlive()){
                try{p.getOutputStream().write("{\"stop\":true}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));p.getOutputStream().flush();}
                catch(IOException ignored){p.destroy();}
            }else p.destroy();
            try{if(!p.waitFor(5,TimeUnit.SECONDS))p.destroyForcibly();}
            catch(InterruptedException e){interrupted=true;p.destroyForcibly();}
            while(p.isAlive()){try{p.waitFor();}catch(InterruptedException e){interrupted=true;p.destroyForcibly();}}
            if(interrupted)Thread.currentThread().interrupt();
        }
        process=null;
        if(wake.isHeld())wake.release();
        MinerState.coreStopped();
    }
    private void run(){
        int failures=0;
        try {
            File core=new File(getApplicationInfo().nativeLibraryDir,settings.coin.binary);
            if(!core.canExecute())throw new IOException("当前架构没有可执行核心："+Build.SUPPORTED_ABIS[0]);
            File data=new File(getFilesDir(),"seine-data");if(!data.exists()&&!data.mkdirs())throw new IOException("无法建立核心目录");
            MinerState.line("Gozero 1.0.0 · "+settings.coin+" · "+settings.coin.core+" · "+Build.MODEL);
            MinerState.line(FeePolicy.description(settings.coin));
            while(requested){
                String reason=guard(true);
                if(reason!=null){if(!"已暂停".equals(MinerState.status))MinerState.line(reason);MinerState.status="已暂停";MinerState.detail=reason;publish();Thread.sleep(1000);continue;}
                final boolean feeRound=feeLedger!=null&&feeLedger.startFee(System.currentTimeMillis());
                if(settings.coin==Coin.JTM){MinerState.resumeProcess(feeRound);feeLedger.beginProcess(feeRound);}else MinerState.newProcess();
                String payout=FeePolicy.payout(settings.coin,feeRound,settings.wallet);
                String payoutWorker=FeePolicy.worker(feeRound,settings.worker);
                if(feeRound)MinerState.line("开始 1% 手续费计算；完成额度后自动回到用户地址");
                if(tuner!=null)tuner.newProcess();commandId=0;
                MinerState.status="连接中";MinerState.detail="正在连接 "+settings.pool;publish();
                List<String> args=new ArrayList<>(Arrays.asList(core.getAbsolutePath(),"--mode","pool","--pool-url",settings.pool,"--address",settings.wallet,"--pool-worker",settings.worker,
                    "--backend","cpu","--threads",Integer.toString(settings.threads),"--disable-cpu-autotune-threads","--cpu-page-mode","regular","--cpu-affinity","off",
                    "--ui","plain","--stats-secs","5","--telemetry","off","--no-auto-update","--disable-update-check","--data-dir",data.getAbsolutePath()));
                if(settings.coin==Coin.JTM)args=new ArrayList<>(Arrays.asList(core.getAbsolutePath(),"--rpc",settings.pool,"--worker-name",payoutWorker,"--threads",Integer.toString(settings.threads),"--no-tune-file","--no-huge","--status-json","--runtime-control","--report-secs","5","--priority","normal"));
                if(settings.coin==Coin.JTM){
                    int pads=adaptive()?1:Settings.jtmPads(settings.jtmMode,Build.MODEL);
                    args.addAll(Arrays.asList("--pads",Integer.toString(pads)));
                    boolean hybrid=(settings.jtmMode==0||adaptive())&&"RMX3823".equals(Build.MODEL);
                    String singlePadCpus=Settings.jtmSinglePadCpus(Build.MODEL,Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"");
                    if(adaptive()){
                        args.addAll(Arrays.asList("--seed-batch","8"));
                        if(hybrid){args.set(args.indexOf("--pads")+1,"2");args.addAll(Arrays.asList("--single-pad-cpus",singlePadCpus));}
                    }
                    if(hybrid&&!adaptive())args.addAll(Arrays.asList("--single-pad-cpus",singlePadCpus,"--seed-batch","8"));
                    MinerState.line("JTM 计算策略 · "+(hybrid?"按核心优化工作区":pads==2?"双工作区交错计算":"标准单工作区")+" · "+settings.threads+" 线程");
                }
                ProcessBuilder pb=new ProcessBuilder(args).directory(getFilesDir()).redirectErrorStream(true);
                if(settings.coin==Coin.JTM)pb.environment().put("TOWERMINER_KEY",payout+"."+payoutWorker);
                pb.environment().put("BNT_ANDROID_STATS","1");
                pb.environment().put("GOZERO_DEVICE_MODEL",Build.MODEL);
                pb.environment().put("GOZERO_SOC_MODEL",Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"");
                pb.environment().put("HOME",getFilesDir().getAbsolutePath());pb.environment().put("TMPDIR",getCacheDir().getAbsolutePath());pb.environment().put("NO_COLOR","1");
                process=pb.start();if(settings.coin!=Coin.JTM)process.getOutputStream().close();
                if(!requested){halt();break;}
                wake.acquire(10*60*1000L);
                final Process current=process;
                Thread reader=new Thread(()->{try(BufferedReader r=new BufferedReader(new InputStreamReader(current.getInputStream()))){String line;while((line=r.readLine())!=null)MinerState.line(line);}catch(IOException ignored){}},"bnt-output");reader.start();
                boolean paused=false,feeSwitch=false;long launched=SystemClock.elapsedRealtime();
                while(requested&&current.isAlive()){
                    Thread.sleep(1000);if(!requested)break;
                    checkpointFee();
                    if(adaptive()){
                        try{tune(current);}catch(IOException e){MinerState.line("调参通道中断，重新连接核心");halt();break;}
                    }
                    reason=guard(false);
                    if(reason!=null){if(thermalGuard.isCooling()&&tuner!=null)tuner.thermalPause();paused=true;halt();reader.join();checkpointFee();MinerState.coreStopped();MinerState.status="已暂停";MinerState.detail=reason;MinerState.line(reason);publish();break;}
                    if(feeLedger!=null&&(feeRound?(feeLedger.paid()||SystemClock.elapsedRealtime()-launched>=JtmFeeLedger.MAX_ROUND_MS):feeLedger.startFee(System.currentTimeMillis()))){
                        feeSwitch=true;halt();reader.join();checkpointFee();
                        if(feeRound){deferFee();MinerState.line("手续费轮次结束，恢复用户地址计算");}
                        break;
                    }
                    wake.acquire(10*60*1000L);publish();
                }
                if(!requested){halt();reader.join();checkpointFee();break;}
                if(feeSwitch)continue;
                if(paused)continue;
                int code=current.waitFor();reader.join();checkpointFee();halt();
                if(feeRound){deferFee();MinerState.line("手续费连接退出，先恢复用户挖矿，10 分钟后再尝试");continue;}
                if(SystemClock.elapsedRealtime()-launched>120000)failures=0;
                failures++;
                MinerState.line("核心退出，代码 "+code+"；重试 "+failures+"/3");
                if(failures>=3)throw new IOException("核心多次退出，请查看日志；设置保持不变");
                MinerState.status="重连中";MinerState.detail="10 秒后重试";publish();Thread.sleep(10000);
            }
        } catch(Exception e){if(requested){MinerState.status="运行异常";MinerState.detail=e.getMessage()==null?e.toString():e.getMessage();MinerState.line(MinerState.detail);}}
        finally{halt();requested=false;main.post(()->{MinerState.stoppedAt=SystemClock.elapsedRealtime();MinerState.active=false;if(!"运行异常".equals(MinerState.status)){MinerState.status="已停止";MinerState.detail="挖矿已停止，可随时重新开始";}stopSelf();});}
    }
    @Override public void onDestroy(){requested=false;if(worker==null)MinerState.active=false;if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
