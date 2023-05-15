package com.example.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        //Simulate a workload
        new Thread(new MatrixMultiply()).start();

        SpringApplication.run(Application.class, args);
    }
}