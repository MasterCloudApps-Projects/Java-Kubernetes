package com.javieraviles;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "properties.fubar")
@Getter
@Setter
public class PropertiesFubar {
	private String foo;
	private String bar;
}
