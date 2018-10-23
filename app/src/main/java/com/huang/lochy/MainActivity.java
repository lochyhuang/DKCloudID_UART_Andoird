package com.huang.lochy;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dk.uartnfc.R;
import com.dk.uartnfc.SerialManager;
import com.dk.uartnfc.StringTool;

import java.util.Arrays;
import java.util.List;

import static com.huang.lochy.ClientDispatcher.SAM_V_ERROR_COM;
import static com.huang.lochy.ClientDispatcher.SAM_V_FRAME_START_CODE;
import static com.huang.lochy.ClientDispatcher.SAM_V_INIT_COM;

public class MainActivity extends AppCompatActivity {
    final String[] botes = new String[]{"9600", "19200", "38400", "57600", "115200", "230400", "460800", "500000", "576000", "921600", "1000000", "1152000"};

    private static TextView msgTextView;
    private Spinner spSerial;
    private EditText edInput;
    private Button btSend;
    private Spinner spBote;
    private Button btOpen;

    SerialManager serialManager;
//    SerialManager serialManager1;
    String selectSerialName;
    String selectBaudRate;

    ClientDispatcher clientDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        msgTextView = findViewById(R.id.msgText);
        spSerial = findViewById(R.id.sp_serial);
        edInput = findViewById(R.id.ed_input);
        btSend = findViewById(R.id.bt_send);
        spBote = findViewById(R.id.sp_bote);
        btOpen = findViewById(R.id.bt_open);

        serialManager = new SerialManager();

        clientDispatcher = new ClientDispatcher(this);
        clientDispatcher.setSerialRXTX(serialManager);

        //设置串口数据接收监听
        serialManager.setOnReceiveDataListener(new SerialManager.onReceiveDataListener() {
            @Override
            public void OnReceiverData(String portNumberString, byte[] data) {
                if ( data[0] == (byte)0xAA ) {
                    if (StringTool.byteHexToSting(data).equals("AA01EA")) {
                        refreshLogView("卡片已拿开！\r\n");
                    }
                    else {
                        msgTextView.setText("");
                        refreshLogView(portNumberString + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");
                    }
                }
                //System.out.println(portNumberString + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");
                if ( (data != null) && (data[0] == SAM_V_FRAME_START_CODE) ) {
                    //数据长度校验
                    if ( data.length < 6 ) {
                        MainActivity.logViewln( "数据长度错误！" );
                        if ( clientDispatcher != null ) {
                            clientDispatcher.Close();
                        }
                        return;
                    }

                    //和校验
                    byte bcc_sum = 0;
                    for ( int i=0; i<data.length - 1; i++ ) {
                        bcc_sum ^= data[i];
                    }
                    if ( bcc_sum != data[data.length - 1] ) {
                        System.out.println("和校验失败");
                        MainActivity.logViewln( "和校验失败！" );
                        if ( clientDispatcher != null ) {
                            clientDispatcher.Close();
                        }
                        return;
                    }

                    switch ( data[3] ) {
                        case SAM_V_INIT_COM:      //接收到开始解析请求
                            System.out.println("开始解析");
                            msgTextView.setText("");
                            clientDispatcher = null;
                            clientDispatcher = new ClientDispatcher(MainActivity.this);
                            clientDispatcher.setSerialRXTX(serialManager);
                            byte[] dnBytes = Arrays.copyOfRange( data, 4, data.length - 1 );
                            clientDispatcher.setInitData(dnBytes);
                            new Thread(clientDispatcher).start();
                            break;

                        case SAM_V_ERROR_COM:
                            int errorCode = ((data[4] & 0xff) << 8) | (data[5] & 0xff);
                            MainActivity.logViewln( "解析出错：" + errorCode );
                            System.out.println("解析出错：" + errorCode);
                            if ( clientDispatcher != null ) {
                                //clientDispatcher.Close();
                                //重新解析
                                serialManager.send(StringTool.hexStringToBytes( "AA0118" ));
                            }
                            break;

                        default:
                            break;
                    }
                }
                else if (StringTool.byteHexToSting(data).equals("aa01ea")) {
                    if ( clientDispatcher != null ) {
                        clientDispatcher.Close();
                    }
                    System.out.println("卡片已经拿开");
                }
            }
        });

        iniview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serialManager != null) {
            serialManager.close();
        }
//        if (serialManager1 != null) {
//            serialManager1.close();
//        }
    }

    private void iniview() {
        final List<String> ports = serialManager.getAvailablePorts();  //获取可用的串口
        System.out.println("可用的串口列表为：" + ports.toString());

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
                    //serialManager.open("/dev/ttyUSB0", "1152000");
                }

                updataSendBt();
            }
        });

        //发送数据监听
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (edInput.getText().toString().length() > 0) {
                    if (serialManager.isOpen()) {
                        serialManager.send( StringTool.hexStringToBytes( edInput.getText().toString()) );
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
        msgTextView.append(msg);
        int offset = msgTextView.getLineCount() * msgTextView.getLineHeight();
        if(offset > msgTextView.getHeight()){
            msgTextView.scrollTo(0,offset-msgTextView.getHeight());
        }
    }

    static void logViewln(String msg) {
        if (msgTextView.length() > 500) {
            msgTextView.setText("");
        }
        msgTextView.append(msg + "\r\n");
        int offset = msgTextView.getLineCount() * msgTextView.getLineHeight();
        if(offset > msgTextView.getHeight()){
            msgTextView.scrollTo(0,offset-msgTextView.getHeight());
        }
    }

    static void showIDMsg(IDCardData idCardData) {
        msgTextView.append(idCardData.toString() + "\r\n");


        SpannableString ss = new SpannableString(msgTextView.getText().toString()+"[smile]");
        //得到要显示图片的资源
        Drawable d = Drawable.createFromPath("mnt/sdcard/photo.bmp");//new BitmapDrawable(idCardData.PhotoBmp);
        if (d != null) {
            //设置高度
            d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            //跨度底部应与周围文本的基线对齐
            ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BASELINE);
            //附加图片
            ss.setSpan(span, msgTextView.getText().length(),msgTextView.getText().length()+"[smile]".length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            msgTextView.setText(ss);
            //msgTextView.setText("\r\n");
            System.out.println(idCardData.PhotoBmp);
        }
    }
}
