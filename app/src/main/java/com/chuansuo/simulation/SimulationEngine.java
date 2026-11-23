package com.chuansuo.simulation;

import com.chuansuo.model.Coordinate;

/** One owner for coordinates shared by the service, route controls and joystick. */
public final class SimulationEngine {
    public enum Mode { FIXED, JOYSTICK, ROUTE }
    private Coordinate position = new Coordinate(117.027707, 36.667662, 55);
    private Mode mode = Mode.FIXED;
    private RoutePlayer player;
    private double speed;
    private float bearing;

    public synchronized void setPosition(Coordinate point) {
        position = point; mode = Mode.FIXED; player = null; speed = 0;
    }
    public synchronized void startRoute(RoutePlayer route) {
        player = route; mode = Mode.ROUTE; position = route.tick(0);
    }
    public synchronized Coordinate tick(long now) {
        if (mode == Mode.ROUTE && player != null) {
            Coordinate next = player.tick(now);
            double distance = position.distanceTo(next);
            if (distance > 0.001) bearing = (float) position.bearingTo(next);
            position = next;
        }
        return position;
    }
    public synchronized void move(double metersPerSecond, double eastKm, double northKm, double direction) {
        if (!Double.isFinite(metersPerSecond) || metersPerSecond < 0 || !Double.isFinite(eastKm)
                || !Double.isFinite(northKm) || !Double.isFinite(direction)) return;
        if (Math.abs(eastKm) + Math.abs(northKm) < 1e-12) { if (mode != Mode.ROUTE) speed = 0; return; }
        mode = Mode.JOYSTICK; player = null;
        double lat = Math.max(-89.999999, Math.min(89.999999, position.latitude + northKm / 110.574));
        double lon = position.longitude + eastKm / (111.320 * Math.cos(Math.toRadians(position.latitude)));
        lon = ((lon + 180) % 360 + 360) % 360 - 180;
        position = new Coordinate(lon, lat, position.altitude);
        speed = metersPerSecond; bearing = (float) ((direction % 360 + 360) % 360);
    }
    public synchronized void pause(long now) { if (player != null) player.pause(now); }
    public synchronized void resume(long now) { if (player != null) player.resume(now); }
    public synchronized void setRouteSpeed(double value, long now) { if (player != null) player.setSpeed(value, now); }
    public synchronized void stopRoute() { player = null; mode = Mode.FIXED; speed = 0; }
    public synchronized Coordinate currentPosition() { return position; }
    public synchronized Mode mode() { return mode; }
    public synchronized RoutePlayer route() { return player; }
    public synchronized float bearing() { return bearing; }
    public synchronized float speed() {
        return mode == Mode.ROUTE && player != null ? (player.state() == RoutePlayer.State.RUNNING ? (float) player.speed() : 0) : (float) speed;
    }
}
