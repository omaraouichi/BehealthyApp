package com.example.controll

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private var lastX = 0f
    private var lastY = 0f
    private var sensitivity = 1f

    // ================= PERMISSION CHECK =================
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                100
            )
        }
    }

    // ================= HID DESCRIPTOR =================
    private val hidDescriptor = byteArrayOf(
        // MOUSE
        0x05, 0x01,
        0x09, 0x02,
        0xA1.toByte(), 0x01,
        0x85.toByte(), 0x01,
        0x09, 0x01,
        0xA1.toByte(), 0x00,
        0x05, 0x09,
        0x19, 0x01,
        0x29, 0x03,
        0x15, 0x00,
        0x25, 0x01,
        0x95.toByte(), 0x03,
        0x75, 0x01,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x01,
        0x75, 0x05,
        0x81.toByte(), 0x03,
        0x05, 0x01,
        0x09, 0x30,
        0x09, 0x31,
        0x15, 0x81.toByte(),
        0x25, 0x7F,
        0x75, 0x08,
        0x95.toByte(), 0x02,
        0x81.toByte(), 0x06,
        0xC0.toByte(),
        0xC0.toByte(),

        // KEYBOARD
        0x05, 0x01,
        0x09, 0x06,
        0xA1.toByte(), 0x01,
        0x85.toByte(), 0x02,
        0x05, 0x07,
        0x19, 0xE0.toByte(),
        0x29, 0xE7.toByte(),
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95.toByte(), 0x08,
        0x81.toByte(), 0x02,
        0x95.toByte(), 0x01,
        0x75, 0x08,
        0x81.toByte(), 0x03,
        0x95.toByte(), 0x06,
        0x75, 0x08,
        0x15, 0x00,
        0x25, 0x65,
        0x05, 0x07,
        0x19, 0x00,
        0x29, 0x65,
        0x81.toByte(), 0x00,
        0xC0.toByte()
    )

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
        } else {
            initBluetooth()
        }

        setupTouchpad()
        setupButtons()
        setupKeyboard()
    }

    // ================= INIT BLUETOOTH =================
    private fun initBluetooth() {

        if (!hasBluetoothPermission()) return

        val manager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Bluetooth Ready ✅", Toast.LENGTH_SHORT).show()

        bluetoothAdapter.getProfileProxy(
            this,
            object : BluetoothProfile.ServiceListener {

                @RequiresApi(Build.VERSION_CODES.P)
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {

                        bluetoothHidDevice = proxy as BluetoothHidDevice

                        val sdp = BluetoothHidDeviceAppSdpSettings(
                            "AirControl HID",
                            "Mouse & Keyboard",
                            "Omar",
                            BluetoothHidDevice.SUBCLASS1_COMBO,
                            hidDescriptor
                        )

                        try {
                            bluetoothHidDevice?.registerApp(
                                sdp,
                                null,
                                null,
                                Executors.newSingleThreadExecutor(),
                                hidCallback
                            )
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                    }
                }

                override fun onServiceDisconnected(profile: Int) {}
            },
            BluetoothProfile.HID_DEVICE
        )
    }

    private val hidCallback = @RequiresApi(Build.VERSION_CODES.P)
    object : BluetoothHidDevice.Callback() {

        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "HID Registered: $registered",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Connected to PC ✅",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ================= TOUCHPAD =================
    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchpad() {
        val touchPad = findViewById<View>(R.id.touchPad)

        touchPad.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((event.x - lastX) * sensitivity).toInt()
                    val dy = ((event.y - lastY) * sensitivity).toInt()
                    sendMouseMove(dx, dy)
                    lastX = event.x
                    lastY = event.y
                }
            }
            true
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupButtons() {
        findViewById<Button>(R.id.leftClickBtn).setOnClickListener {
            sendClick(0x01)
        }
        findViewById<Button>(R.id.rightClickBtn).setOnClickListener {
            sendClick(0x02)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendMouseMove(dx: Int, dy: Int) {
        if (!hasBluetoothPermission()) return
        val device = connectedDevice ?: return

        val report = byteArrayOf(
            0x00,
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte()
        )

        try {
            bluetoothHidDevice?.sendReport(device, 1, report)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendClick(mask: Int) {
        if (!hasBluetoothPermission()) return
        val device = connectedDevice ?: return

        val press = byteArrayOf(mask.toByte(), 0x00, 0x00)
        val release = byteArrayOf(0x00, 0x00, 0x00)

        try {
            bluetoothHidDevice?.sendReport(device, 1, press)
            bluetoothHidDevice?.sendReport(device, 1, release)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setupKeyboard() {
        val input = findViewById<EditText>(R.id.keyboardInput)

        input.addTextChangedListener(object : android.text.TextWatcher {

            private var previousText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                if (s == null) return

                val currentText = s.toString()

                // Detect backspace
                if (currentText.length < previousText.length) {
                    sendSpecialKey(0x2A) // Backspace
                }

                // Detect new character
                if (currentText.length > previousText.length) {
                    val newChar = currentText.last()
                    sendSingleKey(newChar)
                }

                previousText = currentText
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Detect ENTER key
        input.setOnEditorActionListener { _, _, _ ->
            sendSpecialKey(0x28) // Enter
            true
        }
    }
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendSpecialKey(keyCode: Int) {

        if (!hasBluetoothPermission()) return
        val device = connectedDevice ?: return

        val press = byteArrayOf(
            0x00, 0x00,
            keyCode.toByte(),
            0x00, 0x00, 0x00, 0x00, 0x00
        )

        val release = ByteArray(8)

        try {
            bluetoothHidDevice?.sendReport(device, 2, press)
            bluetoothHidDevice?.sendReport(device, 2, release)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendSingleKey(c: Char) {

        if (!hasBluetoothPermission()) return
        val device = connectedDevice ?: return

        val key = asciiToKeyCode(c) ?: return

        val press = byteArrayOf(
            0x00, 0x00,
            key,
            0x00, 0x00, 0x00, 0x00, 0x00
        )

        val release = ByteArray(8)

        try {
            bluetoothHidDevice?.sendReport(device, 2, press)
            bluetoothHidDevice?.sendReport(device, 2, release)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendKeyboardText(text: String) {
        if (!hasBluetoothPermission()) return
        val device = connectedDevice ?: return

        for (c in text) {
            val key = asciiToKeyCode(c) ?: continue

            val press = byteArrayOf(
                0x00, 0x00,
                key,
                0x00, 0x00, 0x00, 0x00, 0x00
            )

            val release = ByteArray(8)

            try {
                bluetoothHidDevice?.sendReport(device, 2, press)
                bluetoothHidDevice?.sendReport(device, 2, release)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            Thread.sleep(20)
        }
    }

    private fun asciiToKeyCode(c: Char): Byte? {
        return when (c.lowercaseChar()) {

            // AZERTY physical mapping
            'a' -> 0x14  // physical Q key
            'z' -> 0x1A
            'e' -> 0x08
            'r' -> 0x15
            't' -> 0x17
            'y' -> 0x1C
            'u' -> 0x18
            'i' -> 0x0C
            'o' -> 0x12
            'p' -> 0x13

            'q' -> 0x04  // physical A key
            's' -> 0x16
            'd' -> 0x07
            'f' -> 0x09
            'g' -> 0x0A
            'h' -> 0x0B
            'j' -> 0x0D
            'k' -> 0x0E
            'l' -> 0x0F
            'm' -> 0x33

            'w' -> 0x1D
            'x' -> 0x1B
            'c' -> 0x06
            'v' -> 0x19
            'b' -> 0x05
            'n' -> 0x11
            // Numbers
            '1' -> 0x1E
            '2' -> 0x1F
            '3' -> 0x20
            '4' -> 0x21
            '5' -> 0x22
            '6' -> 0x23
            '7' -> 0x24
            '8' -> 0x25
            '9' -> 0x26
            '0' -> 0x27
            ' ' -> 0x2C

            else -> null
        }?.toByte()
    }

}
