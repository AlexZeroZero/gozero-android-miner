package trade.gozero.bnt;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutoTunerTest {
    static final long RAM=4L*1024*1024*1024;
    @Test public void waitingStaleAndUnacknowledgedSamplesNeverScore(){
        AutoTuner t=new AutoTuner(8,4,true,null);t.commandSent(1);
        assertNull(t.observe(1,0,0,0,true));assertNull(t.observe(2,0,0,1,false));
        t.observe(3,0,0,1,true);
        for(int i=4;i<100;i++)assertNull(t.observe(i,0,0,1,true));
        assertTrue(t.scores.isEmpty());
        assertNotNull(t.observe(100,36000,60_000_000_000L,1,true));
        assertEquals(3000,t.scores.values().iterator().next().get(0),.01);
    }
    @Test public void counterResetCannotCreateWinningSpike(){
        AutoTuner t=new AutoTuner(5,4,true,null);t.commandSent(1);
        t.observe(10,10000,50_000_000_000L,1,true);
        assertNull(t.observe(11,10,1000,1,true));assertTrue(t.scores.isEmpty());
        t.newProcess();assertEquals(0,t.requestId);assertNull(t.observe(1,1,10,0,true));
    }
    @Test public void sweepFinishesWithinLimitsAndRechecksWinner(){
        AutoTuner t=new AutoTuner(8,4,true,null);int id=1;
        while(!t.complete&&id<40){
            assertTrue(t.valid(t.current));t.commandSent(id);
            t.observe(1,0,0,id,true);
            long hashes=t.current.threads==6&&t.current.pads==2?105000:90000;
            t.observe(31,hashes,30_000_000_000L*t.current.threads,id,true);id++;
        }
        assertTrue(t.complete);assertTrue(t.scores.get(t.best).size()>=2);
        assertTrue(t.valid(t.best));
        AutoTuner small=new AutoTuner(1,1,false,null);assertEquals(1,small.current.threads);
        assertFalse(small.valid(new AutoTuner.Profile(1,2,false)));
    }
    @Test public void nearTiesPreferLessMemoryAndThreads(){
        AutoTuner t=new AutoTuner(8,4,true,null);
        AutoTuner.Profile a=new AutoTuner.Profile(5,1,false),b=new AutoTuner.Profile(6,4,false);
        t.scores.put(a,new java.util.ArrayList<>(java.util.Arrays.asList(3000d,2980d)));
        t.scores.put(b,new java.util.ArrayList<>(java.util.Arrays.asList(3020d,3010d)));
        assertEquals(a,t.winner());
    }
    @Test public void warmChallengerAndPreviousGt5ProfileGetARealRecheck(){
        AutoTuner t=new AutoTuner(8,4,true,null);int id=1;
        while(!t.complete&&id<50){
            AutoTuner.Profile p=t.current;
            long rate=p.threads*350L;
            if(p.threads==8&&p.pads==1&&!p.prefetch)rate=t.phase==2?2500:3100;
            if(p.threads==7&&p.pads==0&&!p.prefetch)rate=2900;
            t.commandSent(id);t.observe(1,0,0,id,true);
            t.observe(31,rate*30,30_000_000_000L*p.threads,id,true);id++;
        }
        assertTrue(t.complete);assertEquals(new AutoTuner.Profile(7,0,false),t.best);
        assertTrue(t.scores.get(t.best).size()>=2);
    }
    @Test public void heatReductionAndRecoveryAreGradualAndBounded(){
        AutoTuner.Profile best=new AutoTuner.Profile(5,2,false);
        AutoTuner t=new AutoTuner(8,4,true,best);
        assertEquals(4,t.temperature(0,42,0,43,RAM).threads);
        assertNull(t.temperature(10000,42,0,43,RAM));
        assertEquals(3,t.temperature(30000,42,0,43,RAM).threads);
        assertNull(t.temperature(60000,38,0,43,RAM));
        assertEquals(3,t.temperature(90000,38,2,43,RAM).threads);assertFalse(t.heatHold);
        assertEquals(3,t.thermalCeiling);assertFalse(t.complete);
        assertNull(t.temperature(100000,38,0,43,RAM));
        assertEquals(4,t.temperature(220000,38,0,43,RAM).threads);
        assertNull(t.temperature(222000,38,0,43,RAM));
        assertEquals(5,t.temperature(342000,38,0,43,RAM).threads);
        t.thermalPause();assertEquals(4,t.current.threads);assertEquals(1,t.current.pads);
    }
    @Test public void restartKeepsJtmSharesAndActualRates(){
        MinerState.coin=Coin.JTM;MinerState.newProcess();
        try{
            MinerState.line("{\"type\":\"status\",\"hps\":100,\"hashes\":1000,\"uptime_s\":10,\"accepted\":3,\"refused\":1,\"found\":4}");
            MinerState.resumeProcess();
            MinerState.line("{\"type\":\"status\",\"hps\":200,\"hashes\":1000,\"uptime_s\":5,\"accepted\":2,\"refused\":0,\"found\":2,\"threads\":4,\"busy_ns\":10000,\"config_id\":3,\"config_ready\":true}");
            assertEquals(5,MinerState.accepted);assertEquals(1,MinerState.rejected);assertEquals(6,MinerState.submitted);
            assertEquals(200,MinerState.rate,.01);assertEquals(2000d/15,MinerState.cumulative,.01);
            assertEquals(4,MinerState.runningThreads);assertTrue(MinerState.configReady);
        }finally{MinerState.coin=Coin.BNT;MinerState.newProcess();}
    }
}
