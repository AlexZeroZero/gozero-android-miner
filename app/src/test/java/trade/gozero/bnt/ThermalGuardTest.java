package trade.gozero.bnt;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThermalGuardTest {
    @Test public void systemRecoversAtModerateAfterThreeSeconds(){
        ThermalGuard g=new ThermalGuard();
        assertTrue(g.reason(41,3,43,0).contains("系统严重"));
        assertNotNull(g.reason(41,2,43,1000));
        assertNotNull(g.reason(41,2,43,3999));
        assertNull(g.reason(41,2,43,4000));
        assertFalse(g.isCooling());
    }
    @Test public void batteryRequiresTwoDegreeDropAndStableReadings(){
        ThermalGuard g=new ThermalGuard();
        assertTrue(g.reason(43,0,43,0).contains("电池 43.0°C"));
        assertNotNull(g.reason(41.1f,0,43,10000));
        assertNotNull(g.reason(41,0,43,11000));
        assertNotNull(g.reason(41,0,43,13999));
        assertNull(g.reason(41,0,43,14000));
    }
    @Test public void overlappingCausesClearIndependently(){
        ThermalGuard g=new ThermalGuard();
        assertTrue(g.reason(44,3,43,0).contains("；"));
        g.reason(40,3,43,1000);
        String systemOnly=g.reason(40,3,43,4000);
        assertTrue(systemOnly.contains("系统"));assertFalse(systemOnly.contains("电池"));
        g.reason(40,2,43,5000);assertNull(g.reason(40,2,43,8000));
        g.reason(44,3,43,9000);g.reason(42,1,43,10000);
        String batteryOnly=g.reason(42,1,43,13000);
        assertTrue(batteryOnly.contains("电池"));assertFalse(batteryOnly.contains("系统"));
    }
    @Test public void freshStartBelowLimitDoesNotWait(){
        for(int limit:new int[]{40,43,50}){
            ThermalGuard g=new ThermalGuard();
            assertNull(g.reason(limit-.1f,2,limit,0));
            assertNotNull(g.reason(limit,2,limit,1));
        }
    }
    @Test public void missingSamplesResetRecoveryWindow(){
        ThermalGuard g=new ThermalGuard();g.reason(44,3,43,0);
        g.reason(40,1,43,1000);
        assertNotNull(g.reason(Float.NaN,-1,43,4000));
        assertNotNull(g.reason(40,1,43,5000));
        assertNotNull(g.reason(40,1,43,7999));
        assertNull(g.reason(40,1,43,8000));
    }
    @Test public void thermalBounceRestartsTheStableWindow(){
        ThermalGuard g=new ThermalGuard();g.reason(40,3,43,0);
        g.reason(40,2,43,1000);g.reason(40,3,43,3000);
        assertNotNull(g.reason(40,2,43,4000));
        assertNotNull(g.reason(40,2,43,6999));
        assertNull(g.reason(40,2,43,7000));
        assertNotNull(g.reason(40,3,43,7001));
    }
    @Test public void batteryBounceCannotUseAnOldRecoveryWindow(){
        ThermalGuard g=new ThermalGuard();g.reason(43,0,43,0);
        g.reason(41,0,43,1000);g.reason(41.5f,0,43,3000);
        assertNotNull(g.reason(41,0,43,4000));
        assertNotNull(g.reason(41,0,43,6999));
        assertNull(g.reason(41,0,43,7000));
        assertNotNull(g.reason(43,0,43,7001));
    }
}
