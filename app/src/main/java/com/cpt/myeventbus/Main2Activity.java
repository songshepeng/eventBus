package com.cpt.myeventbus;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.cpt.eventbus.EventBus;
import com.cpt.eventbus_annotation.annotation.Subscribe;
import com.cpt.eventbus_annotation.mode.ThreadMode;
import com.cpt.myeventbus.mode.UserInfo;

public class Main2Activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
    }

    public void post(View view) {
        EventBus.getDefault().post(new UserInfo("萱萱"));
        finish();
    }

    public void sticky(View view) {
        EventBus.getDefault().register(this);
        EventBus.getDefault().removeStickyEvent(UserInfo.class);
    }

    @Subscribe(threadMode = ThreadMode.MAIN ,sticky = true)
    public void  abc(UserInfo userInfo){
        Log.e("粘性事件接收", "abc: "+userInfo.toString() );
    }
}
