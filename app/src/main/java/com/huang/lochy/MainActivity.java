package com.huang.lochy;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dk.uartnfc.Card.CpuCard;
import com.dk.uartnfc.Card.DESFire;
import com.dk.uartnfc.Card.DeviceManagerCallback;
import com.dk.uartnfc.Card.FeliCa;
import com.dk.uartnfc.Card.Iso14443BIdCard;
import com.dk.uartnfc.Card.Iso14443bCard;
import com.dk.uartnfc.Card.Iso15693Card;
import com.dk.uartnfc.Card.Mifare;
import com.dk.uartnfc.Card.Ntag21x;
import com.dk.uartnfc.DKCloudID.DKCloudID;
import com.dk.uartnfc.DKCloudID.IDCard;
import com.dk.uartnfc.DKCloudID.IDCardData;
import com.dk.uartnfc.DeviceManager.DeviceManager;
import com.dk.uartnfc.DeviceManager.UartNfcDevice;
import com.dk.uartnfc.Exception.CardNoResponseException;
import com.dk.uartnfc.Exception.DKCloudIDException;
import com.dk.uartnfc.Exception.DeviceNoResponseException;
import com.dk.uartnfc.OTA.DialogUtils;
import com.dk.uartnfc.OTA.YModem;
import com.dk.uartnfc.Tool.StringTool;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "DKCloudID";

    final String[] botes = new String[]{"9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000"};

    private TextView msgTextView;
    private Spinner spSerial;
    private EditText edInput;
    private Button btOpen;
    private MyTTS myTTS;
    static long time_start = 0;
    static long time_end = 0;

    private UartNfcDevice uartNfcDevice;
    String selectSerialName;
    String selectBaudRate;

    private ProgressDialog readWriteDialog = null;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //语音初始化
        myTTS = new MyTTS(MainActivity.this);

        uartNfcDevice = new UartNfcDevice();
        uartNfcDevice.setCallBack(deviceManagerCallback);

        iniview();
        edInput.setText("aa020401");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (uartNfcDevice.serialManager != null) {
            uartNfcDevice.serialManager.close();
        }

        DKCloudID.Close();
    }

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        //非接寻卡回调
        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            System.out.println("Activity接收到激活卡片回调：UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS));

            final int cardTypeTemp = cardType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    readWriteCardDemo(cardTypeTemp);
                }
            }).start();
        }

        //身份证开始请求云解析回调
        @Override
        public void onReceiveSamVIdStart(byte[] initData) {
            super.onReceiveSamVIdStart(initData);

            Log.d(TAG, "开始解析");
            logViewln(null);
            logViewln("正在读卡，请勿移动身份证!");
            myTTS.speak("正在读卡，请勿移动身份证");

            time_start = System.currentTimeMillis();
        }

        //身份证云解析进度回调
        @Override
        public void onReceiveSamVIdSchedule(int rate) {
            super.onReceiveSamVIdSchedule(rate);
            showReadWriteDialog("正在读取身份证信息,请不要移动身份证", rate);
            if (rate == 100) {
                time_end = System.currentTimeMillis();

                /**
                 * 这里已经完成读卡，可以拿开身份证了，在此提示用户读取成功或者打开蜂鸣器提示可以拿开身份证了
                 */
                myTTS.speak("读取成功");
            }
        }

        //身份证云解析异常回调
        @Override
        public void onReceiveSamVIdException(String msg) {
            super.onReceiveSamVIdException(msg);

            //显示错误信息
            logViewln(msg);

            //读卡结束关闭进度条显示
            hidDialog();
        }

        //身份证云解析明文结果回调
        @Override
        public void onReceiveIDCardData(IDCardData idCardData) {
            super.onReceiveIDCardData(idCardData);

            //显示身份证数据
            showIDMsg(idCardData);
        }

        //卡片离开回调
        @Override
        public void onReceiveCardLeave() {
            super.onReceiveCardLeave();
            Log.d(TAG, "卡片已离开");
            logViewln("卡片已离开");
        }
    };

    //普通IC读写卡API调用示例
    private synchronized boolean readWriteCardDemo(int cardType) {
        switch (cardType) {
            case DeviceManager.CARD_TYPE_ISO4443_A:   //寻到A CPU卡
                final CpuCard cpuCard = (CpuCard) uartNfcDevice.getCard();
                if (cpuCard != null) {
                    logViewln(null);

                    try {
                        byte[] atr = cpuCard.getAtr();
                        logViewln("寻到CPU卡->UID:" + cpuCard.uidToString() + " ATR:" + StringTool.byteHexToSting(atr));

                        //选择深圳通主文件
                        byte[] bytApduRtnData = cpuCard.transceive(SZTCard.getSelectMainFileCmdByte());
                        if (bytApduRtnData.length <= 2) {
                            System.out.println("不是深圳通卡，当成银行卡处理！");
                            //选择储蓄卡交易文件
                            String cpuCardType;
                            bytApduRtnData = cpuCard.transceive(FinancialCard.getSelectDepositCardPayFileCmdBytes());
                            if (bytApduRtnData.length <= 2) {
                                System.out.println("不是储蓄卡，当成借记卡处理！");
                                //选择借记卡交易文件
                                bytApduRtnData = cpuCard.transceive(FinancialCard.getSelectDebitCardPayFileCmdBytes());
                                if (bytApduRtnData.length <= 2) {
                                    logViewln("未知CPU卡！");
                                    return false;
                                }
                                else {
                                    cpuCardType = "储蓄卡";
                                }
                            }
                            else {
                                cpuCardType = "借记卡";
                            }

                            bytApduRtnData = cpuCard.transceive(FinancialCard.getCardNumberCmdBytes());
                            //提取银行卡卡号
                            String cardNumberString = FinancialCard.extractCardNumberFromeRturnBytes(bytApduRtnData);
                            if (cardNumberString == null) {
                                logViewln("未知CPU卡！");
                                return false;
                            }
                            logViewln("储蓄卡卡号：" + cardNumberString);

                            //读交易记录
                            System.out.println("发送APDU指令-读10条交易记录");
                            for (int i = 1; i <= 10; i++) {
                                bytApduRtnData = cpuCard.transceive(FinancialCard.getTradingRecordCmdBytes((byte) i));
                                logViewln(FinancialCard.extractTradingRecordFromeRturnBytes(bytApduRtnData));
                            }
                        }
                        else {  //深圳通处理流程
                            bytApduRtnData = cpuCard.transceive(SZTCard.getBalanceCmdByte());
                            if (SZTCard.getBalance(bytApduRtnData) == null) {
                                logViewln("未知CPU卡！");
                                System.out.println("未知CPU卡！");
                                return false;
                            }
                            else {
                                logViewln("深圳通余额：" + SZTCard.getBalance(bytApduRtnData));
                                System.out.println("余额：" + SZTCard.getBalance(bytApduRtnData));
                                //读交易记录
                                System.out.println("发送APDU指令-读10条交易记录");
                                for (int i = 1; i <= 10; i++) {
                                    bytApduRtnData = cpuCard.transceive(SZTCard.getTradeCmdByte((byte) i));
                                    logViewln("\r\n" + SZTCard.getTrade(bytApduRtnData));
                                }
                            }
                        }
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_FELICA:  //寻到FeliCa
                FeliCa feliCa = (FeliCa) uartNfcDevice.getCard();
                if (feliCa != null) {
                    logViewln("寻到FeliCa->UID:" + feliCa.uidToString());
                }
                break;
            case DeviceManager.CARD_TYPE_ULTRALIGHT: //寻到Ultralight卡
                final Ntag21x ntag21x = (Ntag21x) uartNfcDevice.getCard();
                if (ntag21x != null) {
                    try {
                        logViewln("寻到Ultralight卡->UID:" + ntag21x.uidToString());

                        //任意长度读写Demo,不带进度回调方式
                        byte[] writeBytes = new byte[100];
                        Arrays.fill(writeBytes, (byte) 0xAA);
                        logViewln("开始写100个字节数据：0xAA");
                        boolean isSuc = ntag21x.longWrite((byte) 4, writeBytes);
                        if (isSuc) {
                            logViewln("写数据成功！");
                            logViewln("开始读10个字节数据");
                            byte[] readTempBytes = ntag21x.longRead((byte) 4, (byte) (100 / 4));
                            logViewln("读取成功：\r\n" + StringTool.byteHexToSting(readTempBytes));
                        }
                        else {
                            logViewln("写数据失败！");
                        }

                        //NDEF Demo

                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_MIFARE:   //寻到Mifare卡
                final Mifare mifare = (Mifare) uartNfcDevice.getCard();
                if (mifare != null) {
                    logViewln(null);
                    logViewln("寻到Mifare卡->UID:" + mifare.uidToString());
                    Log.d(TAG, "寻到Mifare卡->UID:" + mifare.uidToString());

                    try {
                        //配置密钥到NFC模块，此密钥在读取时会用到
                        boolean status = mifare.setKey(Mifare.MIFARE_KEY_TYPE_A, Mifare.MIFARE_DEFAULT_KEY);
                        if (status) {
                            logViewln("配置默认密钥A到模块成功");
                        }
                        else {
                            logViewln("配置默认密钥A到模块失败");
                            break;
                        }
                        status = mifare.write(1, new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16});
                        if (status) {
                            logViewln("写数据01020304050607080910111213141516到块1成功");
                        }
                        else {
                            logViewln("写数据01020304050607080910111213141516到块1失败");
                        }

                        byte[] rspBytes = mifare.read(1);
                        logViewln("读取到块1数据：" + StringTool.byteHexToSting(rspBytes));
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_ISO15693: //寻到15693卡
                final Iso15693Card iso15693Card = (Iso15693Card) uartNfcDevice.getCard();
                if (iso15693Card != null) {
                    logViewln(null);
                    logViewln("寻到15693卡->UID:" + iso15693Card.uidToString());
                    logViewln("读块0数据：");
                    try {
                        boolean status = iso15693Card.write(1, new byte[] {0x01, 0x01, 0x03, 0x04});
                        if (status) {
                            logViewln("写数据01020304到块1成功");
                        }
                        else {
                            logViewln("写数据01020304到块1失败");
                        }

                        byte[] rsp = iso15693Card.read(1);
                        logViewln("块1数据：" + StringTool.byteHexToSting(rsp));

                        rsp = iso15693Card.ReadMultiple(0, 10);
                        logViewln("块0-块10数据：" + StringTool.byteHexToSting(rsp));
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case DeviceManager.CARD_TYPE_DESFire:
                final DESFire desFire = (DESFire) uartNfcDevice.getCard();
                if (desFire != null) {
                    logViewln("寻到DESFire卡->UID:" + desFire.uidToString());
                }
                break;
            case DeviceManager.CARD_TYPE_ISO4443_B:
                final Iso14443bCard iso14443bCard = (Iso14443bCard) uartNfcDevice.getCard();
                if (iso14443bCard != null) {
                    logViewln("寻到iso14443b卡->UID:" + iso14443bCard.uidToString());
                }
                break;
        }

        return false;
    }

    private void iniview() {
        msgTextView = (TextView) findViewById(R.id.msgText);
        spSerial = (Spinner) findViewById(R.id.sp_serial);
        edInput = (EditText) findViewById(R.id.ed_input);
        btOpen = (Button) findViewById(R.id.bt_open);
        Button btSend = (Button) findViewById(R.id.bt_send);
        Spinner spBote = (Spinner) findViewById(R.id.sp_bote);
        Button btOTA = (Button) findViewById(R.id.bt_ota);

        readWriteDialog = new ProgressDialog(MainActivity.this);
        readWriteDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        // 设置ProgressDialog 标题
        readWriteDialog.setTitle("请稍等");
        // 设置ProgressDialog 提示信息
        readWriteDialog.setMessage("正在读写数据……");
        readWriteDialog.setMax(100);

        final List<String> ports = uartNfcDevice.serialManager.getAvailablePorts();  //获取可用的串口
        Log.d(TAG, "可用的串口列表为：" + ports.toString());

        //配置选择串口号的选择器
        SpAdapter spAdapter = new SpAdapter(this);
        spAdapter.setDatas( ports.toArray(new String[ports.size()]) );
        spSerial.setAdapter(spAdapter);
        spSerial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectSerialName = ports.get(position);
                if ( uartNfcDevice.serialManager.isOpen() ) {
                    uartNfcDevice.serialManager.close();
                    uartNfcDevice.serialManager.open(selectSerialName, selectBaudRate);
                    updataSendBt();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        //配置选择波特率的选择器
        SpAdapter spAdapter2 = new SpAdapter(this);
        spAdapter2.setDatas(botes);
        spBote.setAdapter(spAdapter2);
        spBote.setSelection(4);
        spBote.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectBaudRate = botes[position];
                if ( uartNfcDevice.serialManager.isOpen() ) {
                    uartNfcDevice.serialManager.close();
                    uartNfcDevice.serialManager.open(selectSerialName, selectBaudRate);
                    updataSendBt();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        //打开串口按键监听
        btOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( uartNfcDevice.serialManager.isOpen() ) {
                    uartNfcDevice.serialManager.close();
                }
                else {
                    uartNfcDevice.serialManager.open(selectSerialName, selectBaudRate);
                    //serialManager.open("/dev/ttyS3", "115200");
                }

                updataSendBt();
            }
        });

        //固件升级按键监听
        btOTA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!uartNfcDevice.serialManager.isOpen()) {
                    Toast.makeText(getBaseContext(), "串口未打开！", Toast.LENGTH_SHORT).show();
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //让模块进入升级模式
                        uartNfcDevice.serialManager.send(StringTool.hexStringToBytes("AA0124"));
                    }
                }).start();

                DialogUtils.select_file(MainActivity.this, new DialogUtils.DialogSelection() {
                    @Override
                    public void onSelectedFilePaths(String[] files) {
                        if (files.length == 1) {

                            final String fileName = files[0];
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        new YModem(uartNfcDevice.serialManager).send(new File(fileName), new YModem.onReceiveScheduleListener() {
                                            @Override
                                            public void onReceiveSchedule(int rate) {  //读取进度回调
                                                showReadWriteDialog("正在升级", rate);
                                            }
                                        });

                                        logViewln( "升级成功" );
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    finally {
                                        //读卡结束关闭进度条显示
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (readWriteDialog.isShowing()) {
                                                    readWriteDialog.dismiss();
                                                }
                                                readWriteDialog.setProgress(0);
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }
                    }
                });
            }
        });

        //发送数据监听
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (edInput.getText().toString().length() > 0) {
                    if (uartNfcDevice.serialManager.isOpen()) {
                        uartNfcDevice.serialManager.send( StringTool.hexStringToBytes( edInput.getText().toString()) );
                        msgTextView.setText("");
                        refreshLogView("发送：" +  StringTool.byteHexToSting(StringTool.hexStringToBytes( edInput.getText().toString())) + "\r\n");
                    } else {
                        Toast.makeText(getBaseContext(), "串口未打开！", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(getBaseContext(), "未输入指令！", Toast.LENGTH_SHORT).show();
                }
            }
        });

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                uartNfcDevice.serialManager.open("/dev/ttyUSB0", "115200");
//            }
//        }).start();
    }

    //更新按键状态
    private void updataSendBt() {
        if ( uartNfcDevice.serialManager.isOpen() ) {
            btOpen.setText("关闭串口");
            Toast.makeText(getBaseContext(), "串口已打开！", Toast.LENGTH_SHORT).show();
        }
        else {
            btOpen.setText("打开串口");
            Toast.makeText(getBaseContext(), "串口已关闭！", Toast.LENGTH_SHORT).show();
        }
    }

    //更新显示框状态
    synchronized void refreshLogView(String msg){
        final String theMsg = msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgTextView.append(theMsg);
            }
        });
    }

    private void logViewln(String string) {
        final String msg = string;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (msg == null) {
                    msgTextView.setText("");
                    return;
                }

                if (msgTextView.length() > 1000) {
                    msgTextView.setText("");
                }
                msgTextView.append(msg + "\r\n");
                int offset = msgTextView.getLineCount() * msgTextView.getLineHeight();
                if(offset > msgTextView.getHeight()){
                    msgTextView.scrollTo(0,offset-msgTextView.getHeight());
                }
            }
        });
    }

    private void showIDMsg(IDCardData msg) {
        final IDCardData idCardData = msg ;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgTextView.setText("解析成功，读卡用时:" + (time_end - time_start) + "ms\r\n" + idCardData.toString() + "\r\n");

                SpannableString ss = new SpannableString(msgTextView.getText().toString()+"[smile]");
                //得到要显示图片的资源
                Drawable d = new BitmapDrawable(idCardData.PhotoBmp);//Drawable.createFromPath("mnt/sdcard/photo.bmp");
                if (d != null) {
                    //设置高度
                    d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                    //跨度底部应与周围文本的基线对齐
                    ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BASELINE);
                    //附加图片
                    ss.setSpan(span, msgTextView.getText().length(),msgTextView.getText().length()+"[smile]".length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    msgTextView.setText(ss);
                    //msgTextView.setText("\r\n");
                    //Log.d(TAG, idCardData.PhotoBmp);
                }
            }
        });
    }

    private void showMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                msgTextView.setText(msg);
            }
        });
    }

    //进度条显示
    private void showReadWriteDialog(String msg, int rate) {
        final int theRate = rate;
        final String theMsg = msg;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((theRate == 0) || (theRate == 100)) {
                    readWriteDialog.dismiss();
                    readWriteDialog.setProgress(0);
                } else {
                    readWriteDialog.setMessage(theMsg);
                    readWriteDialog.setProgress(theRate);
                    if (!readWriteDialog.isShowing()) {
                        readWriteDialog.show();
                    }
                }
            }
        });
    }

    //隐藏进度条
    private void hidDialog() {
        //关闭进度条显示
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (readWriteDialog.isShowing()) {
                    readWriteDialog.dismiss();
                }
                readWriteDialog.setProgress(0);
            }
        });
    }
}
