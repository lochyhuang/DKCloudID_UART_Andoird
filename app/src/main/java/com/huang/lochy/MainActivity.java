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

import com.DKCloudID.DKCloudID;
import com.DKCloudID.IDCard;
import com.DKCloudID.IDCardData;
import com.Exception.CardNoResponseException;
import com.Exception.DKCloudIDException;
import com.OTA.DialogUtils;
import com.OTA.YModem;
import com.Exception.DeviceNoResponseException;
import com.dk.uartnfc.SamVIdCard;
import com.dk.uartnfc.SerialManager;
import com.Tool.StringTool;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.dk.uartnfc.SamVIdCard.SAM_V_FRAME_START_CODE;
import static com.dk.uartnfc.SamVIdCard.SAM_V_INIT_COM;

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

    SerialManager serialManager;
    String selectSerialName;
    String selectBaudRate;

    byte[] initData;
    IDCard idCard = null;

    private ProgressDialog readWriteDialog = null;

    final Semaphore semaphore = new Semaphore(1);

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //语音初始化
        myTTS = new MyTTS(MainActivity.this);

        //串口初始化
        serialManager = new SerialManager();

        //设置串口数据接收监听
        serialManager.setOnReceiveDataListener(new SerialManager.onReceiveDataListener() {
            @Override
            public void OnReceiverData(String portNumberString, byte[] dataBytes) {
                final String portNumber = portNumberString;
                final byte[] data = dataBytes;

                synchronized (this) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "[MainActivity]" + portNumber + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");

                            if ((data.length >= 3) && (data[0] == (byte) 0xAA)) {
                                if (StringTool.byteHexToSting(data).equals("AA01EA")) {
                                    refreshLogView("卡片已拿开！\r\n");
                                    hidDialog();
                                }
//                                else if ((data.length > 5)            //寻到IC卡，提取UID
//                                        && (data[0] == (byte) 0xAA)
//                                        && (data[2] == (byte) 0x01)) {
//                                    //截取UID
//                                    final byte[] uidBytes = new byte[(data[1] & 0xFF) - 2];
//                                    System.arraycopy(data, 4, uidBytes, 0, uidBytes.length);
//
//                                    runOnUiThread(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            msgTextView.setText("");
//                                            refreshLogView("寻卡卡片，UID: " + StringTool.byteHexToSting(uidBytes) + "\r\n");
//                                        }
//                                    });
//                                }
                            else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            msgTextView.setText("");
                                            refreshLogView(portNumber + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");
                                        }
                                    });
                                }
                            } else if ((data.length >= 3) && (data[0] == SAM_V_FRAME_START_CODE) && (data[3] == SAM_V_INIT_COM)) {
                                try {
                                    if ( !semaphore.tryAcquire(10, TimeUnit.MILLISECONDS) ) {
                                        return;
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                    return;
                                }

                                //校验数据
                                try {
                                    SamVIdCard.verify(data);
                                } catch (CardNoResponseException e) {
                                    e.printStackTrace();

                                    logViewln("正在重新解析..");
                                    serialManager.send(StringTool.hexStringToBytes("AA0118"));
                                    return;
                                }

                                Log.d(TAG, "开始解析");
                                logViewln(null);
                                logViewln("正在读卡，请勿移动身份证!");
                                myTTS.speak("正在读卡，请勿移动身份证");

                                initData = Arrays.copyOfRange(data, 4, data.length - 1);
                                SamVIdCard samVIdCard = new SamVIdCard(serialManager, initData);

                                //关闭上一次的云解码
                                idCard = new IDCard(samVIdCard);
                                time_start = System.currentTimeMillis();
                                int cnt = 0;
                                do {
                                    try {
                                        /**
                                         * 获取身份证数据，带进度回调，如果不需要进度回调可以去掉进度回调参数或者传入null
                                         * 注意：此方法为同步阻塞方式，需要一定时间才能返回身份证数据，期间身份证不能离开读卡器！
                                         */
                                        IDCardData idCardData = idCard.getIDCardData(new IDCard.onReceiveScheduleListener() {
                                            @Override
                                            public void onReceiveSchedule(int rate) {  //读取进度回调
                                                showReadWriteDialog("正在读取身份证信息,请不要移动身份证", rate);
                                                if (rate == 100) {
                                                    time_end = System.currentTimeMillis();
                                                    /**
                                                     * 这里已经完成读卡，可以开身份证了，在此提示用户读取成功或者打开蜂鸣器提示可以拿开身份证了
                                                     */
                                                    myTTS.speak("读取成功");

                                                    //发送蜂鸣器控制命令
                                                    serialManager.send(StringTool.hexStringToBytes("AA03B20A01"));
                                                }
                                            }
                                        });

                                        /**
                                         * 显示身份证数据
                                         */
                                        showIDMsg(idCardData);
                                        semaphore.release();
                                        //返回读取成功
                                        return;
                                    } catch (DKCloudIDException e) {   //服务器返回异常，重复5次解析
                                        e.printStackTrace();

                                        //显示错误信息
                                        logViewln(e.getMessage());
                                    } catch (CardNoResponseException e) {    //卡片读取异常，直接退出，需要重新读卡
                                        e.printStackTrace();

                                        //显示错误信息
                                        logViewln(e.getMessage());

                                        //返回读取失败
                                        myTTS.speak("请不要移动身份证");
                                        logViewln("正在重新解析..");
                                        serialManager.send(StringTool.hexStringToBytes("AA0118"));
                                        semaphore.release();
                                        return;
                                    } finally {
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
                                } while (cnt++ < 5);  //如果服务器返回异常则重复读5次直到成功

                                semaphore.release();

                            } else if (StringTool.byteHexToSting(data).equals("aa01ea")) {
                                Log.d(TAG, "卡片已经拿开");
                                hidDialog();
                            }
                        }
                    }).start();
                }
            }
        });

        //UI初始化
        iniview();
        edInput.setText("aa020401");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serialManager != null) {
            serialManager.close();
        }

        DKCloudID.Close();
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

        final List<String> ports = serialManager.getAvailablePorts();  //获取可用的串口
        Log.d(TAG, "可用的串口列表为：" + ports.toString());

        //配置选择串口号的选择器
        SpAdapter spAdapter = new SpAdapter(this);
        spAdapter.setDatas( ports.toArray(new String[ports.size()]) );
        spSerial.setAdapter(spAdapter);
        spSerial.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectSerialName = ports.get(position);
                if ( serialManager.isOpen() ) {
                    serialManager.close();
                    serialManager.open(selectSerialName, selectBaudRate);
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
                if ( serialManager.isOpen() ) {
                    serialManager.close();
                    serialManager.open(selectSerialName, selectBaudRate);
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
                if ( serialManager.isOpen() ) {
                    serialManager.close();
                }
                else {
                    serialManager.open(selectSerialName, selectBaudRate);
                    //serialManager.open("/dev/ttyS3", "115200");
                }

                updataSendBt();
            }
        });

        //固件升级按键监听
        btOTA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!serialManager.isOpen()) {
                    Toast.makeText(getBaseContext(), "串口未打开！", Toast.LENGTH_SHORT).show();
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //让模块进入升级模式
                        serialManager.send(StringTool.hexStringToBytes("AA0124"));
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
                                        new YModem(serialManager).send(new File(fileName), new YModem.onReceiveScheduleListener() {
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
                    if (serialManager.isOpen()) {
                        serialManager.send( StringTool.hexStringToBytes( edInput.getText().toString()) );
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

        msgTextView.setText("正在搜索设备...");
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                int i = 0;
//
//                for (String thePort:ports) {
//                    try {
//                        try {
//                            Thread.sleep(100);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//
//                        if (serialManager.open(thePort, "115200")) {
//                            byte[] bytes = serialManager.sendWithReturn(new byte[]{(byte) 0xAA, 0x01, (byte)0xB0}, 200);
//                            if (bytes[0] == (byte) 0xAA) {
//
//                                final int selection = i;
//                                runOnUiThread(new Runnable() {
//                                    @Override
//                                    public void run() {
//                                        spSerial.setSelection(selection);
//                                        msgTextView.setText("找到串口" + selectSerialName + "上的设备");
//                                    }
//                                });
//
//                                try {
//                                    serialManager.sendWithReturn(StringTool.hexStringToBytes("AA0495FF1476"));  //配置NFC模块
//                                } catch (DeviceNoResponseException e1) {
//                                    e1.printStackTrace();
//                                }
//                                break;
//                            } else {
//                                serialManager.close();
//                            }
//                        }
//                    } catch (DeviceNoResponseException e) {
//                        e.printStackTrace();
//                        serialManager.close();
//                    }
//                    i++;
//                }
//            }
//        }).start();
    }

    //更新按键状态
    private void updataSendBt() {
        if ( serialManager.isOpen() ) {
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

                if (msgTextView.length() > 500) {
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                readWriteDialog.dismiss();
            }
        });
    }
}
