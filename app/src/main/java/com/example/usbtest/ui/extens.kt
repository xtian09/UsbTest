package com.example.usbtest.ui

import android.app.Activity
import android.widget.Toast

fun Activity.toast(string: String?) {
    string?.let {
        Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
    }
}