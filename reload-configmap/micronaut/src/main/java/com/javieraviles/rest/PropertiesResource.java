package com.javieraviles.rest;

import javax.inject.Inject;

import com.javieraviles.PropertiesFubar;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/fubar")
public class PropertiesResource {

    @Inject
    PropertiesFubar fubar;

	@Get(uri = "/", produces = MediaType.TEXT_PLAIN) 
    HttpResponse<String> getFubar() { 
        return HttpResponse.ok("foo: " + fubar.getFoo() + " bar: " + fubar.getBar());
    }

}