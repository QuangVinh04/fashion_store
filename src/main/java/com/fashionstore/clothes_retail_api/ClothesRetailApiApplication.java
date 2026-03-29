package com.fashionstore.clothes_retail_api;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ClothesRetailApiApplication {

	

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

		SpringApplication.run(ClothesRetailApiApplication.class, args);
	}

}
