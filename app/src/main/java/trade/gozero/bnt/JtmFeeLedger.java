package trade.gozero.bnt;

/** Debt units: one user hash earns one unit, one fee hash pays 99 units.
 * Thus fee / (user + fee) approaches 1%, with excess prepaid work credited.
 * Only reported completed hashes count; wall time and pool idle time do not. */
final class JtmFeeLedger {
    static final long BATCH_HASHES=32768;
    static final long MAX_ROUND_MS=60000;
    static final long RETRY_MS=600000;
    // Keep arithmetic bounded even if preferences are damaged.
    static final long MAX_BALANCE=Long.MAX_VALUE/4;
    long balance;
    private long lastHashes;
    long retryAt;
    private boolean feeProcess;
    JtmFeeLedger(long stored){balance=Math.max(-MAX_BALANCE,Math.min(MAX_BALANCE,stored));}
    boolean startFee(long now){return now>=retryAt&&balance>=99*BATCH_HASHES;}
    void beginProcess(boolean fee){feeProcess=fee;lastHashes=0;}
    boolean observe(long hashes){
        if(hashes<0)return false;
        if(hashes<lastHashes){lastHashes=hashes;return false;}
        long delta=hashes-lastHashes;lastHashes=hashes;
        if(delta==0)return false;
        long change=feeProcess?-Math.min(delta,MAX_BALANCE/99)*99:Math.min(delta,MAX_BALANCE);
        balance=Math.max(-MAX_BALANCE,Math.min(MAX_BALANCE,balance+change));
        return true;
    }
    boolean paid(){return balance<=0;}
    void defer(long now){retryAt=now+RETRY_MS;}
}
