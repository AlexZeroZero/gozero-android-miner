package trade.gozero.bnt;
import org.junit.Test;
import static org.junit.Assert.*;
public class StatsAndSettingsTest {
    @Test public void x3MixedWorkspaceRequiresMeasuredModelAndSoc(){
        assertEquals("0-2,5-6",Settings.jtmSinglePadCpus("RMX3823","SM8550"));
        assertEquals("0-2,5-7",Settings.jtmSinglePadCpus("RMX3823","SM8650"));
        assertEquals("0-2,5-7",Settings.jtmSinglePadCpus("RMX3820","SM8550"));
        assertEquals("0-2,5-7",Settings.jtmSinglePadCpus("RMX3823",""));
    }
    @Test public void measuredProfileIsScopedAndManualChoiceWins(){
        assertEquals(2,Settings.jtmPads(0,"RMX3823"));
        assertEquals(1,Settings.jtmPads(0,"SM-S9210"));
        assertEquals(1,Settings.jtmPads(0,"unknown"));
        assertEquals(1,Settings.jtmPads(1,"RMX3823"));
        assertEquals(2,Settings.jtmPads(2,"SM-S9210"));
    }
    @Test public void jtmLiveProtocolUpdatesCountersAndResources(){
        MinerState.coin=Coin.JTM;MinerState.newProcess();
        try{
            MinerState.line("{\"type\":\"profile\",\"threads\":2,\"backend\":\"neon+pmull\"}");
            MinerState.line("{\"type\":\"status\",\"hps\":1104.5,\"hashes\":26386,\"uptime_s\":25,\"accepted\":3,\"refused\":0,\"found\":3,\"rss_bytes\":9412608,\"state\":\"mining\"}");
            assertEquals(2,MinerState.runningThreads);assertEquals(1104.5,MinerState.rate,.01);assertEquals(1055.44,MinerState.cumulative,.01);
            assertEquals(3,MinerState.accepted);assertEquals(9412608,MinerState.coreRssBytes);assertEquals("挖矿中",MinerState.status);
            MinerState.line("{\"type\":\"share\",\"height\":51013,\"result\":\"accepted\"}");
            assertTrue(MinerState.last.startsWith("份额 accepted"));assertEquals(3,MinerState.accepted);
            MinerState.line("{\"type\":\"status\",\"state\":\"error\",\"rss_bytes\":null,\"message\":\"RPC 20\"}");
            assertEquals(-1,MinerState.coreRssBytes);assertEquals("连接异常",MinerState.status);assertEquals("RPC 20",MinerState.detail);
        }finally{MinerState.coin=Coin.BNT;MinerState.newProcess();}
    }
    @Test public void jtmAddressChecksumAndCoinIsolation(){
        assertTrue(Settings.validJtmAddress(Settings.JTM_WALLET));
        assertFalse(Settings.validJtmAddress(Settings.JTM_WALLET.substring(0,59)+"q"));
        assertFalse(Settings.validJtmAddress(Settings.WALLET));
        assertNull(Settings.validate(Coin.JTM,Coin.JTM.pool,Settings.JTM_WALLET,"gt5"));
        assertNotNull(Settings.validate(Coin.JTM,"http://jtm.pool.gozero.trade/rpc",Settings.JTM_WALLET,"gt5"));
        assertNotNull(Settings.validate(Coin.JTM,Coin.BNT.pool,Settings.JTM_WALLET,"gt5"));
        assertNotNull(Settings.validate(Coin.BNT,Coin.BNT.pool,Settings.JTM_WALLET,"gt5"));
        assertTrue(Settings.requiredMemory(Coin.JTM,8)<1024L*1024*1024);
    }
    @Test public void windowRateDoesNotUseCumulativeAverage(){
        MinerState.newProcess();
        MinerState.line("elapsed=15.0s hashes=30 rate=2.000 H/s templates=1 submitted=2 stale=0 accepted=2 deferred=0 dropped=0");
        MinerState.line("elapsed=20.0s hashes=55 rate=2.750 H/s templates=1 submitted=3 stale=0 accepted=3 deferred=0 dropped=0");
        assertEquals(5.0,MinerState.rate,.0001);assertEquals(2.75,MinerState.cumulative,.0001);assertEquals(3L,MinerState.accepted);
    }
    @Test public void restartClearsPreviousCounter(){
        MinerState.newProcess();
        MinerState.line("elapsed=5.0s hashes=10 rate=2.000 H/s templates=1 submitted=0 stale=0 accepted=0 deferred=0 dropped=0");
        assertEquals(2.0,MinerState.rate,.0001);assertEquals(0L,MinerState.accepted);
    }
    @Test public void invalidPoolOrWalletCannotBeSaved(){
        assertNull(Settings.validate("stratum+tcp://bntpool.com:3333",Settings.WALLET,"samsung-s24"));
        assertNull(Settings.validate("stratum+ssl://bntpool.com:443",Settings.WALLET,"s24"));
        assertNotNull(Settings.validate("https://bntpool.com/",Settings.WALLET,"s24"));
        assertNotNull(Settings.validate("stratum+tcp://bntpool.com:99999",Settings.WALLET,"s24"));
        assertNotNull(Settings.validate("stratum+tcp://bntpool.com:3333","not-a-wallet","s24"));
        assertNotNull(Settings.validate("stratum+tcp://bntpool.com:3333",Settings.WALLET,"bad worker"));
    }
    @Test public void logsAndChartAreBounded(){
        MinerState.newProcess();
        for(int i=1;i<=400;i++)MinerState.line("elapsed="+i+".0s hashes="+(i*2)+" rate=2.000 H/s templates=1 submitted=0 stale=0 accepted=0 deferred=0 dropped=0");
        assertTrue(MinerState.logs.size()<=240);assertTrue(MinerState.chart().length<=60);
    }
    @Test public void resourcesReflectCoreSamplesAndReadyWorkers(){
        MinerState.newProcess();assertEquals(0,MinerState.runningThreads);assertEquals(-1L,MinerState.coreRssBytes);
        MinerState.line("0.0s INFO BACKEND cpu: starting 2 threads (~4.0 GiB RAM)");
        assertEquals(0,MinerState.runningThreads);
        MinerState.line("0.0s INFO BACKEND cpu#1: ready in 0.0s");assertEquals(2,MinerState.runningThreads);
        int count=MinerState.logs.size();
        MinerState.line("ANDROID_RESOURCE rss_kib=4194304");assertEquals(4294967296L,MinerState.coreRssBytes);assertEquals(count,MinerState.logs.size());
        MinerState.coreStopped();assertEquals(0,MinerState.runningThreads);assertEquals(0L,MinerState.coreRssBytes);
        MinerState.newProcess();assertEquals(-1L,MinerState.coreRssBytes);
        MinerState.line("ANDROID_RESOURCE rss_kib=9999999999999999999999");assertEquals(-1L,MinerState.coreRssBytes);
    }
    @Test public void expandedThreadsRetainValuesAndUseLongMemoryBudget(){
        assertEquals(8,Settings.threadOptions().length);
        assertEquals(4,Settings.clampThreads(4));assertEquals(8,Settings.clampThreads(8));
        assertEquals(1,Settings.clampThreads(0));assertEquals(8,Settings.clampThreads(99));
        assertEquals(8L*1024*1024*1024+768L*1024*1024,Settings.requiredMemory(4));
        assertEquals(16L*1024*1024*1024+768L*1024*1024,Settings.requiredMemory(8));
    }
}
