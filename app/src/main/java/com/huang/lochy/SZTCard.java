package com.huang.lochy;

import com.dk.uartnfc.Card.CpuCard;
import com.dk.uartnfc.DeviceManager.DeviceManager;

/**
 * Created by Administrator on 2016/5/31.
 */
public class SZTCard extends CpuCard {
    private onReceiveBalanceListener mOnReceiveBalanceListener;
    private onReceiveTradeListener mOnReceiveTradeListener;

    public SZTCard(DeviceManager deviceManager, byte[] uid, byte[] atr) {
        super(deviceManager, uid, atr);
    }

    //获取余额回调接口
    public interface onReceiveBalanceListener{
        void onReceiveBalance(boolean isSuc, long balance);
    }

    //获取交易记录回调接口
    public interface onReceiveTradeListener{
        void onReceiveTrade(boolean isSuc, String tradeString);
    }

    //选择SZT余额/交易记录文件
    public static byte[] getSelectMainFileCmdByte() {
        return new byte[]{0x00, (byte)0xa4, 0x04, 0x00, 0x07, 0x50, 0x41, 0x59, 0x2e, 0x53, 0x5a, 0x54, 0x00};
        //return new byte[]{0x00, (byte)0xa4, 0x04, 0x00, 0x08, 0x50, 0x41, 0x59, 0x2e, 0x54, 0x49, 0x43, 0x4c, 0x00};
    }

    //获取余额APDU指令
    public static byte[] getBalanceCmdByte() {
        return new byte[]{(byte)0x80, (byte)0x5c, 0x00, 0x02, 0x04};
    }

    //获取交易记录APDU指令
    public static byte[] getTradeCmdByte(byte n) {
        return new byte[]{(byte)0x00, (byte)0xB2, n, (byte)0xC4, 0x00};
    }

    public static String getBalance(byte[] apduData) {
        if ((apduData != null) && (apduData.length == 6) && (apduData[4] == (byte)0x90)&& (apduData[5] == (byte)0x00)) {
            long balance = ((long) (apduData[1] & 0x00ff) << 16)
                    | ((long) (apduData[2] & 0x00ff) << 8)
                    | ((long) (apduData[3] & 0x00ff));
            return ( (balance/100) + "." + (balance % 100));
        }
        return null;
    }

    public static String getTrade(byte[] bytes) {
        if ( (bytes != null) && (bytes.length == 25) && (bytes[24] == 0x00) && (bytes[23] == (byte) 0x90)) {
            StringBuffer displayStrBuffer = new StringBuffer();

            long money = ((long) (bytes[5] & 0x00ff) << 24)
                    | ((long) (bytes[6] & 0x00ff) << 16)
                    | ((long) (bytes[7] & 0x00ff) << 8)
                    | ((long) (bytes[8] & 0x00ff));
            String optStr = new String();
            if ((bytes[9] == 6) || (bytes[9] == 9)) {
                optStr = "扣款";
            } else {
                optStr = "充值";
            }

            displayStrBuffer.append(String.format("%02x%02x.%02x.%02x %02x:%02x:%02x %s %d.%d 元",
                    bytes[16],
                    bytes[17],
                    bytes[18],
                    bytes[19],
                    bytes[20],
                    bytes[21],
                    bytes[22],
                    optStr,
                    money / 100,
                    money % 100));

            return displayStrBuffer.toString();
        }
        return null;
    }

    public void requestSelectTradeFile() {

    }
}