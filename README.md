# DKCloudID_UART_Android

#### 介绍
深圳市德科物联技术有限公司的串口身份证阅读器Demo, 支持DK26ME、DK26ME-ANT、DK25GM、DK25-ST等模块。更多产品信息请访问[德科官网](http://www.derkiot.com/)。

### 如何集成到项目中
 **Step 1. Add the JitPack repository to your build file**
 
打开根build.gradle文件，将maven { url 'https://jitpack.io' }添加到repositories的末尾

```
allprojects {
    repositories {
    ...
    maven { url 'https://jitpack.io' }
    }
}
```
 **Step 2. 添加 implementation 'com.gitee.lochy:dkcloudid-uart-android-sdk:v1.0.0-beta' 到dependency** 

```

dependencies {
        implementation 'com.gitee.lochy:dkcloudid-uart-android-sdk:v1.0.0-beta'
}
```

 **Step 3. 在AndroidManifest.xml中添加网络访问权限** 
 
 ```

    <uses-permission android:name="android.permission.INTERNET" />
```
 
 
 **Step 4. 初始化串口** 

```

    serialManager = new SerialManager();
```

 **Step 5. 添加读卡回调和读卡代码** 

```

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
						if ((data.length >= 3) && (data[0] == SAM_V_FRAME_START_CODE) && (data[3] == SAM_V_INIT_COM)) {
							//校验数据
							try {
								SamVIdCard.verify(data);
							} catch (CardNoResponseException e) {
								e.printStackTrace();
								serialManager.send(StringTool.hexStringToBytes("AA0118"));
								return;
							}

							initData = Arrays.copyOfRange(data, 4, data.length - 1);
							SamVIdCard samVIdCard = new SamVIdCard(serialManager, initData);

							//关闭上一次的云解码
							idCard = new IDCard(samVIdCard);
							int cnt = 0;
							do {
								try {
									/**
									 * 获取身份证数据
									 * 注意：此方法为同步阻塞方式，需要一定时间才能返回身份证数据，期间身份证不能离开读卡器！
									 */
									IDCardData idCardData = idCard.getIDCardData();

									/**
									 * 显示身份证数据
									 */
									showIDMsg(idCardData);
									//返回读取成功
									return;
								} catch (DKCloudIDException e) {   //服务器返回异常，重复5次解析
									e.printStackTrace();
								} catch (CardNoResponseException e) {    //卡片读取异常，直接退出，需要重新读卡
									e.printStackTrace();
									serialManager.send(StringTool.hexStringToBytes("AA0118"));
									return;
								}
							} while (cnt++ < 5);  //如果服务器返回异常则重复读5次直到成功

						} else if (StringTool.byteHexToSting(data).equals("aa01ea") || StringTool.byteHexToSting(data).equals("AA01EA")) {
							Log.d(TAG, "卡片已经拿开");
						}
					}
				}).start();
			}
		}
	});
```
