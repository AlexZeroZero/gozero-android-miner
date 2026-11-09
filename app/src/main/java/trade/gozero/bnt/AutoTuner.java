package trade.gozero.bnt;

import java.util.*;

/** Live tuning uses completed hashes / worker busy time, never pool idle time.
 * Decisions only change work allocation; displayed hashrate remains native H/s. */
final class AutoTuner {
    static final class Profile {
        final int threads,pads; final boolean prefetch;
        Profile(int threads,int pads,boolean prefetch){this.threads=threads;this.pads=pads;this.prefetch=prefetch;}
        String key(){return threads+":"+pads+":"+(prefetch?1:0);}
        String label(){return threads+" 线程 · "+(pads==0?"按核工作区":(pads*512)+" KiB/线程")+(prefetch?" · 预取":"");}
        double memoryCost(){return threads*(pads==0?1.25:pads);}
        @Override public boolean equals(Object o){return o instanceof Profile&&key().equals(((Profile)o).key());}
        @Override public int hashCode(){return key().hashCode();}
    }
    final int maxThreads,maxPads;
    final boolean hybrid;
    final ArrayDeque<Profile> queue=new ArrayDeque<>();
    final LinkedHashMap<Profile,ArrayList<Double>> scores=new LinkedHashMap<>();
    Profile current,best;
    int requestId=0,phase=0,thermalCeiling;
    long lastUptime=-1,startHashes=-1,startBusy=0,lastHotChange=-120000,coolSince=-1;
    long lastHashes=0,lastBusy=0;
    boolean complete=false,heatHold=false,savePending=false;
    String message="准备实测",resultLine="";

    AutoTuner(int cap,int maxPads,boolean hybrid,Profile cached){
        this.maxThreads=Math.max(1,Math.min(8,cap));this.thermalCeiling=this.maxThreads;this.maxPads=maxPads>=4?4:maxPads>=2?2:1;this.hybrid=hybrid;
        if(valid(cached)){best=cached;current=cached;complete=true;message="已载入实测配置";}
        else{
            LinkedHashSet<Integer> counts=new LinkedHashSet<>();
            for(int n:new int[]{5,4,6,3,7,8,2})counts.add(Math.min(n,this.maxThreads));
            for(int n:counts)queue.add(new Profile(n,1,false));
            current=queue.removeFirst();message="实测线程组合";
        }
    }
    boolean valid(Profile p){return p!=null&&p.threads>=1&&p.threads<=maxThreads&&
        (p.pads==1||p.pads==2||p.pads==4||p.pads==0&&hybrid)&&
        (p.pads!=0?p.pads<=maxPads:maxPads>=2);}
    void resetWindow(){startHashes=-1;lastUptime=-1;}
    void commandSent(int id){requestId=id;resetWindow();}
    void newProcess(){requestId=0;resetWindow();}
    void rescanWithin(int ceiling){
        thermalCeiling=Math.max(1,Math.min(maxThreads,ceiling));
        heatHold=false;complete=false;best=null;scores.clear();queue.clear();phase=0;resetWindow();
        for(int n=thermalCeiling;n>=1;n--)if(n!=current.threads)queue.add(new Profile(n,1,false));
        message="按当前散热重新实测";
    }
    double conservative(Profile p){ArrayList<Double> a=scores.get(p);return a==null||a.isEmpty()?0:Collections.min(a);}
    Profile winner(){
        Profile pick=null;double fastest=0;
        for(Profile p:scores.keySet())fastest=Math.max(fastest,conservative(p));
        for(Profile p:scores.keySet())if(conservative(p)>=fastest*.98 &&
            (pick==null||p.threads<pick.threads||p.threads==pick.threads&&p.memoryCost()<pick.memoryCost()||
             p.threads==pick.threads&&p.memoryCost()==pick.memoryCost()&&!p.prefetch))pick=p;
        return pick==null?current:pick;
    }
    Profile observe(long uptime,long hashes,long busy,int configId,boolean ready){
        if(heatHold||complete||configId!=requestId||!ready||uptime<=lastUptime)return null;
        lastUptime=uptime;
        if(startHashes<0||hashes<lastHashes||busy<lastBusy){startHashes=hashes;startBusy=busy;lastHashes=hashes;lastBusy=busy;return null;}
        lastHashes=hashes;lastBusy=busy;
        double activeSeconds=(busy-startBusy)/1e9/current.threads;
        if(activeSeconds<(phase==2?25:12)||hashes-startHashes<256)return null;
        double hps=(hashes-startHashes)/activeSeconds;
        if(!Double.isFinite(hps)||hps<=0){resetWindow();return null;}
        scores.computeIfAbsent(current,p->new ArrayList<>()).add(hps);
        resultLine=String.format(Locale.US,"自动实测 · %s · 有效计算 %.1f H/s",current.label(),hps);
        if(queue.isEmpty()&&phase==0){
            best=winner();phase=1;
            // Include the previous GT5 profile even when a different thread
            // count wins the first pass; thread/pad choices interact.
            if(hybrid&&maxPads>=2&&thermalCeiling>=7&&best.threads!=7)queue.add(new Profile(7,0,false));
            if(maxPads>=2)queue.add(new Profile(best.threads,2,false));
            if(maxPads>=4)queue.add(new Profile(best.threads,4,false));
            if(hybrid&&maxPads>=2)queue.add(new Profile(best.threads,0,false));
            queue.add(new Profile(best.threads,1,true));
            if(maxPads>=2)queue.add(new Profile(best.threads,2,true));
            if(maxPads>=4)queue.add(new Profile(best.threads,4,true));
            if(hybrid&&maxPads>=2)queue.add(new Profile(best.threads,0,true));
            message="实测工作区 / 预取";
        }else if(queue.isEmpty()&&phase==1){
            best=winner();phase=2;
            // Revisit ALL plausible challengers in reverse order. Testing only
            // the cold winner can discard a faster warmed-up memory strategy.
            double cutoff=conservative(best)*.85;
            ArrayList<Profile> finalists=new ArrayList<>(scores.keySet());Collections.reverse(finalists);
            for(Profile p:finalists)if(!p.equals(best)&&conservative(p)>=cutoff)queue.add(p);
            queue.add(best);message="复核持续算力";
        }else if(queue.isEmpty()&&phase==2){
            // Only profiles actually revisited in the final pass can be selected.
            scores.entrySet().removeIf(e->e.getValue().size()<2);
            best=winner();complete=true;savePending=true;current=best;message="已选择实测配置";
            return best;
        }
        if(!queue.isEmpty()){current=queue.removeFirst();return current;}
        return null;
    }
    Profile temperature(long now,float battery,int thermal,int limit,long availableBytes){
        boolean hot=battery>=limit-1.5f||thermal>=3;
        boolean lowMemory=availableBytes<1024L*1024*1024;
        if((hot||lowMemory)&&now-lastHotChange>=30000){
            int threads=hot?Math.max(1,current.threads-1):current.threads;
            Profile reduced=new Profile(threads,1,false);
            heatHold=true;thermalCeiling=threads;coolSince=-1;lastHotChange=now;resetWindow();
            message=hot?"提前减载，减少温控停机":"内存余量低，收缩工作区";
            if(!reduced.equals(current)){current=reduced;return reduced;}
        }
        if(heatHold){
            // Moderate Android throttling alone is not a reason to stay stuck
            // forever: after stabilization, test pads/threads below the new cap.
            if(!hot&&!lowMemory&&now-lastHotChange>=60000){
                rescanWithin(current.threads);coolSince=-1;return current;
            }
        }
        if(!heatHold&&thermalCeiling<maxThreads){
            if(battery<=limit-3&&thermal<=1&&!lowMemory){
                if(coolSince<0)coolSince=now;
                if(now-coolSince>=120000){
                    int raised=thermalCeiling+1;current=new Profile(raised,1,false);
                    rescanWithin(raised);coolSince=-1;return current;
                }
            }else coolSince=-1;
        }
        return null;
    }
    void thermalPause(){
        current=new Profile(Math.max(1,current.threads-1),1,false);
        heatHold=true;thermalCeiling=current.threads;coolSince=-1;message="冷却后以较低负载恢复";resetWindow();
    }
}
