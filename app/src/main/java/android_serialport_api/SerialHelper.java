package android_serialport_api;

import android.os.CountDownTimer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;


/**
 * @author benjaminwan
 *串口辅助工具类
 */
public abstract class SerialHelper{
	private SerialPort mSerialPort;
	private OutputStream mOutputStream;
	private InputStream mInputStream;
	private ReadThread mReadThread;
	private SendThread mSendThread;
	private String sPort="/dev/ttySAC2";
	private int iBaudRate=9600;
	private boolean _isOpen=false;
	private byte[] _bLoopData=new byte[]{0x30};
	private int iDelay=500;

	private static boolean timerOutFlag = false;
	private final CountDownTimer timer = new CountDownTimer(500, 500) {
		@Override
		public void onTick(long millisUntilFinished) {

		}
		@Override
		public void onFinish() {
			//timer.start();
			timerOutFlag = true;
			timer.cancel();
		}
	};

	//----------------------------------------------------
	public SerialHelper(String sPort, int iBaudRate){
		this.sPort = sPort;
		this.iBaudRate=iBaudRate;
	}
	public SerialHelper(){
		this("/dev/ttySAC2", 115200);
	}
	public SerialHelper(String sPort){
		this(sPort,115200);
	}
	public SerialHelper(String sPort, String sBaudRate){
		this(sPort, Integer.parseInt(sBaudRate));
	}
	//----------------------------------------------------
	public void open() throws SecurityException, IOException,InvalidParameterException {
		mSerialPort =  new SerialPort(new File(sPort), iBaudRate, 0);
		mOutputStream = mSerialPort.getOutputStream();
		mInputStream = mSerialPort.getInputStream();
		mReadThread = new ReadThread();
		mReadThread.start();
//		mSendThread = new SendThread();
//		mSendThread.setSuspendFlag();
//		mSendThread.start();
		_isOpen=true;
	}
	//----------------------------------------------------
	public void close(){
		if (mReadThread != null)
			mReadThread.interrupt();
		if (mSerialPort != null) {
			mSerialPort.close();
			mSerialPort = null;
		}
		_isOpen=false;
	}
	//----------------------------------------------------
	public void send(byte[] bOutArray){
		try
		{
			mOutputStream.write(bOutArray);
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	//----------------------------------------------------
	public void sendHex(String sHex){
		byte[] bOutArray = StringTool.hexStringToBytes(sHex);
		send(bOutArray);
	}
	//----------------------------------------------------
	public void sendTxt(String sTxt){
		byte[] bOutArray =sTxt.getBytes();
		send(bOutArray);
	}
	//----------------------------------------------------
	private class ReadThread extends Thread {
		@Override
		public void run() {
			super.run();
            byte[] buffer = new byte[3000];
            byte[] temp = new byte[3000];
            int readLen = 0;
            int totalLen = 0;
			while( !isInterrupted() ) {
				try {
					if (mInputStream == null) {
						return;
					}

//					byte[] buffer=new byte[512];
//					int size = mInputStream.read(buffer);
//					if (size > 0){
//						ComBean ComRecData = new ComBean(sPort,buffer,size);
//						System.out.println("接收到数据：" + size);
//						onDataReceived(ComRecData);
//					}

                    readLen = 0;
                    totalLen = 0;
					int len = mInputStream.read(temp);
					//int len = mSerialPort.read(temp, 100);
					//System.out.println("接收到数据：" + len);

//					if ( (len >= 3) && (temp[0] == (byte)0xBB) ) {
//						totalLen = ((temp[1] & 0xff) << 8) | (temp[2] & 0xff);
//						totalLen += 4;
//						System.arraycopy(temp, 0, buffer, readLen, len);
//						readLen += len;
//						if ( totalLen < 500 ) {
//							timerOutFlag = false;
//							timer.start();
//							while ( (readLen < totalLen) && !timerOutFlag ) {
//								//if (mInputStream.available() > 0) {
//									len = mInputStream.read(temp);
//									//len = mSerialPort.read(temp, 100);
//									if ( len > 0 ) {
//										//System.out.println("长度：" + len);
//										System.arraycopy(temp, 0, buffer, readLen, len);
//										readLen += len;
//										timer.cancel();
//										timer.start();
//									}
//								//}
//							}
//							timer.cancel();
//							byte[] readBytes = new byte[readLen];
//							System.arraycopy(buffer, 0, readBytes, 0, readLen);
//							ComBean ComRecData = new ComBean(sPort, readBytes, readLen);
//							//System.out.println("接收到数据：" + readLen);
//							onDataReceived(ComRecData);
//						}
//					}
//					else
					if (len > 0){
						byte[] readBytes = new byte[len];
						System.arraycopy(temp, 0, readBytes, 0, len);
						ComBean ComRecData = new ComBean(sPort, readBytes, len);
						//System.out.println("接收到数据：" + readLen);
						onDataReceived(ComRecData);
					}
				} catch (Throwable e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}
	//----------------------------------------------------
	private class SendThread extends Thread {
		public boolean suspendFlag = true;// 控制线程的执行
		@Override
		public void run() {
			super.run();
			while(!isInterrupted()) {
				synchronized (this) {
					while (suspendFlag) {
						try {
							wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				send(getbLoopData());
//				try {
//					Thread.sleep(iDelay);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
			}
		}

		//线程暂停
		public void setSuspendFlag() {
			this.suspendFlag = true;
		}

		//唤醒线程
		public synchronized void setResume() {
			this.suspendFlag = false;
			notify();
		}
	}
	//----------------------------------------------------
	public int getBaudRate()
	{
		return iBaudRate;
	}
	public boolean setBaudRate(int iBaud)
	{
		if (_isOpen)
		{
			return false;
		} else
		{
			iBaudRate = iBaud;
			return true;
		}
	}
	public boolean setBaudRate(String sBaud)
	{
		int iBaud = Integer.parseInt(sBaud);
		return setBaudRate(iBaud);
	}
	//----------------------------------------------------
	public String getPort()
	{
		return sPort;
	}
	public boolean setPort(String sPort)
	{
		if (_isOpen)
		{
			return false;
		} else
		{
			this.sPort = sPort;
			return true;
		}
	}
	//----------------------------------------------------
	public boolean isOpen()
	{
		return _isOpen;
	}
	//----------------------------------------------------
	public byte[] getbLoopData()
	{
		return _bLoopData;
	}
	//----------------------------------------------------
	public void setbLoopData(byte[] bLoopData)
	{
		this._bLoopData = bLoopData;
	}
	//----------------------------------------------------
	public void setTxtLoopData(String sTxt){
		this._bLoopData = sTxt.getBytes();
	}
	//----------------------------------------------------
	public void setHexLoopData(String sHex){
		this._bLoopData = StringTool.hexStringToBytes(sHex);
	}
	//----------------------------------------------------
	public int getiDelay()
	{
		return iDelay;
	}
	//----------------------------------------------------
	public void setiDelay(int iDelay)
	{
		this.iDelay = iDelay;
	}
	//----------------------------------------------------
	public void startSend()
	{
		if (mSendThread != null)
		{
			mSendThread.setResume();
		}
	}
	//----------------------------------------------------
	public void stopSend()
	{
		if (mSendThread != null)
		{
			mSendThread.setSuspendFlag();
		}
	}
	//----------------------------------------------------
	protected abstract void onDataReceived(ComBean ComRecData);
}