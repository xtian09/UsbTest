package com.example.usbtest;

public class TestRun {

    public void cutFileTransfer(String filePath) {
        FileSpliter fileSpliter = null;
        try {
            fileSpliter = new FileSpliter(filePath, 61);
            Long mStartPos = 0L;
            Long length = fileSpliter.getFileLength();
            int mBufferSize = 64;
            byte[] buffer = new byte[mBufferSize];
            FileSpliter.Files files;
            long nRead;
            while (mStartPos < length) {
                files = fileSpliter.getContent(mStartPos);
                nRead = files.length;
                buffer = files.bytes;
                //in
                //int retOut = argUsbDeviceConnection.bulkTransfer(argEpIn, buffer, 64, 100);
                //out
                mStartPos += nRead;
            }
        } catch (Exception e) {

        } finally {
            if (fileSpliter != null) {
                fileSpliter.close();
            }
        }
    }
}
