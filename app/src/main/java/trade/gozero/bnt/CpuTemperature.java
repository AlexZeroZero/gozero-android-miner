package trade.gozero.bnt;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Local, read-only thermal diagnostics. Never substitutes battery/skin or cached HAL values. */
final class CpuTemperature {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cpu-temperature");t.setDaemon(true);return t;});
    private static final Pattern CPU=Pattern.compile("Temperature\\{mValue=([^,]+),\\s*mType=0,\\s*mName=([^,}]+),");
    static final long FRESH_MS=15000;
    static final class Sample {
        final Map<String,Float> cores;final long time;final String missing;
        Sample(Map<String,Float> cores,long time,String missing){this.cores=Collections.unmodifiableMap(new TreeMap<>(cores));this.time=time;this.missing=missing;}
        float maximum(){float max=Float.NaN;for(float v:cores.values())if(Float.isNaN(max)||v>max)max=v;return max;}
        boolean fresh(long now){return !cores.isEmpty()&&now>=time&&now-time<=FRESH_MS;}
        String display(long now){return fresh(now)?String.format(Locale.US,"%.1f°",maximum()):cores.isEmpty()?missing:"不可用";}
    }
    private static volatile Sample latest=new Sample(Collections.emptyMap(),0,"采样中");
    private static boolean pending;
    private static long lastRequest=-5000;
    static Sample snapshot(){return latest;}
    static String text(){return latest.display(SystemClock.elapsedRealtime());}
    static Map<String,Float> parseCurrent(String raw){
        TreeMap<String,Float> cores=new TreeMap<>();
        int start=raw.indexOf("Current temperatures from HAL:");
        if(start<0)return cores;
        String current=raw.substring(start);
        for(String end:new String[]{"Current cooling devices", "Temperature thresholds", "Cached temperatures", "HAL connection:"}){
            int i=current.indexOf(end);if(i>=0)current=current.substring(0,i);
        }
        Matcher m=CPU.matcher(current);
        while(m.find())try{float v=Float.parseFloat(m.group(1).trim());if(Float.isFinite(v)&&v>=-20&&v<=150)cores.put(m.group(2).trim(),v);}catch(NumberFormatException ignored){}
        return cores;
    }
    static synchronized void request(Context context){
        long now=SystemClock.elapsedRealtime();
        if(pending||now-lastRequest<5000)return;
        lastRequest=now;
        if(context.checkSelfPermission(Manifest.permission.DUMP)!=PackageManager.PERMISSION_GRANTED){latest=new Sample(Collections.emptyMap(),now,"未授权");return;}
        pending=true;
        IO.execute(()->{
            Process p=null;Sample result=new Sample(Collections.emptyMap(),SystemClock.elapsedRealtime(),"不可用");
            try{
                p=new ProcessBuilder("/system/bin/dumpsys","-t","2","thermalservice").redirectErrorStream(true).start();
                if(p.waitFor(3,TimeUnit.SECONDS)&&p.exitValue()==0){
                    try(InputStream in=p.getInputStream()){
                        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
                        while(out.size()<=65536&&(n=in.read(block))!=-1)out.write(block,0,n);
                        byte[] bytes=out.toByteArray();
                        if(bytes.length<=65536)result=new Sample(parseCurrent(new String(bytes,StandardCharsets.UTF_8)),SystemClock.elapsedRealtime(),"不可用");
                    }
                }
            }catch(Exception ignored){}finally{
                if(p!=null&&p.isAlive())p.destroyForcibly();
                synchronized(CpuTemperature.class){latest=result;pending=false;}
            }
        });
    }
}
