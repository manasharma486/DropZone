package com.madn.dropzone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DropzoneApplication {

	public static void main(String[] args) {
		SpringApplication.run(DropzoneApplication.class, args);
	}

}


