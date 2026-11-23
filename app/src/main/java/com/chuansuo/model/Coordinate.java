package com.chuansuo.model;

/** Immutable WGS84 point. Longitude always precedes latitude. */
public final class Coordinate {
    public final double longitude;
    public final double latitude;
    public final double altitude;
    public final Long timeMillis;
    public final int segment;
    public final String name;

    public Coordinate(double longitude, double latitude, double altitude) {
        this(longitude, latitude, altitude, null, 0, "");
    }

    public Coordinate(double longitude, double latitude, double altitude,
                      Long timeMillis, int segment, String name) {
        if (!Double.isFinite(longitude) || Math.abs(longitude) > 180
                || !Double.isFinite(latitude) || Math.abs(latitude) > 90
                || !Double.isFinite(altitude) || segment < 0) {
            throw new IllegalArgumentException("坐标或海拔无效");
        }
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.timeMillis = timeMillis;
        this.segment = segment;
        this.name = name == null ? "" : name;
    }

    public double distanceTo(Coordinate other) {
        double a = Math.toRadians(latitude), b = Math.toRadians(other.latitude);
        double dlat = b - a, dlon = Math.toRadians(other.longitude - longitude);
        double h = Math.pow(Math.sin(dlat / 2), 2)
                + Math.cos(a) * Math.cos(b) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
    }

    public double bearingTo(Coordinate other) {
        double a = Math.toRadians(latitude), b = Math.toRadians(other.latitude);
        double d = Math.toRadians(other.longitude - longitude);
        return (Math.toDegrees(Math.atan2(Math.sin(d) * Math.cos(b),
                Math.cos(a) * Math.sin(b) - Math.sin(a) * Math.cos(b) * Math.cos(d))) + 360) % 360;
    }

    public Coordinate interpolate(Coordinate other, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        double a = Math.toRadians(latitude), b = Math.toRadians(other.latitude);
        double x = Math.toRadians(longitude), y = Math.toRadians(other.longitude);
        double angle = distanceTo(other) / 6371008.8;
        double p = 1 - f, q = f;
        if (angle > 1e-9 && Math.abs(Math.sin(angle)) > 1e-9) {
            p = Math.sin((1 - f) * angle) / Math.sin(angle);
            q = Math.sin(f * angle) / Math.sin(angle);
        }
        double cx = p * Math.cos(a) * Math.cos(x) + q * Math.cos(b) * Math.cos(y);
        double cy = p * Math.cos(a) * Math.sin(x) + q * Math.cos(b) * Math.sin(y);
        double cz = p * Math.sin(a) + q * Math.sin(b);
        return new Coordinate(Math.toDegrees(Math.atan2(cy, cx)),
                Math.toDegrees(Math.atan2(cz, Math.hypot(cx, cy))),
                altitude + f * (other.altitude - altitude));
    }
}
