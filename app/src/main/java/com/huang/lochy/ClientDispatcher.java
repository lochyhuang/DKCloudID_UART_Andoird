package com.huang.lochy;

import android.annotation.SuppressLint;
import android.content.Context;

import com.dk.uartnfc.SerialManager;
import com.dk.uartnfc.StringTool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ClientDispatcher implements Runnable {
    public static final byte SAM_V_FRAME_START_CODE = (byte)0xBB; /* 扩展通讯协议帧头定义 */
    public static final byte SAM_V_INIT_COM = (byte)0x32;   	  /* 解析服务器开始请求解析命令 */
    public static final byte SAM_V_APDU_COM = (byte)0x33;   	  /* 解析服务器APDU命令 */
    public static final byte SAM_V_FINISH_COM = (byte)0x34;   	  /* 解析服务器解析完成命令 */
    public static final byte SAM_V_ERROR_COM = (byte)0x35;   	  /* 解析服务器解析错误命令 */
    public static final byte SAM_V_GET_AES_KEY_COM = (byte)0x36;  /* 获取明文解谜密钥 */

    public static final int NUMBER_OF_REPARSING = 5;              /*解析失败时，重新解析的次数*/

    private Socket client;
    private OutputStream out;
    private InputStream in;
    private volatile byte[] bodyBuff = new byte[0];
    private volatile byte[] headBuff = new byte[0];

    public static final int PACKET_HEAD_LENGTH = 2;

    private byte[] initData;
    private SerialManager serialRXTX;
    private Context mContext;
    private WeakReference<MainActivity> UIActivity;

    private boolean closed = false;
    static int err_cnt = 0;
    int schedule = 1;

    public ClientDispatcher(Context context) {
        mContext = context;
        UIActivity = new WeakReference<MainActivity>((MainActivity) context);
    }

    public void setInitData(byte[] initData) {
        this.initData = initData;
    }
    
    public void setSerialRXTX(SerialManager serialRXTX) {
        this.serialRXTX = serialRXTX;
    }

    public void run() {
        try {
            //创建一个客户端socket
            client = new Socket("www.dkcloudid.cn", 20006);
            client.setTcpNoDelay(true);
            
            //向服务器端传递信息
            out = client.getOutputStream();
            //获取服务器端传递的数据
            in = client.getInputStream();

            logViewln("开始解析...");

            schedule = 1;

            //发送解析请求
            Handle(initData);
            
            //等待接收数据，一直循环到关闭连接
            ReadPacket();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // read tcp stream
    @SuppressLint("DefaultLocale")
    @SuppressWarnings("unused")
	private void ReadPacket() {
        while (true) {
            if (closed) {
                System.out.println("请求已被关闭");
                return;
            }
            try {
                // packet head size
                if (headBuff.length < PACKET_HEAD_LENGTH) {
                    byte[] head = new byte[PACKET_HEAD_LENGTH - headBuff.length];
                    int couter = in.read(head);
                    if (couter < 0) {
                        continue;
                    }

                    headBuff = UtilTool.mergeByte(headBuff, head, 0, couter);
                }

                // packet body length
                short bodyLen = UtilTool.byteToShort(headBuff);

                if (bodyBuff.length < bodyLen) {
                    byte[] body = new byte[bodyLen - bodyBuff.length];
                    int couter = in.read(body);
                    if (couter < 0) {
                        continue;
                    }

                    bodyBuff = UtilTool.mergeByte(bodyBuff, body, 0, couter);
                    if (couter < body.length) {
                        continue;
                    }
                }

                logViewln(null);
                logViewln(String.format("正在解析%%%d", (int)((++schedule) * 100 / 50.0)));
                
                //数据接收完成
                switch (bodyBuff[0]) {
                    case 0x03:
                        //将数据发送给NFC模块
                        byte[] bytes = new byte[bodyBuff.length + 5];
                        int cmdLen = bodyBuff.length + 1;
                        bytes[0] = SAM_V_FRAME_START_CODE;
                        bytes[1] = (byte)((cmdLen & 0xff00) >> 8);
                        bytes[2] = (byte)(cmdLen & 0x00ff);
                        bytes[3] = SAM_V_APDU_COM;
                        System.arraycopy(bodyBuff, 0, bytes, 4, bodyBuff.length);
                        bytes[bytes.length - 1] = UtilTool.bcc_check( bytes );
                        byte[] serialRes = serialRXTX.sendWithReturn(bytes, 300);

                        //数据长度校验
                        if ( (serialRes == null) || (serialRes.length < 6) ) {
                            logViewln( "数据长度错误！" );
                            Close();

                            if (err_cnt++ < NUMBER_OF_REPARSING) {
                                logViewln( "正在重新解析.." );
                                serialRXTX.send(StringTool.hexStringToBytes("AA0118"));
                            }
                            else {
                                err_cnt = 0;
                            }
                            return;
                        }

                        //和校验
                        byte bcc_sum = 0;
                        for ( int i=0; i<serialRes.length - 1; i++ ) {
                            bcc_sum ^= serialRes[i];
                        }
                        if ( bcc_sum != serialRes[serialRes.length - 1] ) {
                            System.out.println("和校验失败");
                            logViewln( "和校验失败！" );
                            Close();

                            if (err_cnt++ < NUMBER_OF_REPARSING) {
                                logViewln( "正在重新解析.." );
                                serialRXTX.send(StringTool.hexStringToBytes("AA0118"));
                            }
                            else {
                                err_cnt = 0;
                            }

                            return;
                        }

                        //协议校验
                        if ( (serialRes[0] == SAM_V_FRAME_START_CODE) && (serialRes[3] == SAM_V_APDU_COM) ) {
                            byte[] apduBytes = Arrays.copyOfRange( serialRes, 4, serialRes.length - 1 );
                            Handle(apduBytes);
                        }
                        else {
                            System.out.println("协议错误！");
                            Close();
                            if (err_cnt++ < NUMBER_OF_REPARSING) {
                                logViewln( "正在重新解析.." );
                                serialRXTX.send(StringTool.hexStringToBytes("AA0118"));
                            }
                            else {
                                err_cnt = 0;
                            }
                            return;
                        }
                        break;

                    case 0x04:
                        byte[] decrypted = new byte[bodyBuff.length - 3];
                        System.arraycopy(bodyBuff, 3, decrypted, 0, decrypted.length);
                        IDCardData idCardData = new IDCardData(decrypted, mContext);
                        if ( idCardData.IDCardNo != null ) {
                            System.out.println("解析成功：" + err_cnt + idCardData.toString());
                            logViewln(null);
                            logViewln("解析成功(" + err_cnt + "):");
                            showIDMsg(idCardData);
                            //Close();
                            err_cnt = 0;

                            //获取照片
                            initData[0] = (byte)0xA0;
                            Handle(initData);
                        }
                        else {
                            logViewln( "数据错误！" );
                            System.out.println("数据错误！");
                            Close();

                            if (err_cnt++ < NUMBER_OF_REPARSING) {
                                logViewln( "正在重新解析.." );
                                serialRXTX.send(StringTool.hexStringToBytes("AA0118"));
                            }
                            else {
                                err_cnt = 0;
                            }

                            return;
                        }
                        break;

                    case 0x05:
                        System.out.println("服务器请求超时！");
                        logViewln( "服务器请求超时！" );
                        Close();

                        if (err_cnt++ < NUMBER_OF_REPARSING) {
                            logViewln( "正在重新解析.." );
                            serialRXTX.send(StringTool.hexStringToBytes("AA0118"));
                        }
                        else {
                            err_cnt = 0;
                        }

                        return;

                    case 0x06:
                        Close();
                        System.out.println("设备未注册！");
                        logViewln("设备未注册！");
                        return;

                    default:
                        logViewln( "服务器返回命令错误！！" );
                        Close();

                        if (err_cnt++ < NUMBER_OF_REPARSING) {
                            logViewln( "正在重新解析.." );
                            serialRXTX.send(StringTool.hexStringToBytes("AA0118"));
                        }
                        else {
                            err_cnt = 0;
                        }
                        return;
                }

//                if ( bodyBuff.length > 300 ) {
//                    if (bodyBuff[0] == 0x04) {
//                        byte[] decrypted = new byte[bodyBuff.length - 3];
//                        System.arraycopy(bodyBuff, 3, decrypted, 0, decrypted.length);
//                        IDCardData idCardData = new IDCardData(decrypted, mContext);
//                        if ( (idCardData != null) && (idCardData.IDCardNo != null) ) {
//                            System.out.println("解析成功：" + idCardData.toString());
//                            //logViewln(idCardData.toString());
//                            showIDMsg(idCardData);
//                            Close();
//                        }
//                        else {
//                            logViewln( "数据错误！" );
//                            Close();
//                            return;
//                        }
//                    }
//                }
//                else {
//	                //将数据发送给NFC模块
//	                byte[] bytes = new byte[bodyBuff.length + 5];
//	                int cmdLen = bodyBuff.length + 1;
//	                bytes[0] = SAM_V_FRAME_START_CODE;
//	                bytes[1] = (byte)((cmdLen & 0xff00) >> 8);
//	                bytes[2] = (byte)(cmdLen & 0x00ff);
//	                bytes[3] = SAM_V_APDU_COM;
//	                System.arraycopy(bodyBuff, 0, bytes, 4, bodyBuff.length);
//	                bytes[bytes.length - 1] = UtilTool.bcc_check( bytes );
//	                byte[] serialRes = serialRXTX.sendWithReturn(bytes, 300);
//
//                	//数据长度校验
//                	if ( (serialRes == null) || (serialRes.length < 6) ) {
//                        logViewln( "数据长度错误！" );
//                    	Close();
//                		return;
//                	}
//
//            		//和校验
//            		byte bcc_sum = 0;
//            		for ( int i=0; i<serialRes.length - 1; i++ ) {
//            			bcc_sum ^= serialRes[i];
//            		}
//            		if ( bcc_sum != serialRes[serialRes.length - 1] ) {
//                        System.out.println("和校验失败");
//                        logViewln( "和校验失败！" );
//                        Close();
//                		return;
//            		}
//
//            		//协议校验
//            		if ( (serialRes[0] == SAM_V_FRAME_START_CODE) && (serialRes[3] == SAM_V_APDU_COM) ) {
//            			byte[] apduBytes = Arrays.copyOfRange( serialRes, 4, serialRes.length - 1 );
//            			Handle(apduBytes);
//            		}
//                }
                
                // reset headBuff && bodyBuff
                headBuff = new byte[0];
                bodyBuff = new byte[0];
            } catch (Exception e) {
                e.printStackTrace();
                Close();
                logViewln( "解析失败！" );
                return;
            }
        }
    }

    public void Handle(byte[] body) {
        //将数据发送给服务器
        SendPacket(body);
    }

    // send packet to Server
    private void SendPacket( byte[] res ) {
        byte[] headLen = UtilTool.shortToByte((short) res.length);
        byte[] body = UtilTool.mergeByte(headLen, res, 0, res.length);
        try {
            out.write(body);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // close the tcp connection
    public void Close() {
        this.closed = true;
        if (client != null) {
            try {
                client.close();

                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void logViewln(String string) {
//        final String msg = string;
//        UIActivity.get().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                MainActivity.logViewln(msg);
//            }
//        });
    }

    private void showIDMsg(IDCardData idCardData) {
//        final IDCardData msg = idCardData;
//        UIActivity.get().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                MainActivity.showIDMsg(msg);
//            }
//        });
    }
}
