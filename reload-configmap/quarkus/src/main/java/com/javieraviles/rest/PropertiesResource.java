package com.javieraviles.rest;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.javieraviles.Properties;
import javax.inject.Inject;

@Path("/fubar")
public class PropertiesResource {

    @Inject
    public Properties properties;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getFubar() {
        return "foo: " + properties.fubar.foo + " and bar: " + properties.fubar.bar;
    }

}