package com.example.usbtest.mcu;

import com.example.usbtest.FileSpliter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class McuCmd {

    //Within the range 0~MAX_APROM_SIZE, program APROM from address user specified
    public static final int CMD_UPDATE_APROM = 0x000000A0;

    //Write Config0 and Config1 registers of Flash memory
    public static final int CMD_UPDATE_CONFIG = 0x000000A1;

    //Get Config0 and Config1
    public static final int CMD_READ_CONFIG = 0x000000A2;

    //Erase all APROM, including Data Flash in Flash memory and Config area. The Config registers to restored to default value
    public static final int CMD_ERASE_ALL = 0x000000A3;

    //Synchronize packet number with NuMicro MCU microcontrollers before a valid command send
    public static final int CMD_SYNC_PACKNO = 0x000000A4;

    //Get version information of ISP firmware
    public static final int CMD_GET_FWVER = 0x000000A6;

    //Get chip product ID
    public static final int CMD_GET_DEVICEID = 0x000000B1;

    //Program APROM from address user specified, before program, erase the corresponding sector
    public static final int CMD_UPDATE_DATAFLASH = 0x000000C3;

    //Instruct ISP to boot from APROM
    public static final int CMD_RUN_APROM = 0x000000AB;

    //Instruct ISP to boot from LDROM
    public static final int CMD_RUN_LDROM = 0x000000AC;

    //Instruct ISP to reboot
    public static final int CMD_RESET = 0x000000AD;

    //Instruct ISP to write application length and checksum in APROM to the last 8 bytes of APROM
    public static final int CMD_WRITE_CHECKSUM = 0x000000C9;

    //Get boot selection
    public static final int CMD_GET_FLASHMODE = 0x000000CA;

    public static final int CMD_RESEND_PACKET = 0x000000FF;

    //Test whether or not the ISP is active
    public static final int CMD_CONNECT = 0x000000AE;

    public static byte[] getSyncByte() {
        return concatByte(IntToByte(McuCmd.CMD_SYNC_PACKNO), IntToByte(1), IntToByte(1));
    }

    public static byte[] getModeByte() {
        return concatByte(IntToByte(McuCmd.CMD_GET_FLASHMODE), IntToByte(3));
    }

    public static List<Integer> getReturnCode(byte[] data, int count) {
        List<Integer> result = new ArrayList<>();
        int size = data.length;
        if (size > 0 && count > 0) {
            int segments = size / count;
            segments = size % count == 0 ? segments : segments + 1;
            int reCode = 0;
            for (int i = 0; i < segments; i++) {
                if (i == segments - 1) {
                    reCode = ByteToInt(Arrays.copyOfRange(data, count * i, size));
                } else {
                    reCode = ByteToInt(Arrays.copyOfRange(data, count * i, count * (i + 1)));
                }
                result.add(reCode);
            }
        }
        return result;
    }

    public static int ByteToInt(byte[] bytes) {
        return (bytes[3] & 0xff) << 24
                | (bytes[2] & 0xff) << 16
                | (bytes[1] & 0xff) << 8
                | (bytes[0] & 0xff);
    }

    public static byte[] IntToByte(int num) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte) ((num >> 24) & 0xff);
        bytes[2] = (byte) ((num >> 16) & 0xff);
        bytes[1] = (byte) ((num >> 8) & 0xff);
        bytes[0] = (byte) (num & 0xff);
        return bytes;
    }

    public static byte[] concatByte(byte[] first, byte[]... rest) {
        int totalLength = first.length;
        for (byte[] array : rest) {
            totalLength += array.length;
        }
        byte[] result = Arrays.copyOf(first, totalLength);
        int offset = first.length;
        for (byte[] array : rest) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

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
