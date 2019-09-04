package com.example.usbtest.mcu;

public interface Callback {

    void onFailure(String error);

    void onResponse(byte[] response);
}