package co.edu.corhuila.barbersaas.barbershop.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "co.edu.corhuila.barbersaas.barbershop")
public class BarbershopApplication {
    public static void main(String[] args) {
        SpringApplication.run(BarbershopApplication.class, args);
    }
}
