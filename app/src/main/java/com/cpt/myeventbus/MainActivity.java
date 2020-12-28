package com.cpt.myeventbus;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.arouter.apt.EventBusIndex;
import com.cpt.eventbus.EventBus;
import com.cpt.eventbus_annotation.annotation.Subscribe;
import com.cpt.eventbus_annotation.mode.EventBeans;
import com.cpt.eventbus_annotation.mode.SubscriberInfo;
import com.cpt.eventbus_annotation.mode.SubscriberMethod;
import com.cpt.myeventbus.mode.UserInfo;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().addIndex(new EventBusIndex());
        EventBus.getDefault().register(this);
        setContentView(R.layout.activity_main);

    }

    @Subscribe
    public void onEvent(UserInfo userInfo) {
        Log.e("test>>>>", "onEvent: " + userInfo.toString());
    }

    @Subscribe
    public void onEvent(String userInfo) {
        Log.e("test>>>>", "onEvent: " + userInfo);
    }

    public void test(View view) {
        EventBus.getDefault().postSticky(new UserInfo("张三"));
        Log.e("test>>>>", "onEvent:  发送粘性事件");
    }

    public void jump(View view) {
        startActivity(new Intent(this, Main2Activity.class));
    }
}
