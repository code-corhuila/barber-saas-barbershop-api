package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.ServiceView;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ServiceUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ServiceUseCases.ServiceData;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Services; the barbershop is always the token's. */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private static final Set<String> CREATE_FIELDS = Set.of("name", "description", "durationMinutes", "priceCents");
    private static final Set<String> UPDATE_FIELDS = Set.of("name", "description", "durationMinutes", "priceCents",
            "isActive");

    private final ServiceUseCases services;

    public ServiceController(ServiceUseCases services) {
        this.services = services;
    }

    @GetMapping
    public PageView<ServiceView> list(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer limit,
                                      @RequestParam(required = false) Boolean isActive) {
        return PageView.of(services.list(caller, isActive, Requests.page(page, limit)), ServiceView::of);
    }

    @PostMapping
    public ResponseEntity<ServiceView> create(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                              @RequestBody(required = false) JsonNode json) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody body = JsonBody.of(json, CREATE_FIELDS);
        ServiceData data = data(body);
        body.validate();
        Created<Service> result = services.create(caller, data, idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(ServiceView.of(result.value()));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/services/" + result.value().id()))
                .body(ServiceView.of(result.value()));
    }

    @GetMapping("/{id}")
    public ServiceView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        return ServiceView.of(services.get(caller, id));
    }

    /** Replaces name, description, duration and price; isActive omitted keeps the current state. */
    @PutMapping("/{id}")
    public ServiceView update(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id,
                              @RequestBody(required = false) JsonNode json) {
        JsonBody body = JsonBody.of(json, UPDATE_FIELDS);
        ServiceData data = data(body);
        Optional<Boolean> active = body.bool("isActive");
        body.validate();
        return ServiceView.of(services.update(caller, id, data, active));
    }

    private static ServiceData data(JsonBody body) {
        String name = body.requiredText("name", 100);
        Optional<String> description = body.optionalText("description", 255);
        Integer duration = body.integer("durationMinutes", true, 5);
        Long price = body.longValue("priceCents", 0);
        return new ServiceData(name, description == null ? null : description.orElse(null),
                duration == null ? 0 : duration, price == null ? 0 : price);
    }
}
