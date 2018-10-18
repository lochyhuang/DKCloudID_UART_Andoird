package com.dk.uartnfc;

/**
 * Created by Administrator on 2017/5/15.
 */

public class DeviceNoResponseException extends Exception {
	private static final long serialVersionUID = 1L;

    public static final String DEVICE_NO_RESPONSE = "设备无响应！";

	public DeviceNoResponseException(){
    }

    //设备超时无响应异常
    public DeviceNoResponseException(String detailMessage){
        super(detailMessage);
    }
}
