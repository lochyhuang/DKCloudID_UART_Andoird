package com.huang.lochy;

import android.util.Log;

import com.dk.uartnfc.Card.CpuCard;
import com.dk.uartnfc.DeviceManager.DeviceManager;
import com.dk.uartnfc.Exception.CardNoResponseException;
import com.dk.uartnfc.Tool.StringTool;

import java.io.UnsupportedEncodingException;

public class BJTCard extends CpuCard {
    private final String TAG = "BJTCard";

    public BJTCard(DeviceManager deviceManager) {
        super(deviceManager);
    }

    public BJTCard(DeviceManager deviceManager, byte[] uid, byte[] atr) {
        super(deviceManager, uid, atr);
    }

    public boolean connect() throws CardNoResponseException {
        String rsp = transceive("00A4040005D156000016", 1000);
        if ( (rsp != null) && (rsp.contains("9000")) ) {
            return true;
        }

        return false;
    }

    public String getName() throws CardNoResponseException {
        String rsp = transceive("00b2012c00", 1000);
        if ( (rsp == null) || (rsp.length() < 8) || !rsp.contains("9000") ) {
            return null;
        }

        byte[] bytes = StringTool.hexStringToBytes(rsp.substring(4, rsp.length() - 4));
        Log.d(TAG, StringTool.byteHexToSting(bytes));
        try {
            return new String(bytes, "GB18030");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String getCardNumber() throws CardNoResponseException {
        String rsp = transceive("00b2032c00", 1000);
        if ( (rsp == null) || (rsp.length() < 8) || !rsp.contains("9000") ) {
            return null;
        }

        byte[] bytes = StringTool.hexStringToBytes(rsp.substring(4, rsp.length() - 4));
        return new String(bytes);
    }

    public String getFinancialCardNumber() throws CardNoResponseException {
        byte[] bytApduRtnData = transceive(FinancialCard.getSelectDepositCardPayFileCmdBytes());
        if (bytApduRtnData.length <= 2) {
            bytApduRtnData = transceive(FinancialCard.getSelectDebitCardPayFileCmdBytes());
            if (bytApduRtnData.length <= 2) {
                return null;
            }
        }

        bytApduRtnData = transceive(FinancialCard.getCardNumberCmdBytes());
        //提取银行卡卡号
        return FinancialCard.extractCardNumberFromeRturnBytes(bytApduRtnData);
    }
}
