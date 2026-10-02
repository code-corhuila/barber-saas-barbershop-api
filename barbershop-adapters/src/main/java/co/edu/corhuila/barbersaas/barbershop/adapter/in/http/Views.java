package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** The response schemas of barbershop-service.yaml: camelCase fields of the contract, never the entity itself. */
final class Views {

    private Views() {
    }

    record Meta(int page, int limit, long total, long totalPages) { }

    record PageView<T>(List<T> data, Meta meta) {
        static <D, T> PageView<T> of(Page<D> page, Function<D, T> view) {
            return new PageView<>(page.items().stream().map(view).toList(),
                    new Meta(page.page(), page.limit(), page.total(), page.totalPages()));
        }
    }

    record BarbershopView(UUID id, String name, String address, String city, BigDecimal latitude,
                          BigDecimal longitude, String phone, String whatsappNumber, String logoUrl, String status,
                          UUID planId, String timezone, int cancellationPolicyHours, Instant trialEndsAt,
                          Instant createdAt, Instant updatedAt) {
        static BarbershopView of(Barbershop b) {
            return new BarbershopView(b.id(), b.name(), b.address(), b.city(), b.latitude(), b.longitude(), b.phone(),
                    b.whatsappNumber(), b.logoUrl(), b.status().name(), b.planId(), b.timezone(),
                    b.cancellationPolicyHours(), b.trialEndsAt(), b.createdAt(), b.updatedAt());
        }
    }

    record ServiceView(UUID id, String name, String description, int durationMinutes, long priceCents,
                       boolean isActive, Instant createdAt) {
        static ServiceView of(Service s) {
            return new ServiceView(s.id(), s.name(), s.description(), s.durationMinutes(), s.priceCents(), s.active(),
                    s.createdAt());
        }
    }

    record SpecialtyView(UUID id, UUID barberProfileId, String specialtyName) {
        static SpecialtyView of(BarberSpecialty s) {
            return new SpecialtyView(s.id(), s.barberProfileId(), s.specialtyName());
        }
    }

    /** DEC-SHOP-04: no fullName and no photo; they live in identity-auth. */
    record BarberView(UUID id, UUID userId, int experienceYears, String bio, BigDecimal ratingAvg, int ratingCount,
                      List<SpecialtyView> specialties) {
        static BarberView of(BarberProfile p) {
            return new BarberView(p.id(), p.userId(), p.experienceYears(), p.bio(), p.ratingAvg(), p.ratingCount(),
                    p.specialties().stream().map(SpecialtyView::of).toList());
        }
    }
}
