package com.ejemplo.crudpersona;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.ejemplo.crudpersona.repository")
public class RPLCSpringAppConfig {
}