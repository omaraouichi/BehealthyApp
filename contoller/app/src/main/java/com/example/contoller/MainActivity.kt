package com.example.contoller

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var writer: PrintWriter? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val moveBtn = findViewById<Button>(R.id.moveBtn)
        val clickBtn = findViewById<Button>(R.id.clickBtn)
        val ipInput = findViewById<EditText>(R.id.ipInput)
        var sensitivity = 2f
        var lastClickTime = 0L
        val DOUBLE_CLICK_TIME = 300L
        val keyboardInput = findViewById<EditText>(R.id.keyboardInput)
        val sendTextBtn = findViewById<Button>(R.id.sendTextBtn)
        val enterBtn = findViewById<Button>(R.id.enterBtn)
        val backspaceBtn = findViewById<Button>(R.id.backspaceBtn)

        sendTextBtn.setOnClickListener {
            val text = keyboardInput.text.toString()
            if (text.isNotEmpty()) {
                sendCommand("TYPE:$text")
                keyboardInput.text.clear()
            }
        }

        enterBtn.setOnClickListener {
            sendCommand("KEY:ENTER")
        }

        backspaceBtn.setOnClickListener {
            sendCommand("KEY:BACKSPACE")
        }
        moveBtn.setOnClickListener {
          sensitivity+=1f
        }

        var lastX = 0f
        var lastY = 0f
        val touchPad = findViewById<View>(R.id.touchPad)
        touchPad.setOnTouchListener { _, event ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y

                    val clickTime = System.currentTimeMillis()
                    if (clickTime - lastClickTime < DOUBLE_CLICK_TIME) {
                        sendCommand("DOUBLE_CLICK")
                    }
                    lastClickTime = clickTime
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = ((event.x - lastX) * sensitivity).toInt()
                    val dy = ((event.y - lastY) * sensitivity).toInt()

                    if (dx != 0 || dy != 0) {
                        sendCommand("MOVE:$dx:$dy")
                    }

                    lastX = event.x
                    lastY = event.y
                }
            }

            true
        }


        clickBtn.setOnClickListener {
            sendCommand("CLICK")
        }



        val connectButton = findViewById<Button>(R.id.connectButton)

        connectButton.setOnClickListener {
            val ip = ipInput.text.toString()

            thread {
                try {
                    val socket = Socket(ip, 5000)
                    writer = PrintWriter(socket.getOutputStream(), true)

                    writer?.println("CONNECTED")

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun sendCommand(cmd: String) {
        thread {
            writer?.println(cmd)
        }
    }
}
