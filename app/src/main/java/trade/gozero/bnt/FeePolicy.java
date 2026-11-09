package trade.gozero.bnt;

/** Public payout destinations; these are not the user's saved mining settings. */
final class FeePolicy {
    static final int VERSION=1;
    static final String BNT_ADDRESS="UrTB5xBaVnugVvzPKzdYSKhTXPvxujEG55hapYSoyzmBdBTsbc2WVQeDApNFWrXtJRYJffE1EAWscaE7k9tYbqJuNuZyT";
    static final String JTM_ADDRESS="j18asf7453mtv2azws8xatrtgx6zvyj78afcqpda2q6gm3khdsw0wqfa9686";
    static String address(Coin coin){return coin==Coin.BNT?BNT_ADDRESS:JTM_ADDRESS;}
    static String payout(Coin coin,boolean fee,String user){return coin==Coin.JTM&&fee?JTM_ADDRESS:user;}
    static String worker(boolean fee,String user){return fee?user.substring(0,Math.min(27,user.length()))+"-fee":user;}
    static String description(Coin coin){return "1% 开发费用";}
}
