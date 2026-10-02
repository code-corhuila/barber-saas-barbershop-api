package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.BarberView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.BarbershopView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.Views.ServiceView;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopChanges;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Barbershops: validates the shape, never decides business rules. */
@RestController
@RequestMapping("/api/v1/barbershops")
public class BarbershopController {

    private static final Set<String> EDITABLE = Set.of("name", "address", "city", "latitude", "longitude", "phone",
            "whatsappNumber", "logoUrl", "timezone", "cancellationPolicyHours");

    private final BarbershopUseCases barbershops;

    public BarbershopController(BarbershopUseCases barbershops) {
        this.barbershops = barbershops;
    }

    @GetMapping
    public PageView<BarbershopView> search(@RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer limit,
                                           @RequestParam(required = false) String city,
                                           @RequestParam(required = false) BigDecimal lat,
                                           @RequestParam(required = false) BigDecimal lng) {
        List<FieldError> errors = new ArrayList<>();
        if (city != null && city.length() > 80) {
            errors.add(new FieldError("city", "at most 80 characters"));
        }
        if ((lat == null) != (lng == null)) {
            errors.add(new FieldError("lat/lng", "send both or neither"));
        } else if (lat != null && (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
            errors.add(new FieldError("lat/lng", "out of range"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("the request is not valid", errors);
        }
        return PageView.of(barbershops.search(new Search(city, lat, lng), Requests.page(page, limit)),
                BarbershopView::of);
    }

    @GetMapping("/me")
    public BarbershopView mine(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller) {
        return BarbershopView.of(barbershops.getMine(caller));
    }

    /** DEC-SHOP-03: status and planId are not editable fields, so sending them answers 400. */
    @PatchMapping("/me")
    public BarbershopView editMine(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                   @RequestBody(required = false) JsonNode json) {
        JsonBody body = JsonBody.of(json, EDITABLE);
        if (body.isEmpty()) {
            throw new ValidationException("send at least one field", List.of());
        }
        BarbershopChanges changes = BarbershopChanges.none()
                .withName(body.presentText("name", 120))
                .withAddress(body.optionalText("address", 255))
                .withCity(body.presentText("city", 80))
                .withLatitude(body.optionalNumber("latitude"))
                .withLongitude(body.optionalNumber("longitude"))
                .withPhone(body.optionalText("phone", 20))
                .withWhatsappNumber(body.optionalText("whatsappNumber", 20))
                .withLogoUrl(body.optionalText("logoUrl", 255))
                .withTimezone(body.presentText("timezone", 50))
                .withCancellationPolicyHours(body.integer("cancellationPolicyHours", false, 0));
        body.validate();
        return BarbershopView.of(barbershops.editMine(caller, changes));
    }

    @GetMapping("/{id}")
    public BarbershopView visible(@PathVariable UUID id) {
        return BarbershopView.of(barbershops.getVisible(id));
    }

    @GetMapping("/{id}/services")
    public PageView<ServiceView> services(@PathVariable UUID id, @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer limit) {
        return PageView.of(barbershops.listVisibleServices(id, Requests.page(page, limit)), ServiceView::of);
    }

    @GetMapping("/{id}/barbers")
    public PageView<BarberView> barbers(@PathVariable UUID id, @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer limit) {
        return PageView.of(barbershops.listVisibleBarbers(id, Requests.page(page, limit)), BarberView::of);
    }
}
