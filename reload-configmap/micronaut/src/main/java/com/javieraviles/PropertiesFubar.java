package com.javieraviles;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("properties.fubar")
public class PropertiesFubar {
    private String foo;
    private String bar;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }

    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
}