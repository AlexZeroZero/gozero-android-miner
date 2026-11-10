package trade.gozero.bnt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/** Shared UI preference; no global Locale changes or mining-service restart. */
final class L10n {
    static final String[] CODES={"zh","en","ru","ja"};
    static final String[] NAMES={"中文","English","Русский","日本語"};
    private static volatile String language="zh";
    static void init(Context context){language=Translations.normalize(context.getSharedPreferences("app",0).getString("language","zh"));}
    static String text(String source){return Translations.text(source,language);}
    static int index(){for(int i=0;i<CODES.length;i++)if(CODES[i].equals(language))return i;return 0;}
    static String name(){return NAMES[index()];}
    static void set(Context context,int index){
        if(index<0||index>=CODES.length)return;
        language=CODES[index];context.getSharedPreferences("app",0).edit().putString("language",language).apply();
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        NotificationChannel channel=manager.getNotificationChannel("mining");
        if(channel!=null){channel.setName(text("挖矿状态"));manager.createNotificationChannel(channel);}
    }
    private L10n(){}
}
