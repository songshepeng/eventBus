package com.cpt.myeventbus.mode;

public class UserInfo {
    private String name ;

    public String getName() {
        return name;
    }

    public UserInfo(String name) {
        this.name = name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "UserInfo{" +
                "name='" + name + '\'' +
                '}';
    }
}
