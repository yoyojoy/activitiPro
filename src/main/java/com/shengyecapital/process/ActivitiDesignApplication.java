package com.shengyecapital.process;

import org.activiti.spring.boot.SecurityAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAutoConfiguration(exclude = {
		SecurityAutoConfiguration.class
//		org.activiti.spring.boot.SecurityAutoConfiguration.class
})
@SpringBootApplication
public class ActivitiDesignApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActivitiDesignApplication.class, args);
	}
}
