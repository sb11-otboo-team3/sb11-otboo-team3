package com.otboo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class OtbooApplication {
	public static void main(String[] args) {
		SpringApplication.run(OtbooApplication.class, args);
	}
}