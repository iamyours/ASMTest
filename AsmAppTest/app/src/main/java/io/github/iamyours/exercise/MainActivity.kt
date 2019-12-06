package io.github.iamyours.exercise

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import io.github.iamyours.annotations.MethodLog
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tv.setOnClickListener {
            test()
        }
    }

    @MethodLog
    fun test(){
        Thread.sleep(50)
        Log.i("test","")
    }
}
