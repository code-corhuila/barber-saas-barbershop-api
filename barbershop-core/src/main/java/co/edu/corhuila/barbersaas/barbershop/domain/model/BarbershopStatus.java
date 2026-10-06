package co.edu.corhuila.barbersaas.barbershop.domain.model;

/**
 * Same values as chk_barbershop_status. Only platform-admin asks for a change (DEC-SHOP-03, DEC-SHOP-06);
 * the allowed transitions are those of entities-and-rules.md, "Entity: Barbershop".
 */
public enum BarbershopStatus {
    TRIAL, ACTIVE, SUSPENDED, CANCELLED;

    /** TRIAL → ACTIVE/SUSPENDED, ACTIVE → SUSPENDED/CANCELLED, SUSPENDED → ACTIVE/CANCELLED; CANCELLED is final. */
    public boolean canBecome(BarbershopStatus target) {
        return switch (this) {
            case TRIAL -> target == ACTIVE || target == SUSPENDED;
            case ACTIVE -> target == SUSPENDED || target == CANCELLED;
            case SUSPENDED -> target == ACTIVE || target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    /** A barbershop on trial must be able to receive clients; a suspended or cancelled one is hidden. */
    public boolean visible() {
        return this == TRIAL || this == ACTIVE;
    }
}
