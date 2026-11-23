package com.chuansuo.simulation;

import com.chuansuo.model.Coordinate;
import java.util.ArrayList;
import java.util.List;

/** Clock-driven playback; separate GPX segments are never joined by a fake line. */
public final class RoutePlayer {
    public enum State { RUNNING, PAUSED, FINISHED }
    private final List<Coordinate> points;
    private final double[] cumulative;
    private final double total;
    private final boolean loop;
    private double speed;
    private double traveled;
    private long lastMillis;
    private State state = State.RUNNING;

    public RoutePlayer(List<Coordinate> source, double speed, boolean loop, long now) {
        if (source.size() < 2) throw new IllegalArgumentException("线路至少需要两个节点");
        points = new ArrayList<>(source);
        cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + (points.get(i).segment == points.get(i - 1).segment
                    ? points.get(i - 1).distanceTo(points.get(i)) : 0);
        }
        total = cumulative[cumulative.length - 1];
        if (total < 0.01) throw new IllegalArgumentException("线路长度不足");
        setSpeed(speed, now);
        this.loop = loop;
        lastMillis = now;
    }

    public synchronized Coordinate tick(long now) {
        if (now < lastMillis) now = lastMillis;
        if (state == State.RUNNING) {
            traveled += speed * (now - lastMillis) / 1000.0;
            if (traveled >= total) {
                if (loop) traveled %= total;
                else { traveled = total; state = State.FINISHED; }
            }
        }
        lastMillis = now;
        if (traveled >= total) return points.get(points.size() - 1);
        for (int i = 1; i < points.size(); i++) {
            if (cumulative[i] > traveled) {
                double distance = cumulative[i] - cumulative[i - 1];
                return points.get(i - 1).interpolate(points.get(i),
                        (traveled - cumulative[i - 1]) / distance);
            }
        }
        return points.get(points.size() - 1);
    }

    public synchronized void pause(long now) { tick(now); if (state == State.RUNNING) state = State.PAUSED; }
    public synchronized void resume(long now) { if (state == State.PAUSED) { lastMillis = now; state = State.RUNNING; } }
    public synchronized void setSpeed(double value, long now) {
        if (!Double.isFinite(value) || value <= 0 || value > 100) throw new IllegalArgumentException("速度需为 0～100 米/秒（不含 0）");
        if (speed > 0) tick(now);
        speed = value;
    }
    public synchronized double progress() { return traveled / total; }
    public synchronized State state() { return state; }
    public synchronized double speed() { return speed; }
    public double lengthMeters() { return total; }
}
