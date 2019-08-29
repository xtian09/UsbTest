package com.example.usbtest;

import java.io.IOException;
import java.io.RandomAccessFile;

public class FileSpliter {

    private RandomAccessFile mRandomAF;
    private int splitLength;

    public FileSpliter(String fileName, int splitLength) throws IOException {
        this.splitLength = splitLength;
        mRandomAF = new RandomAccessFile(fileName, "r");
    }

    public synchronized Files getContent(long nStart) {
        Files sFile = new Files();
        byte[] fileByte = new byte[splitLength];
        try {
            mRandomAF.seek(nStart);
            sFile.length = mRandomAF.read(fileByte);
            sFile.bytes = new byte[splitLength + 3];
            System.arraycopy(fileByte, 0, sFile.bytes, 3, splitLength + 3);
            sFile.bytes[0] = 2;
            sFile.bytes[1] = 2;
            sFile.bytes[2] = (byte) sFile.length;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sFile;
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

    public long getFileLength() {
        long length = 0L;
        try {
            length = mRandomAF.length();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return length;
    }

    public class Files {
        public byte[] bytes;
        public int length;
    }

}
