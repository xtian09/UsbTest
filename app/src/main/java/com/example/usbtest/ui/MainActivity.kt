package com.example.usbtest.ui

import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.usbtest.R
import com.example.usbtest.mcu.Callback
import com.example.usbtest.mcu.aprom.ApRomService
import com.example.usbtest.mcu.ldrom.LdRomService
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 10
        private val PERMISSION_EXTERNAL_STORAGE = arrayOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        private val btnList = arrayListOf(
            "findUsb",
            "startSensor",
            "changeToLd",
            "changeToAp",
            "getArgMcuVersion",
            "getArgLtVersion",
            "set3DMode",
            "get3DMode",
            "setCalibration",
            "getCalibration",
            "setBrightness",
            "getBrightness"
        )
        private const val path = "/Download/ARGlass07.bin"
    }

    private var mSensorManager: SensorManager? = null
    private var mUsbManager: UsbManager? = null
    private var mLdRomService: LdRomService? = null
    private var usbAdapter: UsbDeviceAdapter? = null
    private var btnAdapter: ButtonAdapter? = null
    private var argService: ApRomService? = null
    private var usbInfoList: ArrayList<String>? = null
    private var mServiceConnection: ServiceConnection? = null
    private val usbStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val usbDevice =
                (intent.getParcelableExtra<Parcelable>("device") as UsbDevice)
            when {
                "android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action -> {
                    toast("${usbDevice.productId}  is Attached !!")
                    if (16128 == usbDevice.productId) {
                        argService?.let {
                            mServiceConnection?.let {
                                unbindService(it)
                            }
                            argService = null
                        }
                    } else if (20512 == usbDevice.productId) {
                        mLdRomService?.let {
                            it.dispose()
                            mLdRomService = null
                        }
                    }
                }
                "android.hardware.usb.action.USB_DEVICE_DETACHED" == intent.action -> {
                    toast("${usbDevice.productId} is Detached !!")
                    mServiceConnection?.let {
                        unbindService(it)
                        argService = null
                    }
                    mLdRomService?.dispose()
                    mLdRomService = null
                }
                "com.USB_PERMISSION" == intent.action -> {
                    toast("${usbDevice.productId}  has Permission !!")
                    setUsbInfo(usbDevice)
                }
            }
        }
    }
    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

        }

        override fun onSensorChanged(event: SensorEvent?) {
            Log.d("sensorTest", "count = $count")
            count++
        }
    }
    private var count = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
        intentFilter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED")
        intentFilter.addAction("com.USB_PERMISSION")
        registerReceiver(usbStateReceiver, intentFilter)
        if (verifyPermissions()) {
            init()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init()
            } else {
                toast("No External Storge Permission!!")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbStateReceiver)
        argService?.let {
            mServiceConnection?.let {
                unbindService(it)
            }
            argService = null
        }
        //mSensorManager?.unregisterListener(sensorListener)
        argService = null
        mLdRomService?.let {
            it.dispose()
            mLdRomService = null
        }
    }

    private fun verifyPermissions(): Boolean {
        val grantResult =
            ActivityCompat.checkSelfPermission(this, "android.permission.WRITE_EXTERNAL_STORAGE")
        return if (grantResult != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                PERMISSION_EXTERNAL_STORAGE,
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }

    private fun init() {
        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = mSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        //mSensorManager?.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(
                componentName: ComponentName,
                iBinder: IBinder
            ) {
                argService = (iBinder as ApRomService.ArgBinder).service
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                argService = null
            }
        }
        usbAdapter = UsbDeviceAdapter()
        rv_usb.adapter = usbAdapter
        btnAdapter = ButtonAdapter()
        btnAdapter?.let {
            it.setOnItemClickListener { _, _, position ->
                when (position) {
                    0 -> initUsbInfo()
                    1 -> argService?.startSensor()
                    2 -> changeToLdRom()
                    3 -> changeToApRom()
                    4 -> toast(argService?.argMcuVersion)
                    5 -> toast(argService?.argLtVersion)
                    6 -> argService?.set3DMode(true)
                    7 -> toast("3D mode = " + argService?.get3DMode())
                    8 -> argService?.calibration = intArrayOf(1, 2, 3, 4)
                    9 -> toast("calibration = " + argService?.calibration)
                    10 -> argService?.brightness = 4
                    11 -> toast("brightness = " + argService?.brightness)
                    else -> toast("unknown!")
                }
            }
            rv_btn.adapter = it
            it.setNewData(btnList)
        }
    }

    private fun initUsbInfo() {
        mUsbManager?.let {
            if (it.deviceList.isNullOrEmpty()) {
                toast("未找到设备")
            } else {
                usbInfoList = ArrayList()
                for (usbDevice in it.deviceList.values) {
                    if (16128 == usbDevice.productId) {
                        argService?.let {
                            mServiceConnection?.let { ap ->
                                unbindService(ap)
                            }
                            argService = null
                        }
                        val file = Environment.getExternalStoragePublicDirectory(path)
                        if (file.exists()) {
                            mLdRomService = LdRomService(
                                this@MainActivity,
                                file.toString()
                            )
                        }
                    } else if (20512 == usbDevice.productId) {
                        mLdRomService?.let { ld ->
                            ld.dispose()
                            mLdRomService = null
                        }
                        bindService(
                            Intent(this@MainActivity, ApRomService::class.java),
                            mServiceConnection!!,
                            Context.BIND_AUTO_CREATE
                        )
                    }
                    usbInfoList?.let { list ->
                        list.add("设备类别" + usbDevice.deviceClass)
                        list.add("设备id" + usbDevice.deviceId)
                        list.add("设备名称" + usbDevice.deviceName)
                        list.add("协议类别" + usbDevice.deviceProtocol)
                        list.add("设备子类别" + usbDevice.deviceSubclass)
                        list.add("生产商ID" + usbDevice.vendorId)
                        list.add("产品ID" + usbDevice.productId)
                        list.add("接口数量" + usbDevice.interfaceCount)
                    }
                    mUsbManager?.openDevice(usbDevice)
                    if (it.hasPermission(usbDevice)) {
                        setUsbInfo(usbDevice)
                    } else {
                        it.requestPermission(
                            usbDevice,
                            PendingIntent.getBroadcast(this, 0, Intent("com..USB_PERMISSION"), 0)
                        )
                    }
                }
            }
        } ?: toast("手机不支持OTG")
    }

    private fun setUsbInfo(usbDevice: UsbDevice) {
        val mUsbInterface = usbDevice.getInterface(0)
        val inEndPoint = mUsbInterface.getEndpoint(0)
        val outEndPoint = mUsbInterface.getEndpoint(1)
        usbInfoList?.let {
            it.add("in节点地址" + inEndPoint?.address)
            it.add("in节点属性" + inEndPoint?.attributes)
            it.add("in节点传输方向" + inEndPoint?.direction)
            it.add("in节点数据长度" + inEndPoint?.maxPacketSize)
            it.add("out节点地址" + outEndPoint?.address)
            it.add("out节点属性" + outEndPoint?.attributes)
            it.add("out节点传输方向" + outEndPoint?.direction)
            it.add("out节点数据长度" + outEndPoint?.maxPacketSize)
            usbAdapter!!.setNewData(it)
        }
    }

    private fun changeToLdRom() {
        argService?.changeToLdRom(object : Callback {
            override fun onFailure(error: String?) {
                Log.d("MCU_MAIN", "changeRom error $error")
                if ("response fail !!" == error) {
                    Thread.sleep(1000)
                    initUsbInfo()
                }
            }

            override fun onResponse(response: ByteArray?) {

            }
        })
    }

    private fun changeToApRom() {
        Runnable {
            mLdRomService?.start()
        }.run()
        Thread.sleep(1000)
        initUsbInfo()
    }
}
