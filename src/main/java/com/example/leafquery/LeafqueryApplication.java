package com.example.leafquery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.example.leafquery.mapper")
public class LeafqueryApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeafqueryApplication.class, args);
	}

}
