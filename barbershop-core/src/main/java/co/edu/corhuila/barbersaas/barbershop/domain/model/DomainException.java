package co.edu.corhuila.barbersaas.barbershop.domain.model;

/** Errors the domain raises. The HTTP adapter turns each into one status code. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /** An input that breaks an invariant: 422 BUSINESS_RULE_VIOLATION. */
    public static class BusinessRuleViolation extends DomainException {
        public BusinessRuleViolation(String message) {
            super(message);
        }
    }

    /** A lifecycle change the state machine does not allow: 409 INVALID_STATUS_TRANSITION (DEC-SHOP-06). */
    public static class InvalidStatusTransition extends DomainException {
        public InvalidStatusTransition(String message) {
            super(message);
        }
    }
}
