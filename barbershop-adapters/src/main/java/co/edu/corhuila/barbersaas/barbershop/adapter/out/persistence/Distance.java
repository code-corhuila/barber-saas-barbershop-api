package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import java.math.BigDecimal;

/**
 * The approximate distance the public search sorts by: an equirectangular projection, enough to
 * order barbershops of one city. The JDBC repository computes the same expression in SQL.
 */
final class Distance {

    private Distance() {
    }

    /** Squared degrees; a barbershop without coordinates sorts last. */
    static double squared(BigDecimal lat, BigDecimal lng, BigDecimal fromLat, BigDecimal fromLng) {
        if (lat == null || lng == null) {
            return Double.MAX_VALUE;
        }
        double dLat = lat.doubleValue() - fromLat.doubleValue();
        double dLng = (lng.doubleValue() - fromLng.doubleValue()) * Math.cos(Math.toRadians(fromLat.doubleValue()));
        return dLat * dLat + dLng * dLng;
    }
}
