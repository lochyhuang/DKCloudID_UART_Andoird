package com.dk.uartnfc;

import android.os.CountDownTimer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android_serialport_api.ComBean;
import android_serialport_api.SerialHelper;
import android_serialport_api.SerialPortFinder;

import static com.dk.uartnfc.DeviceNoResponseException.DEVICE_NO_RESPONSE;

/**
 * Created by lochy on 2018-08-17.
 */

public class SerialManager {
    final static int DEVICE_NO_RESPONSE_TIME = 5000;  //设备无响应等待时间5000ms
    public SerialPortFinder serialPortFinder;
    public SerialHelper serialHelper;
    private String comPortName;
    private onReceiveDataListener mOnReceiveDataListener;
    public onReceiveDataListener gOnReceiveDataListener;

    private byte[] rcvBuffer = new byte[5000];
    private int rcvLen = 0;
    private boolean rcvDataFlag = false;

    public interface onReceiveDataListener {
        public void OnReceiverData(String portNumberString, byte[] data);
    }

    public void setOnReceiveDataListener(onReceiveDataListener l) {
        this.gOnReceiveDataListener = l;
    }

    private final CountDownTimer timer = new CountDownTimer(1, 1) {
        @Override
        public void onTick(long millisUntilFinished) {

        }
        @Override
        public void onFinish() {
            //timer.start();
            timer.cancel();
            byte[] readBytes = new byte[rcvLen];
            System.arraycopy(rcvBuffer, 0, readBytes, 0, rcvLen);
            rcvLen = 0;

            if (mOnReceiveDataListener != null) {
                mOnReceiveDataListener.OnReceiverData( comPortName, readBytes );
            }

            if (gOnReceiveDataListener != null) {
                gOnReceiveDataListener.OnReceiverData( comPortName, readBytes );
            }

            //System.out.println( "串口\"" + comPortName + "\"接收到数据1：" + StringTool.byteHexToSting(readBytes) );
        }
    };

    public SerialManager() {
        serialPortFinder = new SerialPortFinder();
        serialHelper = new SerialHelper() {
            @Override
            protected void onDataReceived(final ComBean comBean) {
                if (rcvLen + comBean.bRec.length > rcvBuffer.length) {
                    timer.cancel();
                    rcvLen = 0;
                    return;
                }

                if ( comBean.bRec.length == 0 ) {
                    return;
                }

                comPortName = comBean.sComPort;
                System.arraycopy(comBean.bRec, 0, rcvBuffer, rcvLen, comBean.bRec.length);
                rcvLen += comBean.bRec.length;
                //System.out.println("串口\"" + comBean.sComPort + "\"接收到数据：" + FuncUtil.ByteArrToHex(comBean.bRec));

                timer.cancel();
                timer.start();
            }
        };
    }

    /**
     * 串口发送数据, 同步阻塞方式
     * @param msg 要发送的数据
     * @throws DeviceNoResponseException 设备无响应会抛出此异常
     */
    public byte[] sendWithReturn(byte[] msg) throws DeviceNoResponseException {
        if ( !serialHelper.isOpen() ) {
            throw new DeviceNoResponseException("Serial port is close!");
        }

        final byte[][] returnBytes = new byte[1][1];
        final boolean[] isCmdRunSucFlag = {false};

        final Semaphore semaphore = new Semaphore(0);
        returnBytes[0] = null;

        send(msg, new onReceiveDataListener() {
            @Override
            public void OnReceiverData(String portNumberString, byte[] data) {
                if ( data != null ) {
                    returnBytes[0] = data;
                    isCmdRunSucFlag[0] = true;
                }
                else {
                    returnBytes[0] = null;
                    isCmdRunSucFlag[0] = false;
                }
                semaphore.release();
            }
        });

        try {
            semaphore.tryAcquire(DEVICE_NO_RESPONSE_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new DeviceNoResponseException(DEVICE_NO_RESPONSE);
        }
        if (!isCmdRunSucFlag[0]) {
            throw new DeviceNoResponseException(DEVICE_NO_RESPONSE);
        }
        return returnBytes[0];
    }

    /**
     * 串口发送数据, 同步阻塞方式
     * @param msg 要发送的数据
     * @param timeOutMs 超时时间，单位MS
     * @throws DeviceNoResponseException 设备无响应会抛出此异常
     */
    public byte[] sendWithReturn(byte[] msg, int timeOutMs) throws DeviceNoResponseException {
        if ( !serialHelper.isOpen() ) {
            throw new DeviceNoResponseException("Serial port is close!");
        }

        final byte[][] returnBytes = new byte[1][1];
        final boolean[] isCmdRunSucFlag = {false};

        final Semaphore semaphore = new Semaphore(0);
        returnBytes[0] = null;

        send(msg, new onReceiveDataListener() {
            @Override
            public void OnReceiverData(String portNumberString, byte[] data) {
                if ( data != null ) {
                    returnBytes[0] = data;
                    isCmdRunSucFlag[0] = true;
                }
                else {
                    returnBytes[0] = null;
                    isCmdRunSucFlag[0] = false;
                }
                semaphore.release();
            }
        });

        try {
            semaphore.tryAcquire(timeOutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new DeviceNoResponseException(DEVICE_NO_RESPONSE);
        }
        if (!isCmdRunSucFlag[0]) {
            throw new DeviceNoResponseException(DEVICE_NO_RESPONSE);
        }
        return returnBytes[0];
    }

    /**
     * 串口发送数据
     * @param msg 要发送的数据
     */
    public void send(byte[] msg) {
        serialHelper.send(msg);
        //System.out.println("串口" + "\"" + serialHelper.getPort() + "\"发送数据：" + StringTool.byteHexToSting(msg) );
    }

    /**
     * 串口发送数据，带异步回调
     * @param msg 要发送的数据
     * @param listener 命令返回回调
     */
    public void send(byte[] msg, onReceiveDataListener listener) {
        mOnReceiveDataListener = listener;
        send(msg);
    }

    /**
     * 获取所有可用的、没有被占用的串口列表
     * @return         所有可用的、没有被占用的串口
     */
    public List<String> getAvailablePorts() {
        List<String> availablePorts = new ArrayList<String>();
        final String[] ports = serialPortFinder.getAllDevicesPath();
        availablePorts.addAll(Arrays.asList(ports));

        return availablePorts;
    }

    /**
     * 打开串口
     * @param          portName - 串口名称
     * @param          baudRate - 波特率
     * @return         true - 操作成功
     *                  false - 操作失败
     */
    public boolean open(String portName, String baudRate) {
        if ( (portName == null) || (baudRate == null) ) {
            return false;
        }
        serialHelper.setBaudRate(baudRate);
        serialHelper.setPort(portName);
        try {
            serialHelper.open();
            System.out.println("打开串口\"" + portName + "\"成功！");
            return true;
        } catch (Exception e) {
            System.out.println("打开串口\"" + portName + "\"失败！");
            return false;
        }
    }

    /**
     * 关闭串口
     */
    public void close() {
        if ( serialHelper.isOpen() ) {
            serialHelper.close();
        }
    }

    /**
     * 获取串口打开状态
     * @return         true - 串口已打开
     *                  false - 串口已关闭
     */
    public boolean isOpen() {
        return serialHelper.isOpen();
    }
}
