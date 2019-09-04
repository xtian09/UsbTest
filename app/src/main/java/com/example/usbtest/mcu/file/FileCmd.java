package com.example.usbtest.mcu.file;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class FileCmd {

    //Within the range 0~MAX_APROM_SIZE, program APROM from address user specified
    static final int CMD_UPDATE_APROM = 0x000000A0;

    //Synchronize packet number with NuMicro MCU microcontrollers before a valid command send
    static final int CMD_SYNC_PACKNO = 0x000000A4;

    //Instruct ISP to boot from APROM
    static final int CMD_RUN_APROM = 0x000000AB;

    //Get boot selection
    static final int CMD_GET_FLASHMODE = 0x000000CA;

    static List<Integer> getReturnCode(byte[] data, int count) {
        List<Integer> result = new ArrayList<>();
        int size = data.length;
        if (size > 0 && count > 0) {
            int segments = size / count;
            segments = size % count == 0 ? segments : segments + 1;
            int reCode;
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

    private static int ByteToInt(byte[] bytes) {
        return (bytes[3] & 0xff) << 24
                | (bytes[2] & 0xff) << 16
                | (bytes[1] & 0xff) << 8
                | (bytes[0] & 0xff);
    }

    static byte[] IntToByte(int num) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte) ((num >> 24) & 0xff);
        bytes[2] = (byte) ((num >> 16) & 0xff);
        bytes[1] = (byte) ((num >> 8) & 0xff);
        bytes[0] = (byte) (num & 0xff);
        return bytes;
    }

    static byte[] concatByte(byte[] first, byte[]... rest) {
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
}
