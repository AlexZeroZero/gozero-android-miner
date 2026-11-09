package trade.gozero.bnt;

import java.util.Locale;

/** Independent hysteresis for battery temperature and Android thermal status. */
final class ThermalGuard {
    static final int RESUME_DROP_C=2;
    static final long RESUME_STABLE_MS=3000;
    private boolean batteryCooling, systemCooling;
    private long batteryReadySince=-1,systemReadySince=-1;
    boolean isCooling(){return batteryCooling||systemCooling;}

    String reason(float batteryC,int thermal,int batteryLimit,long now){
        boolean validBattery=Float.isFinite(batteryC)&&batteryC>0;
        if(validBattery&&batteryC>=batteryLimit)batteryCooling=true;
        // Android: 0=none, 1=light, 2=moderate, 3=severe, through 6=shutdown.
        if(thermal>=3)systemCooling=true;
        if(batteryCooling&&validBattery&&batteryC<=batteryLimit-RESUME_DROP_C){
            if(batteryReadySince<0)batteryReadySince=now;
            if(now-batteryReadySince>=RESUME_STABLE_MS)batteryCooling=false;
        }else batteryReadySince=-1;
        if(systemCooling&&thermal>=0&&thermal<=2){
            if(systemReadySince<0)systemReadySince=now;
            if(now-systemReadySince>=RESUME_STABLE_MS)systemCooling=false;
        }else systemReadySince=-1;
        if(!batteryCooling)batteryReadySince=-1;
        if(!systemCooling)systemReadySince=-1;

        if(!isCooling())return null;
        String battery=validBattery
            ?String.format(Locale.US,"电池 %.1f°C → ≤%d°C（上限 %d°C）",batteryC,batteryLimit-RESUME_DROP_C,batteryLimit)
            :"等待电池温度读数恢复";
        String system="系统"+level(thermal)+" → 中度或以下";
        return "冷却后自动继续："+(batteryCooling?battery:"")
            +(batteryCooling&&systemCooling?"；":"")+(systemCooling?system:"")+"，稳定 3 秒后恢复";
    }

    private static String level(int value){
        String[] names={"正常","轻度","中度","严重","临界","紧急","关机级"};
        return value>=0&&value<names.length?names[value]:"状态未知";
    }
}
