package trade.gozero.bnt;

enum Coin {
    BNT("Blocknet", "Seine 0.3.1", "stratum+tcp://bntpool.com:3333", "libseine.so"),
    JTM("Jetsam", "TowerWalk 0.3.1", "https://jtm.pool.gozero.trade/rpc", "libtowerminer.so");
    final String title, core, pool, binary;
    Coin(String title,String core,String pool,String binary){this.title=title;this.core=core;this.pool=pool;this.binary=binary;}
    static Coin parse(String value){return "JTM".equals(value)?JTM:BNT;}
}
