package com.huang.lochy;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.style.ImageSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dk.uartnfc.DeviceNoResponseException;
import com.dk.uartnfc.R;
import com.dk.uartnfc.SerialManager;
import com.dk.uartnfc.StringTool;

import org.jmrtd.lds.iso19794.FaceImageInfo;
import org.jmrtd.lds.iso19794.FaceInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
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
    private AlertDialog alertDialog;

    SerialManager serialManager;
//    SerialManager serialManager1;
    String selectSerialName;
    String selectBaudRate;

    ClientDispatcher clientDispatcher;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        msgTextView = (TextView) findViewById(R.id.msgText);
        spSerial = (Spinner) findViewById(R.id.sp_serial);
        edInput = (EditText) findViewById(R.id.ed_input);
        btSend = (Button) findViewById(R.id.bt_send);
        spBote = (Spinner) findViewById(R.id.sp_bote);
        btOpen = (Button) findViewById(R.id.bt_open);

        serialManager = new SerialManager();

        edInput.setText("aa1930453935383732323635363932313031323532373032313234");

        clientDispatcher = new ClientDispatcher(this);
        clientDispatcher.setSerialRXTX(serialManager);

        //如果是护照则跳出机读码输入框
        LayoutInflater factory = LayoutInflater.from(MainActivity.this);
        final View textEntryView = factory.inflate(R.layout.layout, null);

        final EditText passportNoInputEditText = (EditText) textEntryView.findViewById(R.id.passportNoInputEditText);
        passportNoInputEditText.setText("E958722656");
        passportNoInputEditText.setHint(new SpannedString("请输入护照号(E123456789)"));
        passportNoInputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});

        final EditText birthDateInputEditText = (EditText) textEntryView.findViewById(R.id.birthDateInputEditText);
        birthDateInputEditText.setHint(new SpannedString("请输入出生日期(9012255)"));
        birthDateInputEditText.setText("9210125");
        birthDateInputEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        birthDateInputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});

        final EditText expiryDateInputEditText = (EditText) textEntryView.findViewById(R.id.expiryDateInputEditText);
        expiryDateInputEditText.setHint(new SpannedString("请输入有效期(2712257)"));
        expiryDateInputEditText.setText("2702124");
        expiryDateInputEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        expiryDateInputEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        alertDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle("请输入护照上的信息：")
                .setMessage("请输入护照号码、出生日期、有效期")
                .setView(textEntryView)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (passportNoInputEditText.getText().length() != 0
                                && birthDateInputEditText.getText().length() != 0
                                && expiryDateInputEditText.getText().length() != 0) {

                            //验证并读取文件
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String MZR_string = "" + passportNoInputEditText.getText() + birthDateInputEditText.getText() + expiryDateInputEditText.getText();
                                        byte MZR_bytes[] = MZR_string.getBytes();
                                        byte MZR_cmd_bytes[] = new byte[MZR_bytes.length + 3];
                                        MZR_cmd_bytes[0] = (byte)0xAA;
                                        MZR_cmd_bytes[1] = (byte)0x19;
                                        MZR_cmd_bytes[2] = (byte)0x30;
                                        System.arraycopy(MZR_bytes, 0, MZR_cmd_bytes, 3, MZR_bytes.length);

                                        byte[] returnBytes = serialManager.sendWithReturn(MZR_cmd_bytes, 500);
                                        if (StringTool.byteHexToSting(returnBytes).equals("AA01FE")) {
                                            //==============================读取文件1======================================
                                            returnBytes = serialManager.sendWithReturn(StringTool.hexStringToBytes("aa023101"), 200);

                                            //和校验
                                            byte bcc_sum = 0;
                                            for ( int i=0; i<returnBytes.length - 1; i++ ) {
                                                bcc_sum ^= returnBytes[i];
                                            }
                                            if ( bcc_sum != returnBytes[returnBytes.length - 1] ) {
                                                System.out.println("和校验失败");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        MainActivity.logViewln("和校验失败！");
                                                    }
                                                });
                                                return;
                                            }

                                            //提取数据
                                            byte dg1_byte[] = new byte[returnBytes.length - 5];
                                            System.arraycopy(returnBytes, 4, dg1_byte, 0, dg1_byte.length);

                                            final String dg1_String = new String(dg1_byte, "UTF-8");
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    MainActivity.logViewln( "DG1File： " + dg1_String );
                                                }
                                            });

                                            //==============================读取文件11======================================
                                            returnBytes = serialManager.sendWithReturn(StringTool.hexStringToBytes("aa02310B"), 200);

                                            //和校验
                                            bcc_sum = 0;
                                            for ( int i=0; i<returnBytes.length - 1; i++ ) {
                                                bcc_sum ^= returnBytes[i];
                                            }
                                            if ( bcc_sum != returnBytes[returnBytes.length - 1] ) {
                                                System.out.println("和校验失败");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        MainActivity.logViewln("和校验失败！");
                                                    }
                                                });
                                                return;
                                            }

                                            //提取数据
                                            byte dg11_byte[] = new byte[returnBytes.length - 5];
                                            System.arraycopy(returnBytes, 4, dg11_byte, 0, dg11_byte.length);

                                            final String dg11_String = new String(dg11_byte, "UTF-8");
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    MainActivity.logViewln( "DG11File： " + dg11_String );
                                                    MainActivity.logViewln( "照片读取中..."  );
                                                }
                                            });

                                            //==============================读取文件2======================================
                                            returnBytes = serialManager.sendWithReturn(StringTool.hexStringToBytes("aa023102"), 5000);

                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    msgTextView.setText( msgTextView.getText().toString().replace("\r\n照片读取中...", "") );
                                                }
                                            });

                                            //和校验
                                            bcc_sum = 0;
                                            for ( int i=0; i<returnBytes.length - 1; i++ ) {
                                                bcc_sum ^= returnBytes[i];
                                            }
                                            if ( bcc_sum != returnBytes[returnBytes.length - 1] ) {
                                                System.out.println("和校验失败");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        MainActivity.logViewln("和校验失败！");
                                                    }
                                                });
                                                return;
                                            }

                                            //提取数据
                                            final byte dg2_byte[] = new byte[returnBytes.length - 5];
                                            System.arraycopy(returnBytes, 4, dg2_byte, 0, dg2_byte.length);

                                            String dg2_String = StringTool.byteHexToSting(dg2_byte);

                                            //找到图片的位置
                                            int startIndex = dg2_String.indexOf("5F2E");
                                            if (startIndex < 0) {
                                                startIndex = dg2_String.indexOf("7F2E");
                                                if (startIndex < 0) {
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            MainActivity.logViewln("未找到图像数据！");
                                                        }
                                                    });
                                                    return;
                                                }
                                            }

                                            System.out.println("DG2 picture start index:" + startIndex);

                                            //提取照片长度
                                            dg2_String = dg2_String.substring(startIndex);
                                            int picture_data_len = Integer.parseInt(dg2_String.substring(6, 10), 16);
                                            System.out.println("DG2 picture data len:" + picture_data_len);
                                            dg2_String = dg2_String.substring(10);
                                            System.out.println("DG2 picture data:" + dg2_String);
                                            byte picture_bytes[] = StringTool.hexStringToBytes(dg2_String);

                                            //转换成照片
                                            try {
                                                FaceInfo faceInfo = new FaceInfo(new ByteArrayInputStream(picture_bytes));

                                                for (FaceImageInfo faceImageInfo : faceInfo.getFaceImageInfos() ) {
                                                    int imageLength = faceImageInfo.getImageLength();
                                                    DataInputStream dataInputStream = new DataInputStream(faceImageInfo.getImageInputStream());
                                                    byte[] buffer = new byte[imageLength];
                                                    dataInputStream.readFully(buffer, 0, imageLength);
                                                    InputStream inputStream = new ByteArrayInputStream(buffer, 0, imageLength);

                                                    Bitmap bitmap = ImageUtil.decodeImage(
                                                            MainActivity.this, faceImageInfo.getMimeType(), inputStream);

                                                    final SpannableString ss = new SpannableString(msgTextView.getText().toString().replace("\r\n照片读取中...", "") + "[smile]");
                                                    //得到要显示图片的资源
                                                    Drawable d = new BitmapDrawable(bitmap);//Drawable.createFromPath("mnt/sdcard/photo.bmp");
                                                    //设置高度
                                                    d.setBounds(0, 0, d.getIntrinsicWidth()/2, d.getIntrinsicHeight()/2);
                                                    //跨度底部应与周围文本的基线对齐
                                                    ImageSpan span = new ImageSpan(d, ImageSpan.ALIGN_BASELINE);
                                                    //附加图片
                                                    ss.setSpan(span, msgTextView.getText().length(),msgTextView.getText().length()+"[smile]".length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            msgTextView.setText(ss);
                                                        }
                                                    });
                                                    inputStream.close();
                                                }
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }finally {

                                            }

//                                            runOnUiThread(new Runnable() {
//                                                @Override
//                                                public void run() {
//                                                    MainActivity.logViewln( "DG2File： " + dg2_String.length() / 2 + "字节" );
//                                                }
//                                            });
                                        }
                                    } catch (DeviceNoResponseException e) {
                                        e.printStackTrace();
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();

                        }
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .create();

        //设置串口数据接收监听
        serialManager.setOnReceiveDataListener(new SerialManager.onReceiveDataListener() {
            @Override
            public void OnReceiverData(String portNumberString, byte[] dataBytes) {
                final String portNumber = portNumberString;
                final byte[] data = dataBytes;

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("[MainActivity]" + portNumber + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");

                        if ((data.length >= 3) && (data[0] == (byte)0xAA) ) {
                            if (StringTool.byteHexToSting(data).equals("AA01EA")) {
                                refreshLogView("卡片已拿开！\r\n");

                                //卡片拿开退出输入框
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        alertDialog.hide();
                                    }
                                });
                            }
                            else if ( (data.length > 4)            //寻到cpu卡，则当作护照处理
                                    && (data[0] == (byte)0xAA)
                                    && (data[2] == (byte)0x01)
                                    && ((data[3] == (byte)0x04) || (data[3] == (byte)0x03)) ) {

                                //判断是不是护照
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            byte[] returnBytes = serialManager.sendWithReturn(StringTool.hexStringToBytes("aa0d1600a4040c07a0000002471001"), 500);
                                            if (StringTool.byteHexToSting(returnBytes).equals("AA03169000")) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        alertDialog.show();
                                                    }
                                                });
                                            }
                                        } catch (DeviceNoResponseException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).start();
                            }
                            else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        msgTextView.setText("");
                                        refreshLogView(portNumber + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");
                                    }
                                });
                            }
                        }
                        else if ( (data.length >= 3) && (data[0] == SAM_V_FRAME_START_CODE) && (data[3] != 0x31) ) {
                            //数据长度校验
                            if ( data.length < 6 ) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.logViewln( "数据长度错误！" );
                                    }
                                });

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
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.logViewln( "和校验失败！" );
                                    }
                                });

                                if ( clientDispatcher != null ) {
                                    clientDispatcher.Close();
                                }
                                return;
                            }

                            switch ( data[3] ) {
                                case SAM_V_INIT_COM:      //接收到开始解析请求
                                    System.out.println("开始解析");
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            msgTextView.setText("");
                                        }
                                    });

                                    clientDispatcher = null;
                                    clientDispatcher = new ClientDispatcher(MainActivity.this);
                                    clientDispatcher.setSerialRXTX(serialManager);
                                    byte[] dnBytes = Arrays.copyOfRange( data, 4, data.length - 1 );
                                    clientDispatcher.setInitData(dnBytes);
                                    new Thread(clientDispatcher).start();
                                    break;

                                case SAM_V_ERROR_COM:
                                    final int errorCode = ((data[4] & 0xff) << 8) | (data[5] & 0xff);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            MainActivity.logViewln( "解析出错：" + errorCode );
                                        }
                                    });

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
                            alertDialog.hide();
                        }
                        else if (!((data.length > 3) && (data[0] == (byte)0xBB) && (data[3] == 0x31))){
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    msgTextView.setText("");
                                    refreshLogView(portNumber + "接收(" + data.length + ")：" + StringTool.byteHexToSting(data) + "\r\n");
                                }
                            });
                        }
                    }
                }).start();
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
                    //serialManager.open(selectSerialName, selectBaudRate);
                    serialManager.open("/dev/ttyS3", "115200");
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
//                int offset = msgTextView.getLineCount() * msgTextView.getLineHeight();
//                if(offset > msgTextView.getHeight()){
//                    msgTextView.scrollTo(0,offset-msgTextView.getHeight());
//                }
            }
        });
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
            System.out.println(idCardData.PhotoBmp);
        }
    }
}
