package com.javieraviles;

import io.quarkus.arc.config.ConfigProperties;

@ConfigProperties(prefix = "properties") 
public class Properties {

    public Fubar fubar;

    public static class Fubar {
        public String foo;
        public String bar;
    }
}