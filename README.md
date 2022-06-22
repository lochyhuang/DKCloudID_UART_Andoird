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
 **Step 2. 添加 implementation 'com.gitee.lochy:dkcloudid-uart-android-sdk:v2.0.0' 到dependency** 

```

dependencies {
        implementation 'com.gitee.lochy:dkcloudid-uart-android-sdk:v1.1.0'
}
```

 **Step 3. 在AndroidManifest.xml中添加网络访问权限** 
 
 ```

    <uses-permission android:name="android.permission.INTERNET" />
```
 
 
 **Step 4. 初始化设备并打开串口** 

```

	uartNfcDevice = new UartNfcDevice();
	uartNfcDevice.setCallBack(deviceManagerCallback);
	new Thread(new Runnable() {
		@Override
		public void run() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			uartNfcDevice.serialManager.open("/dev/ttyUSB0", "115200");
		}
	}).start();
```

 **Step 5. 添加读卡回调和读卡代码** 

```

    //设备操作类回调
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback() {
        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            System.out.println("Activity接收到激活卡片回调：UID->" + StringTool.byteHexToSting(bytCardSn) + " ATS->" + StringTool.byteHexToSting(bytCarATS));

            final int cardTypeTemp = cardType;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    //readWriteCardDemo(cardTypeTemp);  //普通卡读写API示例
                }
            }).start();
        }

        @Override
        public void onReceiveSamVIdInit(byte[] initData) {
            super.onReceiveSamVIdInit(initData);

            final byte[] initDataTmp = initData;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    dkcloudidDemo(initDataTmp);
                }
            }).start();
        }

        @Override
        public void onReceiveCardLeave() {
            super.onReceiveCardLeave();
            Log.d(TAG, "卡片已离开");
        }
    };
	
    //云解码Demo
    private synchronized boolean dkcloudidDemo(byte[] initData) {
        final Iso14443BIdCard card = (Iso14443BIdCard) uartNfcDevice.getCard();
        if (card == null) {
            Log.e(TAG, "未找到身份证");
            return false;
        }

        Log.d(TAG, "开始解析");

        idCard = new IDCard();
		
        try {
            /**
             * 获取身份证数据，带进度回调，如果不需要进度回调可以去掉进度回调参数或者传入null
             * 注意：此方法为同步阻塞方式，需要一定时间才能返回身份证数据，期间身份证不能离开读卡器！
             */
            IDCardData idCardData = idCard.getIDCardData(card, 5);

            /**
             * 显示身份证数据
             */
            showIDMsg(idCardData);
            //返回读取成功
            return true;
        } catch (DKCloudIDException e) {
            e.printStackTrace();
        } catch (CardNoResponseException e) {    //卡片读取异常，直接退出，需要重新读卡
            e.printStackTrace();

            //返回读取失败
            try {
                card.close();
            } catch (CardNoResponseException cardNoResponseException) {
                cardNoResponseException.printStackTrace();
            }
        }

        return false;
    }
```
