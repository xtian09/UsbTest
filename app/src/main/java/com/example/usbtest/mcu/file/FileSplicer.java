package com.example.usbtest.mcu.file;

import java.io.IOException;
import java.io.RandomAccessFile;

public class FileSplicer {

    private RandomAccessFile mRandomAF;

    public FileSplicer(String fileName) throws IOException {
        mRandomAF = new RandomAccessFile(fileName, "r");
    }

    public synchronized byte[] getData(long start, int length) {
        byte[] data = new byte[length];
        try {
            mRandomAF.seek(start);
            mRandomAF.read(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    public void close() {
        if (mRandomAF != null) {
            try {
                mRandomAF.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mRandomAF = null;
            }
        }
    }

    public int getFileLength() {
        int length = 0;
        try {
            length = (int) mRandomAF.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return length;
    }

}
