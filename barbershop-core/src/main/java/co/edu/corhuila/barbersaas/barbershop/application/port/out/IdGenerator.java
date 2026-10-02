package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import java.util.UUID;

public interface IdGenerator {

    UUID next();
}
