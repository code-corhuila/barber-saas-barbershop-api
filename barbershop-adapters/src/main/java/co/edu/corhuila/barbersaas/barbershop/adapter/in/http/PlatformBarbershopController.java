package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.BarbershopView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.PlatformBarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.PlatformBarbershopUseCases.Filter;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP to use case for the operations platform-admin calls with its service token (tag Internal,
 * DEC-SHOP-06). Served only on the internal network: the api-gateway routes /api/v1, never /internal/v1.
 * The use case checks the token; this class only checks the shape of the request.
 */
@RestController
@RequestMapping("/internal/v1/barbershops")
public class PlatformBarbershopController {

    /** ChangeBarbershopStatusRequest: TRIAL is not a valid target. */
    private static final Set<String> TARGETS = Set.of("ACTIVE", "SUSPENDED", "CANCELLED");

    private final PlatformBarbershopUseCases platform;

    public PlatformBarbershopController(PlatformBarbershopUseCases platform) {
        this.platform = platform;
    }

    @GetMapping
    public PageView<BarbershopView> list(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String planId,
                                         @RequestParam(required = false) String trialEndsBefore) {
        List<FieldError> errors = new ArrayList<>();
        BarbershopStatus s = null;
        if (status != null) {
            try {
                s = BarbershopStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("status", "one of TRIAL, ACTIVE, SUSPENDED, CANCELLED"));
            }
        }
        UUID plan = null;
        if (planId != null) {
            try {
                plan = UUID.fromString(planId);
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("planId", "must be a UUID"));
            }
        }
        Instant before = null;
        if (trialEndsBefore != null) {
            try {
                before = Instant.parse(trialEndsBefore);
            } catch (DateTimeParseException e) {
                errors.add(new FieldError("trialEndsBefore", "must be an RFC 3339 timestamp in UTC"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("the request is not valid", errors);
        }
        return PageView.of(platform.list(caller, new Filter(s, plan, before), Requests.page(page, limit)),
                BarbershopView::of);
    }

    @GetMapping("/{id}")
    public BarbershopView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        return BarbershopView.of(platform.get(caller, id));
    }

    @PatchMapping("/{id}/status")
    public BarbershopView changeStatus(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                       @PathVariable UUID id, @RequestBody(required = false) JsonNode json) {
        JsonBody body = JsonBody.of(json, Set.of("status"));
        String target = body.requiredText("status", 20);
        if (target != null && !TARGETS.contains(target)) {
            throw new ValidationException("the request is not valid",
                    List.of(new FieldError("status", "one of ACTIVE, SUSPENDED, CANCELLED")));
        }
        body.validate();
        return BarbershopView.of(platform.changeStatus(caller, id, BarbershopStatus.valueOf(target)));
    }

    @PutMapping("/{id}/plan")
    public BarbershopView assignPlan(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                     @PathVariable UUID id, @RequestBody(required = false) JsonNode json) {
        JsonBody body = JsonBody.of(json, Set.of("planId"));
        UUID plan = body.uuid("planId");
        body.validate();
        return BarbershopView.of(platform.assignPlan(caller, id, plan));
    }
}
