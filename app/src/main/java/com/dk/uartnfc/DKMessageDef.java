package com.dk.uartnfc;

public class DKMessageDef {
    public byte start;
    public int len;
    public byte command;
    public byte[] data;
    public byte bcc;

    public int status;
    public int index;

    public int dataLen;

    public DKMessageDef() {
        super();
        start = 0;
        len = 0;
        command = 0;
        data = new byte[30000];
        bcc = 0;
        status = 0;
        index = 0;
        dataLen = 0;
    }

    public void clear() {
        start = 0;
        len = 0;
        command = 0;
        data = new byte[30000];
        bcc = 0;
        status = 0;
        index = 0;
        dataLen = 0;
    }
}
