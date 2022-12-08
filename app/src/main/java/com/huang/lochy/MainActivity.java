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

import com.dk.log.DKLog;
import com.dk.log.DKLogCallback;
import com.dk.uartnfc.Card.Card;
import com.dk.uartnfc.Card.CpuCard;
import com.dk.uartnfc.Card.DESFire;
import com.dk.uartnfc.Card.FeliCa;
import com.dk.uartnfc.Card.Iso14443bCard;
import com.dk.uartnfc.Card.Iso15693Card;
import com.dk.uartnfc.Card.Mifare;
import com.dk.uartnfc.Card.Ntag21x;
import com.dk.uartnfc.Card.YCTCard;
import com.dk.uartnfc.DKCloudID.IDCardData;
import com.dk.uartnfc.DeviceManager.Command;
import com.dk.uartnfc.DeviceManager.DeviceManager;
import com.dk.uartnfc.DeviceManager.DeviceManagerCallback;
import com.dk.uartnfc.DeviceManager.UartNfcDevice;
import com.dk.uartnfc.Exception.CardNoResponseException;
import com.dk.uartnfc.Exception.DeviceNoResponseException;
import com.dk.uartnfc.OTA.YModem;
import com.dk.uartnfc.Tool.StringTool;
import com.dk.uartnfc.UartManager.DKMessageDef;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    final static String TAG = "DKCloudID";

    final String[] botes = new String[]{"9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000"};

    private TextView msgTextView;
    private TextView delayTextView = null;
    private Spinner spSerial;
    private EditText edInput;
    private Button btOpen;
    private MyTTS myTTS;
    static long time_start = 0;
    static long time_end = 0;

    private UartNfcDevice uartNfcDevice;
    String selectSerialName;
    String selectBaudRate;

    private static String server_delay = "";
    private static int net_status = 1;

    private ProgressDialog readWriteDialog = null;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //语音初始化
        myTTS = new MyTTS(MainActivity.this);

        //设备初始化
        uartNfcDevice = new UartNfcDevice();
        uartNfcDevice.setCallBack(deviceManagerCallback);

        //设置本地安全模块解码模式（需要带安全模块的读卡模块才支持此模式）
        //uartNfcDevice.setSamvMode(DeviceManager.SAMV_MODE_LOCAL);

        //UI初始化
        iniview();
        edInput.setText("AA09A108041456FF0001EF");

        //日志初始化
        DKLog.setLogCallback(logCallback);

        //打开串口
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                String portName = "/dev/ttyS2";
                boolean isSuc = uartNfcDevice.serialManager.open(portName, "115200");
                if (isSuc) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    logViewln(portName + " 打开成功");
                    try {
                        uartNfcDevice.serialManager.sendWithReturn(StringTool.hexStringToBytes("AA09A108041456FF0001EF"));

                        logViewln("模块固件版本：" + uartNfcDevice.getFirmwareVersion());
                    } catch (DeviceNoResponseException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    logViewln(portName + "打开失败，请手动选择正确的串口号并点击打开按键");
                }
            }
        }).start();

        //网络状态Demo线程
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    String lost = new String();
                    String delay = new String();

                    try {
                        Process p = Runtime.getRuntime().exec("ping -c 1 -w 10 " + "www.dkcloudid.cn");
                        net_status = p.waitFor();
                        //DKLog.d(TAG, "Process:" + net_status );

                        if (net_status == 0) {
                            BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
                            String str = new String();
                            while (true) {
                                try {
                                    if (!((str = buf.readLine()) != null)) break;
                                } catch (IOException e) {
                                    DKLog.e(TAG, e);
                                }

                                if (str.contains("avg")) {
                                    int i = str.indexOf("/", 20);
                                    int j = str.indexOf(".", i);

                                    delay = str.substring(i + 1, j);
                                    server_delay = delay;
                                }
                            }

                            //DKLog.d(TAG, "延迟:" + delay + "ms");
                        }
                        else {
                            //DKLog.d(TAG, "网络未连接！");
                        }
                    } catch (Exception e) {
                        DKLog.e(TAG, e);
                    }

                    showNETDelay();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uartNfcDevice.destroy();
    }

    //日志回调
    private DKLogCallback logCallback = new DKLogCallback() {
        @Override
        public void onReceiveLogI(String tag, String msg) {
            super.onReceiveLogI(tag, msg);
            Log.i(tag, msg);
            logViewln("[I] " + msg);
        }

        @Override
        public void onReceiveLogD(String tag, String msg) {
            super.onReceiveLogD(tag, msg);
            Log.d(tag, msg);
            logViewln("[D] " + msg);
        }

        @Override
        public void onReceiveLogE(String tag, String msg) {
            super.onReceiveLogE(tag, msg);
            Log.e(tag, msg);
            logViewln("[E] " + msg);
        }
    };

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        //非接寻卡回调
        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            System.out.println("Activity接收到激活卡片回调：UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS) + "cardType=" + cardType);
            if (cardType == 7) {
                logViewln(null);
                logViewln("寻到125KID卡->UID:" + StringTool.byteHexToSting(bytCardSn));
                return;
            }

            final int cardTypeTemp = cardType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if ( !readWriteCardDemo(cardTypeTemp) ) {
//                        try {
//                            new Card(uartNfcDevice).close();
//                        } catch (CardNoResponseException e) {
//                            e.printStackTrace();
//                        }
                    }
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
                    logViewln("寻到CPU卡->UID:" + cpuCard.uidToString());
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
                    logViewln("寻到Ultralight卡->UID:" + ntag21x.uidToString());
                }
                break;
            case DeviceManager.CARD_TYPE_MIFARE:   //寻到Mifare卡
                final Mifare mifare = (Mifare) uartNfcDevice.getCard();
//                if (mifare != null) {
//                    logViewln(null);
//                    logViewln("寻到Mifare卡->UID:" + mifare.uidToString());
//                }
                break;
            case DeviceManager.CARD_TYPE_ISO15693: //寻到15693卡
                final Iso15693Card iso15693Card = (Iso15693Card) uartNfcDevice.getCard();
                if (iso15693Card != null) {
                    logViewln(null);
                    logViewln("寻到15693卡->UID:" + iso15693Card.uidToString());
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
                    try {
                        logViewln("发送:" + "0084000008");
                        byte[] rsp = iso14443bCard.transceive(StringTool.hexStringToBytes("0084000008"), 1000);
                        logViewln("接收:" + StringTool.byteHexToSting(rsp));
                    } catch (CardNoResponseException e) {
                        e.printStackTrace();
                    }
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
        delayTextView = findViewById(R.id.delayTextView);

        msgTextView.setKeyListener(null);
        msgTextView.setTextIsSelectable(true);

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

                final InputStream file = getResources().openRawResource(R.raw.dk26me_v7c);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //让模块进入升级模式
                        uartNfcDevice.serialManager.send(StringTool.hexStringToBytes("AA0124"));

                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    new YModem(uartNfcDevice.serialManager).send(file, new YModem.onReceiveScheduleListener() {
                                        @Override
                                        public void onReceiveSchedule(int rate) {  //读取进度回调
                                            showReadWriteDialog("正在升级", rate);
                                        }
                                    });

                                    logViewln( "升级成功，正在等待模块重启..." );
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

                                int cnt = 0;
                                String version = null;
                                do {
                                    try {
                                        version = uartNfcDevice.getFirmwareVersion();
                                        break;
                                    } catch (DeviceNoResponseException e) {
                                        e.printStackTrace();
                                    }

                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }while (cnt++ < 10);
                                logViewln( null );
                                if ( (version == null) || (!version.equals("7C")) ) {
                                    logViewln("升级失败，当前固件版本：" + version);
                                }
                                else {
                                    logViewln("升级成功，当前固件版本：" + version);
                                }
                            }
                        }).start();
                    }
                }).start();
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

//                if (msgTextView.length() > 1000) {
//                    msgTextView.setText("");
//                }
                msgTextView.append(msg + "\r\n");
                int offset = msgTextView.getLineCount() * msgTextView.getLineHeight();
                if(offset > msgTextView.getHeight()){
                    msgTextView.scrollTo(0,offset-msgTextView.getHeight());
                }
            }
        });
    }

    //显示网络延迟
    private void showNETDelay() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ( (net_status == 0) && (server_delay != null) && (server_delay.length() > 0) ) {
                    int delay = Integer.parseInt(server_delay);
                    String pj = "优";
                    if (delay < 30) {
                        pj = "优";
                        delayTextView.setTextColor(0xF000ff00);
                    } else if (delay < 50) {
                        pj = "良";
                        delayTextView.setTextColor(0xF0EEC900);
                    } else if (delay < 100) {
                        pj = "差";
                        delayTextView.setTextColor(0xF0FF0000);
                    } else {
                        pj = "极差";
                        delayTextView.setTextColor(0xF0B22222);
                    }
                    delayTextView.setText("网络延迟：" + server_delay + "ms " + " 等级：" + pj);
                }
                else {
                    delayTextView.setTextColor(0xF0B22222);
                    delayTextView.setText("网络未连接！");
                }
            }
        });
    }

    private void showIDMsg(IDCardData msg) {
        final IDCardData idCardData = msg ;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (uartNfcDevice.samvMode == uartNfcDevice.SAMV_MODE_LOCAL) {
                    time_end = System.currentTimeMillis();
                }
                msgTextView.setText("");
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
