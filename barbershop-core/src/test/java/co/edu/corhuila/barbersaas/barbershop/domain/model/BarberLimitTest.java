package co.edu.corhuila.barbersaas.barbershop.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DEC-SHOP-07: a barbershop has at most the barbers its plan allows; 999 means unlimited. */
class BarberLimitTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");

    private static Barbershop withBarbers(int count) {
        return Barbershop.register(UUID.randomUUID(), "El Clásico", "Neiva", NOW).withActiveBarbers(count);
    }

    @Test
    void a_new_barbershop_has_no_barbers_and_the_count_is_kept_as_read() {
        assertEquals(0, Barbershop.register(UUID.randomUUID(), "El Clásico", "Neiva", NOW).activeBarbers());
        assertEquals(3, withBarbers(3).activeBarbers());
        assertThrows(BusinessRuleViolation.class, () -> withBarbers(-1));
    }

    @Test
    void below_the_limit_there_is_room_for_another_barber() {
        assertDoesNotThrow(() -> withBarbers(1).requireRoomForAnotherBarber(2));
    }

    @Test
    void at_the_limit_the_plan_refuses_another_barber() {
        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class,
                () -> withBarbers(2).requireRoomForAnotherBarber(2));

        assertEquals("Tu plan permite 2 barberos", e.getMessage());
        assertEquals("Tu plan permite 1 barbero",
                assertThrows(BusinessRuleViolation.class, () -> withBarbers(1).requireRoomForAnotherBarber(1))
                        .getMessage());
    }

    @Test
    void an_unlimited_plan_never_refuses() {
        assertDoesNotThrow(() -> withBarbers(999).requireRoomForAnotherBarber(999));
        assertDoesNotThrow(() -> withBarbers(5000).requireRoomForAnotherBarber(999));
    }

    @Test
    void a_change_of_status_plan_or_data_keeps_the_count() {
        Barbershop shop = withBarbers(4);

        assertEquals(4, shop.assignPlan(UUID.randomUUID(), NOW).activeBarbers());
        assertEquals(4, shop.changeStatus(BarbershopStatus.ACTIVE, NOW).activeBarbers());
        assertEquals(4, shop.edit(new BarbershopChanges(null, null, null, null, null, null, null, null, null, null),
                NOW).activeBarbers());
    }
}
