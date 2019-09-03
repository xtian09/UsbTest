package com.example.usbtest.mcu;

import java.io.IOException;

interface Callback {

    void onFailure(byte[] request, IOException e);

    void onResponse(byte[] request, byte[] response);
}