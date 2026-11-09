package trade.gozero.bnt;

import org.junit.Test;
import static org.junit.Assert.*;

public class FeePolicyTest {
    @Test public void feeSharesStaySeparateAndUserTotalsSurviveSwitches(){
        MinerState.coin=Coin.JTM;MinerState.newProcess();
        try{
            MinerState.line("{\"type\":\"status\",\"hashes\":9900,\"uptime_s\":10,\"accepted\":9,\"refused\":1,\"found\":10}");
            MinerState.resumeProcess(true);
            MinerState.line("{\"type\":\"status\",\"hashes\":100,\"uptime_s\":1,\"accepted\":2,\"refused\":3,\"found\":5}");
            assertEquals(9,MinerState.accepted);assertEquals(1,MinerState.rejected);assertEquals(10,MinerState.submitted);assertEquals(2,MinerState.feeAccepted);
            MinerState.resumeProcess(false);
            MinerState.line("{\"type\":\"status\",\"hashes\":2000,\"uptime_s\":2,\"accepted\":4,\"refused\":0,\"found\":4}");
            assertEquals(13,MinerState.accepted);assertEquals(1,MinerState.rejected);assertEquals(14,MinerState.submitted);assertEquals(2,MinerState.feeAccepted);
            assertEquals(12000.0/13,MinerState.cumulative,.001);assertFalse(MinerState.feeActive);
        }finally{MinerState.coin=Coin.BNT;MinerState.newProcess();}
    }
    @Test public void destinationsValidateAndUserPayoutIsNeverOverwritten(){
        assertTrue(Settings.validJtmAddress(FeePolicy.JTM_ADDRESS));
        assertNull(Settings.validate(Coin.BNT,Coin.BNT.pool,FeePolicy.BNT_ADDRESS,"fee"));
        assertEquals(Settings.JTM_WALLET,FeePolicy.payout(Coin.JTM,false,Settings.JTM_WALLET));
        assertEquals(FeePolicy.JTM_ADDRESS,FeePolicy.payout(Coin.JTM,true,Settings.JTM_WALLET));
        assertEquals("user-bnt",FeePolicy.payout(Coin.BNT,true,"user-bnt"));
        assertEquals(31,FeePolicy.worker(true,"abcdefghijklmnopqrstuvwxyz123456").length());
    }
    @Test public void exactRatioIsOneOutOfOneHundredTotalHashes(){
        JtmFeeLedger l=new JtmFeeLedger(0);long batch=JtmFeeLedger.BATCH_HASHES;
        for(int i=0;i<100;i++){
            l.beginProcess(false);l.observe(99*batch);assertTrue(l.startFee(0));
            l.beginProcess(true);l.observe(batch);assertTrue(l.paid());assertEquals(0,l.balance);
        }
    }
    @Test public void noWorkAndDuplicateReportsNeverAccrueFees(){
        JtmFeeLedger l=new JtmFeeLedger(0);l.beginProcess(false);
        for(int i=0;i<1000;i++)l.observe(0);
        assertEquals(0,l.balance);assertFalse(l.startFee(1000000000));
        l.observe(100);l.observe(100);assertEquals(100,l.balance);
    }
    @Test public void restartPreservesDebtAndExcessFeeWorkIsCredited(){
        long b=JtmFeeLedger.BATCH_HASHES;
        JtmFeeLedger l=new JtmFeeLedger(99*b);l.beginProcess(true);l.observe(b+100);
        assertEquals(-9900,l.balance);
        l=new JtmFeeLedger(l.balance);l.beginProcess(false);l.observe(9900);
        assertEquals(0,l.balance);assertFalse(l.startFee(0));
        l.beginProcess(false);l.observe(100);assertEquals(100,l.balance);
    }
    @Test public void failedFeeRoundBacksOffAndDoesNotLoseDebt(){
        JtmFeeLedger l=new JtmFeeLedger(99*JtmFeeLedger.BATCH_HASHES);
        l.defer(1000);assertFalse(l.startFee(600999));assertTrue(l.startFee(601000));
        assertEquals(99*JtmFeeLedger.BATCH_HASHES,l.balance);
    }
    @Test public void resetAndInvalidCountersCannotCreateHugeFees(){
        JtmFeeLedger l=new JtmFeeLedger(0);l.beginProcess(false);l.observe(100);
        l.observe(0);l.observe(-1);assertEquals(100,l.balance);
        l.observe(10);assertEquals(110,l.balance);
        l.observe(Long.MAX_VALUE);assertTrue(l.balance>0&&l.balance<=JtmFeeLedger.MAX_BALANCE);
    }
}
