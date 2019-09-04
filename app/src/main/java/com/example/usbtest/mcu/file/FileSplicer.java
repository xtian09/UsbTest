package com.example.usbtest.mcu.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

class FileSplicer {

    private RandomAccessFile mRandomAF;

    FileSplicer(String fileName) throws FileNotFoundException {
        mRandomAF = new RandomAccessFile(fileName, "r");
    }

    synchronized byte[] getData(long start, int length) {
        byte[] data = new byte[length];
        try {
            mRandomAF.seek(start);
            mRandomAF.read(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    void close() {
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

    int getFileLength() {
        int length = 0;
        try {
            length = (int) mRandomAF.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return length;
    }

}
