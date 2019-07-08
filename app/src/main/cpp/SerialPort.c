/*
 * Copyright 2009-2011 Cedric Priscal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <jni.h>

#include "SerialPort.h"

#include "android/log.h"
static const char *TAG="serial_port";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)

static int mTtyfd = -1;

static speed_t getBaudrate(jint baudrate)
{
	switch(baudrate) {
	case 0: return B0;
	case 50: return B50;
	case 75: return B75;
	case 110: return B110;
	case 134: return B134;
	case 150: return B150;
	case 200: return B200;
	case 300: return B300;
	case 600: return B600;
	case 1200: return B1200;
	case 1800: return B1800;
	case 2400: return B2400;
	case 4800: return B4800;
	case 9600: return B9600;
	case 19200: return B19200;
	case 38400: return B38400;
	case 57600: return B57600;
	case 115200: return B115200;
	case 230400: return B230400;
	case 460800: return B460800;
	case 500000: return B500000;
	case 576000: return B576000;
	case 921600: return B921600;
	case 1000000: return B1000000;
	case 1152000: return B1152000;
	case 1500000: return B1500000;
	case 2000000: return B2000000;
	case 2500000: return B2500000;
	case 3000000: return B3000000;
	case 3500000: return B3500000;
	case 4000000: return B4000000;
	default: return -1;
	}
}

/*
 * Class:     android_serialport_SerialPort
 * Method:    open
 * Signature: (Ljava/lang/String;II)Ljava/io/FileDescriptor;
 */
JNIEXPORT jobject JNICALL Java_android_1serialport_1api_SerialPort_open
  (JNIEnv *env, jclass thiz, jstring path, jint baudrate, jint flags)
{
	int fd;
	speed_t speed;
	jobject mFileDescriptor;

	/* Check arguments */
	{
		speed = getBaudrate(baudrate);
		if (speed == -1) {
			/* TODO: throw an exception */
			LOGE("Invalid baudrate");
			return NULL;
		}
	}

	/* Opening device */
	{
		jboolean iscopy;
		const char *path_utf = (*env)->GetStringUTFChars(env, path, &iscopy);
		LOGD("Opening serial port %s with flags 0x%x", path_utf, O_RDWR | flags);
		fd = open(path_utf, O_RDWR | flags);
		mTtyfd = fd;
		LOGD("open() fd = %d", fd);
		(*env)->ReleaseStringUTFChars(env, path, path_utf);
		if (fd == -1)
		{
			/* Throw an exception */
			LOGE("Cannot open port");
			/* TODO: throw an exception */
			return NULL;
		}
	}

	/* Configure device */
	{
		struct termios cfg;
		LOGD("Configuring serial port");
		if (tcgetattr(fd, &cfg))
		{
			LOGE("tcgetattr() failed");
			close(fd);
			/* TODO: throw an exception */
			return NULL;
		}

		cfmakeraw(&cfg);
		cfsetispeed(&cfg, speed);
		cfsetospeed(&cfg, speed);

		//此处设置校验位
		//cfg.c_cflag……

		if (tcsetattr(fd, TCSANOW, &cfg))
		{
			LOGE("tcsetattr() failed");
			close(fd);
			/* TODO: throw an exception */
			return NULL;
		}
	}

	/* Create a corresponding file descriptor */
	{
		jclass cFileDescriptor = (*env)->FindClass(env, "java/io/FileDescriptor");
		jmethodID iFileDescriptor = (*env)->GetMethodID(env, cFileDescriptor, "<init>", "()V");
		jfieldID descriptorID = (*env)->GetFieldID(env, cFileDescriptor, "descriptor", "I");
		mFileDescriptor = (*env)->NewObject(env, cFileDescriptor, iFileDescriptor);
		(*env)->SetIntField(env, mFileDescriptor, descriptorID, (jint)fd);
	}

	return mFileDescriptor;
}

/*
 * Class:     cedric_serial_SerialPort
 * Method:    close
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_android_1serialport_1api_SerialPort_close
  (JNIEnv *env, jobject thiz)
{
	jclass SerialPortClass = (*env)->GetObjectClass(env, thiz);
	jclass FileDescriptorClass = (*env)->FindClass(env, "java/io/FileDescriptor");

	jfieldID mFdID = (*env)->GetFieldID(env, SerialPortClass, "mFd", "Ljava/io/FileDescriptor;");
	jfieldID descriptorID = (*env)->GetFieldID(env, FileDescriptorClass, "descriptor", "I");

	jobject mFd = (*env)->GetObjectField(env, thiz, mFdID);
	jint descriptor = (*env)->GetIntField(env, mFd, descriptorID);

	LOGD("close(fd = %d)", descriptor);
	close(descriptor);
}

///**
// * 写入数据
// * inputData  发送给读卡器的数据
// * len   发送数据的长度
// */
// JNIEXPORT void JNICALL Java_android_1serialport_1api_SerialPort_write(JNIEnv *env, jbyteArray inputData, jint len) {
//     LOGI("serialportWrite");
//     if (mTtyfd == -1) {
//         LOGE("mTtyfd open failure");
//         return -1;
//     }
//     if (len > 1024)
//        return 0;
//     int length;
//     char DataBuff[1024] = { 0x00 };
//     LOGI("lenFileld=%d", len);
//     memcpy(DataBuff, inputData, len);
//     LOGD_HEX(env, "Inputdata=", DataBuff, len);
//     length = write(mTtyfd, DataBuff, len);
//     //sleep(1); //写完之后睡一秒
//     if (length > 0) {
//         LOGI("write device success");
//         return length;
//     }
//     else {
//        LOGE("write device error");
//     }
//     return -1;
// }

/**
 * 读取数据
 * outputData 读卡器返回的数据
 * timeOut  读取超时时间
 */
 JNIEXPORT jint JNICALL Java_android_1serialport_1api_SerialPort_read(JNIEnv *env, jbyteArray outputData, jint timeOut) {
	 LOGI("serialportRead");
	 if (mTtyfd == -1) {
		 LOGE("mTtyfd open failure");
		 return -1;
	 }
	 int ret;
	 fd_set readfd;
	 struct timeval timeout;
	 while (mTtyfd != -1) {
		 timeout.tv_sec = 0; //设定超时秒数
		 timeout.tv_usec = timeOut; //设定超时毫秒数
		 FD_ZERO(&readfd); //清空集合
		 FD_SET(mTtyfd, &readfd); // 把要检测的句柄mTtyfd加入到集合里
		 ret = select(mTtyfd + 1, &readfd, NULL, NULL, &timeout); // 检测我们上面设置到集合readfd里的句柄是否有可读信息
		 LOGI("ret=%d", ret);
		 switch (ret) {
			 case -1: // 这说明select函数出错
				LOGE("mTtyfd read failure");
				return -1;
				break;
			 case 0: // 说明在我们设定的时间值5秒加0毫秒的时间内，mTty的状态没有发生变化
				LOGE("mTtyfd read timeOut");
				return -2;
			 default: //说明等待时间还未到0秒加500毫秒，mTty的状态发生了变化
				if (FD_ISSET(mTtyfd, &readfd)) { // 先判断一下mTty这外被监视的句柄是否真的变成可读的了
					jbyte tempBuff[30000];
					jint nread = 0;
					memset(tempBuff, 0, sizeof(tempBuff));
					if ((nread = read(mTtyfd, tempBuff, 30000)) > 0) {
						LOGI("nread=%d", nread);
						if (nread >= 30000)
							return 0;
						//tempBuff[nread + 1] = '\0';
						(*env)->SetByteArrayRegion(env, outputData, 0, nread, tempBuff);
						char DataBuff[30000] = { 0x00 };
						jbyte* data = (*env)->GetByteArrayElements(env, outputData, JNI_FALSE);
						memcpy(DataBuff, data, nread);
						(*env)->ReleaseByteArrayElements(env, outputData, data, 0);
						LOGI("serialportRead success");
						return nread;
					}
				}
			 break;
		 }
	 }
	 return -1;
 }
