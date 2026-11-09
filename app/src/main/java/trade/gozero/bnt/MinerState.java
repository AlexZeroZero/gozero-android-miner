package trade.gozero.bnt;

import android.os.SystemClock;
import java.util.*;
import java.util.regex.*;

final class MinerState {
    static volatile Coin coin=Coin.BNT;
    private static final EnumMap<Coin,ArrayDeque<String>> history=new EnumMap<>(Coin.class);
    static synchronized void select(Coin next){if(active)return;history.put(coin,new ArrayDeque<>(logs));coin=next;newProcess();logs.clear();if(history.containsKey(next))logs.addAll(history.get(next));status="未启动";detail="准备就绪 · 点击开始挖矿";started=stoppedAt=0;coreStopped();}
    static volatile boolean active=false;
    static volatile boolean feeActive=false;
    static volatile long feeAccepted=0;
    private static long baseFeeAccepted=0;
    static volatile String status="未启动", detail="准备就绪 · 点击开始挖矿", last="等待核心启动";
    static volatile double rate=0, cumulative=0;
    static volatile long accepted=0, submitted=0, stale=0, rejected=0, started=0, stoppedAt=0;
    static volatile int runningThreads=0;
    static volatile long coreRssBytes=0;
    static volatile int nativeCapacity=0,configId=0;
    static volatile long nativeHashes=0,nativeBusyNs=0,nativeUptime=0;
    static volatile boolean configReady=false;
    static volatile String tuningDetail="";
    private static long baseAccepted=0,baseRejected=0,baseSubmitted=0,baseHashes=0,baseSeconds=0;
    private static int startingThreads=0;
    static final Pattern RESOURCE=Pattern.compile("^ANDROID_RESOURCE rss_kib=(\\d+)$");
    static final Pattern CPU_START=Pattern.compile("cpu: starting (\\d+) threads");
    static String memoryText(){long bytes=coreRssBytes;return bytes<0?"采样中":bytes<1073741824L?String.format(Locale.US,"%.1f MiB",bytes/1048576d):String.format(Locale.US,"%.2f GiB",bytes/1073741824d);}
    static synchronized void coreStopped(){runningThreads=0;startingThreads=0;coreRssBytes=0;rate=0;}
    private static double prevElapsed=-1; private static long prevHashes=0;
    static final ArrayDeque<String> logs=new ArrayDeque<>();
    static final ArrayDeque<Float> rates=new ArrayDeque<>();
    static final Pattern STATS=Pattern.compile("elapsed=([0-9.]+)s hashes=(\\d+) rate=([0-9.]+) H/s.*submitted=(\\d+) stale=(\\d+) accepted=(\\d+)");
    static synchronized void reset() {
        newProcess();tuningDetail="";
        rate=0; cumulative=0; accepted=0; submitted=0; stale=0; rejected=0; prevElapsed=-1; prevHashes=0;
        coreStopped(); stoppedAt=0; started=SystemClock.elapsedRealtime(); rates.clear(); logs.clear();
    }
    static synchronized void newProcess() { coreStopped();coreRssBytes=-1;prevElapsed=-1; prevHashes=0; rate=0; cumulative=0; accepted=0; submitted=0; stale=0; rejected=0; rates.clear();baseAccepted=baseRejected=baseSubmitted=baseHashes=baseSeconds=0;nativeCapacity=configId=0;nativeHashes=nativeBusyNs=nativeUptime=0;configReady=false;feeActive=false;feeAccepted=baseFeeAccepted=0; }
    static synchronized void resumeProcess(){
        resumeProcess(false);
    }
    static synchronized void resumeProcess(boolean fee){
        long a=accepted,r=rejected,s=submitted,h=baseHashes+nativeHashes,t=baseSeconds+nativeUptime,fa=feeAccepted;
        newProcess();baseAccepted=accepted=a;baseRejected=rejected=r;baseSubmitted=submitted=s;baseHashes=h;baseSeconds=t;feeActive=fee;baseFeeAccepted=feeAccepted=fa;
    }
    static synchronized void line(String raw) {
        String s=raw.replaceAll("\\x1B\\[[;\\d]*[ -/]*[@-~]", "");
        if(coin==Coin.JTM&&s.startsWith("{")){
            try{
                org.json.JSONObject j=new org.json.JSONObject(s);String type=j.optString("type");
                if("profile".equals(type)){runningThreads=j.optInt("threads");nativeCapacity=runningThreads;s="核心就绪 · "+runningThreads+" 线程 · "+j.optString("backend");}
                else if("status".equals(type)){
                    nativeHashes=j.optLong("hashes",nativeHashes);nativeBusyNs=j.optLong("busy_ns",nativeBusyNs);nativeUptime=j.optLong("uptime_s",nativeUptime);
                    configId=j.optInt("config_id",configId);configReady=j.optBoolean("config_ready",false);runningThreads=j.optInt("threads",runningThreads);
                    rate=Math.max(0,j.optDouble("hps",0));double seconds=baseSeconds+nativeUptime;cumulative=seconds>0?(baseHashes+nativeHashes)/seconds:0;
                    if(feeActive){feeAccepted=baseFeeAccepted+j.optLong("accepted",feeAccepted-baseFeeAccepted);}
                    else{accepted=baseAccepted+j.optLong("accepted",accepted-baseAccepted);rejected=baseRejected+j.optLong("refused",rejected-baseRejected);submitted=baseSubmitted+j.optLong("found",submitted-baseSubmitted);}
                    coreRssBytes=j.isNull("rss_bytes")?-1:j.optLong("rss_bytes",-1);
                    String state=j.optString("state");status="mining".equals(state)?"挖矿中":"error".equals(state)?"连接异常":"等待任务";
                    detail="mining".equals(state)?"TowerWalk · ARM64 · 每 5 秒更新":j.optString("message","等待矿池任务");
                    if(!tuningDetail.isEmpty())detail+=" · "+tuningDetail;
                    if(feeActive)detail="1% 手续费计算 · "+detail;
                    rates.add((float)rate);while(rates.size()>60)rates.removeFirst();
                    s=feeActive?String.format(Locale.US,"JTM 手续费 1%% · %.1f H/s · 手续费接受 %d",rate,feeAccepted):String.format(Locale.US,"JTM %.1f H/s · 接受 %d / 拒绝 %d · %s",rate,accepted,rejected,status);
                }else if("runtime".equals(type)){s="核心已接收调优 #"+j.optInt("config_id")+" · "+j.optInt("threads")+" 线程 · 工作区 "+j.optInt("pads");}
                else if("block".equals(type)||"share".equals(type)){s=(feeActive?"手续费份额 ":"份额 ")+j.optString("result")+" · 高度 "+j.optLong("height");}
                else if("error".equals(type)){s="核心错误："+j.optString("message");}
            }catch(org.json.JSONException ignored){}
        }
        Matcher resource=RESOURCE.matcher(s);
        if(resource.matches()){
            try{coreRssBytes=Math.multiplyExact(Long.parseLong(resource.group(1)),1024L);}catch(ArithmeticException|NumberFormatException ignored){coreRssBytes=-1;}
            return;
        }
        Matcher cpu=CPU_START.matcher(s);if(cpu.find())startingThreads=Integer.parseInt(cpu.group(1));
        if(s.contains("cpu#1: ready"))runningThreads=startingThreads;
        if(s.length()>2000)s=s.substring(0,2000);
        logs.addLast(s); while(logs.size()>240)logs.removeFirst(); last=s;
        Matcher m=STATS.matcher(s);
        if(m.find()) {
            double elapsed=Double.parseDouble(m.group(1)); long hashes=Long.parseLong(m.group(2));
            cumulative=Double.parseDouble(m.group(3));
            rate=prevElapsed>=0&&elapsed>prevElapsed&&hashes>=prevHashes?(hashes-prevHashes)/(elapsed-prevElapsed):cumulative;
            prevElapsed=elapsed;prevHashes=hashes;
            submitted=Long.parseLong(m.group(4));stale=Long.parseLong(m.group(5));accepted=Long.parseLong(m.group(6));
            rates.add((float)rate);while(rates.size()>60)rates.removeFirst();
        }
        if(s.contains("login accepted")){status="挖矿中";detail="已连接矿池 · 等待首个算力采样";}
        if(coin==Coin.BNT&&s.contains("SHARE")&&s.contains("rejected"))rejected++;
        if(s.contains("STATS")&&active){status="挖矿中";detail="CPU 正在计算 · 每 5 秒更新";}
    }
    static synchronized String logText(){return String.join("\n",logs);}
    static synchronized float[] chart(){float[] a=new float[rates.size()];int i=0;for(float v:rates)a[i++]=v;return a;}
}
