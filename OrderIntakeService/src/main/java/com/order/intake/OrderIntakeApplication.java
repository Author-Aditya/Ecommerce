package com.order.intake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.order.intake.client")
@ComponentScan(basePackages = "com.order.intake")
public class OrderIntakeApplication {

	public static void main(String[] args) {
		SpringApplication.run(OrderIntakeApplication.class, args);
	}

}
