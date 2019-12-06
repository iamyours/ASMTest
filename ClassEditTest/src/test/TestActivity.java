package test;

import android.util.Log;
import annotations.MethodLog;

public class TestActivity {

    @MethodLog
    public void test() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void test2() {
       Log.i("test","test123");
    }
}
