package trade.gozero.bnt;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class CpuTemperatureTest {
    @Test public void currentCpuOnlyNeverBatteryGpuOrCachedCpu(){
        String raw="Cached temperatures:\nTemperature{mValue=110.0, mType=0, mName=CPU0, mStatus=0}\n"+
            "Current temperatures from HAL:\nTemperature{mValue=71.2, mType=0, mName=CPU0, mStatus=0}\n"+
            "Temperature{mValue=82.4, mType=0, mName=CPU7, mStatus=0}\n"+
            "Temperature{mValue=120.0, mType=1, mName=GPU0, mStatus=0}\n"+
            "Temperature{mValue=39.5, mType=2, mName=battery, mStatus=0}\n";
        CpuTemperature.Sample s=new CpuTemperature.Sample(CpuTemperature.parseCurrent(raw),1000,"");
        assertEquals(2,s.cores.size());assertEquals(82.4,s.maximum(),.01);assertEquals("82.4°",s.display(1500));
    }
    @Test public void missingCurrentSectionAndInvalidValuesAreUnavailable(){
        assertTrue(CpuTemperature.parseCurrent("Cached temperatures:\nTemperature{mValue=95, mType=0, mName=CPU0, mStatus=0}").isEmpty());
        assertTrue(CpuTemperature.parseCurrent("Current temperatures from HAL:\nTemperature{mValue=NaN, mType=0, mName=CPU0, mStatus=0}\nTemperature{mValue=99999, mType=0, mName=CPU1, mStatus=0}").isEmpty());
        assertEquals("不可用",new CpuTemperature.Sample(Collections.emptyMap(),1000,"不可用").display(1001));
    }
    @Test public void oldOrFutureSamplesAreNeverShownAsLive(){
        CpuTemperature.Sample s=new CpuTemperature.Sample(Collections.singletonMap("CPU0",75f),1000,"");
        assertTrue(s.fresh(16000));assertEquals("不可用",s.display(16001));assertFalse(s.fresh(999));
    }
}
