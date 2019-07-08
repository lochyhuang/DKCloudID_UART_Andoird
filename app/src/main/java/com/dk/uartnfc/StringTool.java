package com.dk.uartnfc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by lochy on 15/5/12.
 */
public class StringTool {
    public static String byteHexToSting(byte[] data) {
        StringBuilder stringBuffer = new StringBuilder();
        for (int aR_data : data) {
            //            stringBuffer.append(Integer.toHexString(aR_data & 0x00ff));
            stringBuffer.append(String.format("%02X", aR_data & 0x00ff));
        }
        return stringBuffer.toString();
    }

    /**
     * byte[]转变为16进制String字符, 每个字节2位, 不足补0
     */
    public static String getStringByBytes(byte[] bytes) {
        String result = null;
        String hex = null;
        if (bytes != null && bytes.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(bytes.length);
            for (byte byteChar : bytes) {
                hex = Integer.toHexString(byteChar & 0xFF);
                if (hex.length() == 1) {
                    hex = '0' + hex;
                }
                stringBuilder.append(hex.toUpperCase());
            }
            result = stringBuilder.toString();
        }
        return result;
    }

    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    public static final byte[] inputToByte(InputStream inStream) throws IOException {

        ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
        byte[] buff = new byte[50000];
        int rc = 0;
        while ((rc = inStream.read(buff, 0, 50000)) > 0) {
            swapStream.write(buff, 0, rc);
        }
        byte[] in2b = swapStream.toByteArray();
        return in2b;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEFabcdef".indexOf(c);
    }
}
