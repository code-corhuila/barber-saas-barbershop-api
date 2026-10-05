package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.BarbershopView;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.InternalBarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.InternalBarbershopUseCases.NewBarbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP to use case for the tag Internal (DEC-SHOP-05). Served only on the internal network: the
 * api-gateway routes /api/v1, never /internal/v1. The use case checks the service token.
 */
@RestController
@RequestMapping("/internal/v1/barbershops")
public class InternalBarbershopController {

    private static final Set<String> CREATE_FIELDS = Set.of("name", "city", "address", "phone", "latitude",
            "longitude");

    private final InternalBarbershopUseCases onboarding;

    public InternalBarbershopController(InternalBarbershopUseCases onboarding) {
        this.onboarding = onboarding;
    }

    @PostMapping
    public ResponseEntity<BarbershopView> create(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                 @RequestBody(required = false) JsonNode json) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody body = JsonBody.of(json, CREATE_FIELDS);
        NewBarbershop data = new NewBarbershop(body.requiredText("name", 120),
                text(body.optionalText("address", 255)), body.requiredText("city", 80),
                body.number("latitude", -90, 90), body.number("longitude", -180, 180),
                text(body.optionalText("phone", 20)));
        body.validate();
        Created<Barbershop> result = onboarding.create(caller, data, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(BarbershopView.of(result.value()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                       @PathVariable UUID id) {
        onboarding.remove(caller, id);
        return ResponseEntity.noContent().build();
    }

    /** Absent and null are the same on a creation. */
    private static String text(Optional<String> value) {
        return value == null ? null : value.orElse(null);
    }
}
