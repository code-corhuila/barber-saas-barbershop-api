package co.edu.corhuila.barbersaas.barbershop.domain.model;

/** Same values as chk_barbershop_status. Transitions belong to platform-admin (DEC-SHOP-03). */
public enum BarbershopStatus {
    TRIAL, ACTIVE, SUSPENDED, CANCELLED;

    /** A barbershop on trial must be able to receive clients; a suspended or cancelled one is hidden. */
    public boolean visible() {
        return this == TRIAL || this == ACTIVE;
    }
}
