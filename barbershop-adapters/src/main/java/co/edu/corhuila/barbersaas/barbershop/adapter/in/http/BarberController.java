package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.BarberView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.SpecialtyView;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarberUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarberUseCases.NewProfile;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Barbers; the barbershop is always the token's. */
@RestController
@RequestMapping("/api/v1/barbers")
public class BarberController {

    private final BarberUseCases barbers;

    public BarberController(BarberUseCases barbers) {
        this.barbers = barbers;
    }

    @GetMapping
    public PageView<BarberView> list(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Integer limit,
                                     @RequestParam(required = false) String specialty) {
        if (specialty != null && specialty.length() > 80) {
            throw new ValidationException("the request is not valid",
                    List.of(new ApiError.FieldError("specialty", "at most 80 characters")));
        }
        return PageView.of(barbers.list(caller, specialty, Requests.page(page, limit)), BarberView::of);
    }

    @PostMapping
    public ResponseEntity<BarberView> create(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                             @RequestBody(required = false) JsonNode json) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody body = JsonBody.of(json, Set.of("userId", "experienceYears", "bio"));
        UUID userId = body.uuid("userId");
        Integer years = body.integer("experienceYears", false, 0);
        Optional<String> bio = body.optionalText("bio", 500);
        body.validate();
        NewProfile profile = new NewProfile(userId, years == null ? 0 : years, bio == null ? null : bio.orElse(null));
        return created(barbers.create(caller, profile, idempotencyKey), BarberView::of,
                p -> "/api/v1/barbers/" + p.id());
    }

    @GetMapping("/{id}")
    public BarberView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        return BarberView.of(barbers.get(caller, id));
    }

    /** ratingAvg and ratingCount are read-only: sending them answers 400. */
    @PatchMapping("/{id}")
    public BarberView edit(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id,
                           @RequestBody(required = false) JsonNode json) {
        JsonBody body = JsonBody.of(json, Set.of("experienceYears", "bio"));
        if (body.isEmpty()) {
            throw new ValidationException("send at least one field", List.of());
        }
        Integer years = body.integer("experienceYears", false, 0);
        Optional<String> bio = body.optionalText("bio", 500);
        body.validate();
        return BarberView.of(barbers.edit(caller, id, years == null ? null : Optional.of(years), bio));
    }

    @GetMapping("/{id}/specialties")
    public PageView<SpecialtyView> specialties(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                               @PathVariable UUID id, @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer limit) {
        return PageView.of(barbers.specialties(caller, id, Requests.page(page, limit)), SpecialtyView::of);
    }

    @PostMapping("/{id}/specialties")
    public ResponseEntity<SpecialtyView> addSpecialty(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                                      @PathVariable UUID id,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                      @RequestBody(required = false) JsonNode json) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody body = JsonBody.of(json, Set.of("specialtyName"));
        String name = body.requiredText("specialtyName", 80);
        body.validate();
        return created(barbers.addSpecialty(caller, id, name, idempotencyKey), SpecialtyView::of,
                s -> "/api/v1/barbers/" + id + "/specialties/" + s.id());
    }

    @DeleteMapping("/{id}/specialties/{specialtyId}")
    public ResponseEntity<Void> removeSpecialty(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                                @PathVariable UUID id, @PathVariable UUID specialtyId) {
        barbers.removeSpecialty(caller, id, specialtyId);
        return ResponseEntity.noContent().build();
    }

    /** 201 with Location for a new resource; 200 when the same Idempotency-Key was retried. */
    private static <D, V> ResponseEntity<V> created(Created<D> result, Function<D, V> view, Function<V, String> where) {
        V body = view.apply(result.value());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create(where.apply(body))).body(body);
    }
}
