package com.example.usbtest

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder

class UsbDeviceAdapter : BaseQuickAdapter<String, BaseViewHolder>(R.layout.item_usb) {
    override fun convert(helper: BaseViewHolder, item: String?) {
        helper.setText(R.id.tv_compat, item)
    }
}