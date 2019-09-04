package com.example.usbtest.ui

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.example.usbtest.R

class UsbDeviceAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_usb) {
    override fun convert(helper: BaseViewHolder, item: String?) {
        helper.setText(R.id.tv_compat, item)
    }
}