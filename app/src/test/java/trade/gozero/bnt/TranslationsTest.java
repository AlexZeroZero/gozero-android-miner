package trade.gozero.bnt;

import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

public class TranslationsTest {
    private static final Pattern HAN=Pattern.compile("[\\p{IsHan}]");
    private static String numbers(String text){
        java.util.regex.Matcher m=Pattern.compile("[0-9]+(?:\\.[0-9]+)?").matcher(text);StringBuilder out=new StringBuilder();
        while(m.find())out.append(m.group()).append(";");return out.toString();
    }
    @Test public void allCatalogEntriesHaveFourLanguages(){
        assertTrue(Translations.ROWS.length>=225);
        for(String[] row:Translations.ROWS){
            assertEquals(4,row.length);
            for(int i=0;i<4;i++)assertFalse(row[i].isEmpty());
            assertFalse(row[0],HAN.matcher(row[1]).find());
            assertFalse(row[0],HAN.matcher(row[2]).find());
            assertEquals(row[0],Translations.text(row[0],"zh"));
            assertEquals(row[1],Translations.text(row[0],"en"));
            assertEquals(row[2],Translations.text(row[0],"ru"));
            assertEquals(row[3],Translations.text(row[0],"ja"));
        }
    }
    @Test public void publicAddressesAndRawNativeLogsAreUnchanged(){
        for(String lang:new String[]{"zh","en","ru","ja"}){
            for(String value:new String[]{Settings.JTM_WALLET,FeePolicy.JTM_ADDRESS,FeePolicy.BNT_ADDRESS,
                "stratum+tcp://bntpool.com:3333","RPC error: {\"code\":20,\"message\":\"node preparing work; retry\"}",
                "STATS elapsed=5.1s hashes=8 rate=1.583 H/s","$1\\worker"})
                assertEquals(value,Translations.text(value,lang));
        }
    }
    @Test public void composedDashboardAndLogsTranslateWithoutChangingNumbers(){
        String[] messages={"5 / 7 线程 · 核心 12.2 MiB","会话均值 2457.40",
            "所选约 14 GiB · 当前可用 16.4 GiB","7 线程 · 工作区按核心分配 · 当前可用 16.4 GiB",
            "JTM 2670.0 H/s · 接受 8 / 拒绝 0 · 挖矿中","JTM 手续费 1% · 1800.0 H/s · 手续费接受 2",
            "已停止 · 240 行 / 最多 240 行 · 接受 3534 / 拒绝 31",
            "自动实测 · 3 线程 · 512 KiB/线程 · 有效计算 1801.0 H/s","自动实测 · 3 线程 · 有效计算 1801.0 H/s"};
        for(String message:messages)for(String lang:new String[]{"en","ru"}){
            String result=Translations.text(message,lang);
            assertFalse(result,HAN.matcher(result).find());
            assertEquals(numbers(message),numbers(result));
        }
    }
    @Test public void coolingAndValidationMessagesTranslate(){
        ThermalGuard guard=new ThermalGuard();String reason=guard.reason(44f,3,43,0);
        for(String lang:new String[]{"en","ru"}){
            String result=Translations.text(reason,lang);assertFalse(result,HAN.matcher(result).find());
            assertTrue(result.contains("44.0"));assertTrue(result.contains("41"));
            String error=Settings.validate(Coin.JTM,"http://invalid",Settings.JTM_WALLET,"test");
            assertFalse(HAN.matcher(Translations.text(error,lang)).find());
        }
    }
    @Test public void japaneseIsNotRecursivelyReplaced(){
        assertEquals("CPU 温度",Translations.text("CPU 温度","ja"));
        assertEquals("JTM の設定",Translations.text("JTM 配置","ja"));
        assertEquals("自動 · スレッドとメモリを測定",Translations.text("自适应 · 实测线程与内存","ja"));
    }
    @Test public void formattingDoesNotDependOnPhoneLocale(){
        Locale before=Locale.getDefault();
        try{Locale.setDefault(new Locale("ru"));
            String result=Translations.text(String.format(Locale.US,"会话均值 %.2f",12.34),"en");
            assertEquals("Session average 12.34",result);
        }finally{Locale.setDefault(before);}
    }
    @Test public void unsupportedPreferenceFallsBackToChinese(){
        assertEquals("zh",Translations.normalize("fr"));assertEquals("zh",Translations.normalize(null));
        for(String lang:new String[]{"en","ru","ja"})assertEquals(lang,Translations.normalize(lang));
        assertEquals("已停止",Translations.text("已停止","fr"));
    }
}
