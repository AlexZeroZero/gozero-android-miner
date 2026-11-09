package trade.gozero.bnt;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class CoreIntegrationTest {
    @Test public void testPackagedCoreVersion() throws Exception {
        File executable=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationInfo().nativeLibraryDir,"libseine.so");
        assertTrue("Native core missing",executable.canExecute());
        Process p=new ProcessBuilder(executable.getAbsolutePath(),"--version").redirectErrorStream(true).start();
        assertTrue("Core version timed out",p.waitFor(15,TimeUnit.SECONDS));
        String line=new BufferedReader(new InputStreamReader(p.getInputStream())).readLine();
        assertEquals(0,p.exitValue());assertEquals("seine v0.3.1",line);
    }
    @Test public void testRealStatsParserUsesWindowRate() {
        MinerState.reset();
        MinerState.line("15.0s INFO STATS elapsed=15.0s hashes=30 rate=2.000 H/s templates=1 submitted=2 stale=0 accepted=2 deferred=0 dropped=0");
        MinerState.line("20.0s INFO STATS elapsed=20.0s hashes=55 rate=2.750 H/s templates=1 submitted=3 stale=0 accepted=3 deferred=0 dropped=0");
        assertEquals(5.0,MinerState.rate,0.0001);assertEquals(2.75,MinerState.cumulative,0.0001);assertEquals(3,MinerState.accepted);
        MinerState.newProcess();
        MinerState.line("5.0s INFO STATS elapsed=5.0s hashes=10 rate=2.000 H/s templates=1 submitted=0 stale=0 accepted=0 deferred=0 dropped=0");
        assertEquals(2.0,MinerState.rate,0.0001);
    }
    @Test public void testSettingsRejectsMalformedEndpoint() {
        assertNull(Settings.validate("stratum+tcp://bntpool.com:3333",Settings.WALLET,"samsung-s24"));
        assertNull(Settings.validate("stratum+ssl://bntpool.com:443",Settings.WALLET,"s24"));
        assertNotNull(Settings.validate("https://bntpool.com/",Settings.WALLET,"s24"));
        assertNotNull(Settings.validate("stratum+tcp://bntpool.com:3333",Settings.WALLET,"--help"+" "));
        assertNotNull(Settings.validate("stratum+tcp://bntpool.com:99999",Settings.WALLET,"s24"));
    }
}
