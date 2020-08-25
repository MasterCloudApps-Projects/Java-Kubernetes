package com.javieraviles.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javieraviles.PropertiesFubar;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fubar")
public class PropertiesResource {

	@Autowired
	PropertiesFubar fubar;

    @GetMapping("/")
    public ResponseEntity<ResponseData> getData() {
        ResponseData responseData = new ResponseData();
        responseData.setFoo(fubar.getFoo());
        responseData.setBar(fubar.getBar());
        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

    @Getter
    @Setter
    public class ResponseData {
        private String foo;
        private String bar;
    }
}
