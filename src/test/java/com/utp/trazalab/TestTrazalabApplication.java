package com.utp.trazalab;

import org.springframework.boot.SpringApplication;

public class TestTrazalabApplication {

	public static void main(String[] args) {
		SpringApplication.from(TrazalabApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
