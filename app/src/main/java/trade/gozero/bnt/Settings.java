package trade.gozero.bnt;

import android.content.Context;
import android.content.SharedPreferences;
import java.net.URI;

final class Settings {
    static final String WALLET = "UrTB5xBaVnugVvzPKzdYSKhTXPvxujEG55hapYSoyzmBdBTsbc2WVQeDApNFWrXtJRYJffE1EAWscaE7k9tYbqJuNuZyT";
    static final String JTM_WALLET="j1y8tcv7x9dwm52uh0qywqsrc8yux93ynpm79dwef8gs3ll2gxzv5sett35y";
    static final int MAX_THREADS=8;
    static final long BYTES_PER_THREAD=2L*1024*1024*1024;
    static final long START_RESERVE=768L*1024*1024;
    static int clampThreads(int value){return Math.max(1,Math.min(MAX_THREADS,value));}
    static long requiredMemory(int count){return clampThreads(count)*BYTES_PER_THREAD+START_RESERVE;}
    static String[] threadOptions(){String[] a=new String[MAX_THREADS];for(int i=0;i<a.length;i++)a[i]=(i+1)+" 线程 · "+((i+1)*2)+" GiB";return a;}
    static String[] threadOptions(Coin coin){if(coin==Coin.BNT)return threadOptions();String[] a=new String[MAX_THREADS];for(int i=0;i<a.length;i++)a[i]=(i+1)+" 线程";return a;}
    static long requiredMemory(Coin coin,int count){return coin==Coin.BNT?requiredMemory(count):START_RESERVE+32L*1024*1024+clampThreads(count)*4L*1024*1024;}
    static Coin selected(Context c){return Coin.parse(c.getSharedPreferences("app",0).getString("coin","BNT"));}
    static SharedPreferences prefs(Context c,Coin coin){return c.getSharedPreferences(coin==Coin.BNT?"miner":"miner_jtm",0);}
    final Coin coin;
    final String pool, wallet, worker;
    final int threads, temperature, jtmMode, jtmMaxPads;
    // Only enable the measured automatic profile on the tested GT5. Other
    // phones retain the standard profile until they have their own comparison.
    static int jtmPads(int mode,String model){return mode==2?2:mode==1?1:"RMX3823".equals(model)?2:1;}
    // GT5's X3 (CPU7) benefits from two independent walks; keep the original
    // profile on unmeasured identities. This only selects the mixed profile.
    static String jtmSinglePadCpus(String model,String soc){
        return "RMX3823".equals(model)&&"SM8550".equals(soc)?"0-2,5-6":"0-2,5-7";
    }
    final boolean chargingOnly, mini;
    Settings(Context c) {
        this(c,selected(c));
    }
    Settings(Context c,Coin coin) {
        this.coin=coin;
        SharedPreferences p = prefs(c,coin);
        pool=p.getString("pool",coin.pool);
        wallet=p.getString("wallet",coin==Coin.BNT?WALLET:JTM_WALLET); worker=p.getString("worker",coin==Coin.BNT?"samsung-s24":"realme-gt5");
        threads=clampThreads(p.getInt("threads",coin==Coin.BNT?1:2));
        temperature=Math.max(40,Math.min(50,p.getInt("temperature",43)));
        jtmMode=Math.max(0,Math.min(3,p.getInt("jtmMode",0)));
        int pads=p.getInt("jtmMaxPads",4);jtmMaxPads=pads>=4?4:pads>=2?2:1;
        chargingOnly=p.getBoolean("chargingOnly",true);
        mini=p.getBoolean("mini",true);
    }
    static String validate(String pool,String wallet,String worker) {
        return validate(Coin.BNT,pool,wallet,worker);
    }
    static String validate(Coin coin,String pool,String wallet,String worker) {
        try {
            URI u = new URI(pool);
            if(coin==Coin.JTM){
                if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||u.getPort()>65535||u.getPort()==0)return "JTM 矿池必须使用 HTTPS 地址，例如 https://jtm.pool.gozero.trade/rpc";
            }else{
            if (!("stratum+tcp".equals(u.getScheme()) || "stratum+ssl".equals(u.getScheme())) || u.getHost()==null || u.getPort()<1 || u.getPort()>65535 || u.getUserInfo()!=null || (u.getPath()!=null&&!u.getPath().isEmpty()) || u.getQuery()!=null || u.getFragment()!=null)
                return "矿池格式：stratum+tcp://主机:端口，或 stratum+ssl://主机:端口";
            }
        } catch(Exception e) { return "矿池地址格式无效"; }
        if(coin==Coin.JTM&&!validJtmAddress(wallet))return "JTM 地址校验失败，请核对完整的 j1 开头公开收款地址";
        if (coin==Coin.BNT&&!wallet.matches("[1-9A-HJ-NP-Za-km-z]{80,120}")) return "请输入完整的 BNT 收款地址（不是私钥或助记词）";
        if (!worker.matches("[A-Za-z0-9_-]{1,32}")) return "矿工名只支持 1–32 位字母、数字、下划线和短横线";
        return null;
    }
    static boolean validJtmAddress(String value){
        if(value==null||value.length()!=60||!value.equals(value.toLowerCase(java.util.Locale.ROOT))||!value.startsWith("j1"))return false;
        String alphabet="qpzry9x8gf2tvdw0s3jn54khce6mua7l";
        int[] data=new int[61];data[0]='j'>>5;data[1]=0;data[2]='j'&31;
        for(int i=2;i<value.length();i++){int x=alphabet.indexOf(value.charAt(i));if(x<0)return false;data[i+1]=x;}
        int chk=1;int[] gen={0x3b6a57b2,0x26508e6d,0x1ea119fa,0x3d4233dd,0x2a1462b3};
        for(int v:data){int top=chk>>>25;chk=((chk&0x1ffffff)<<5)^v;for(int i=0;i<5;i++)if(((top>>>i)&1)!=0)chk^=gen[i];}
        return chk==0x2bc830a3&&(data[54]&15)==0;
    }
}
