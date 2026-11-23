package com.chuansuo.app;

import com.chuansuo.model.Coordinate;
import com.chuansuo.simulation.RoutePlayer;
import com.chuansuo.simulation.SimulationEngine;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class RoutePlayerTest {
    private List<Coordinate> line() { return Arrays.asList(new Coordinate(0, 0, 0), new Coordinate(.001, 0, 10), new Coordinate(.002, 0, 20)); }
    @Test public void elapsedTimeDeterminesDistance() {
        RoutePlayer p = new RoutePlayer(line(), 10, false, 1000);
        Coordinate point = p.tick(2500);
        assertEquals(15, line().get(0).distanceTo(point), .001);
    }
    @Test public void oneTickCanCrossMultipleNodes() {
        List<Coordinate> shortLine = Arrays.asList(new Coordinate(0, 0, 0), new Coordinate(.00001, 0, 0), new Coordinate(.00002, 0, 0), new Coordinate(.001, 0, 0));
        RoutePlayer p = new RoutePlayer(shortLine, 10, false, 0);
        assertEquals(10, shortLine.get(0).distanceTo(p.tick(1000)), .001);
    }
    @Test public void pauseDoesNotConsumeTime() {
        RoutePlayer p = new RoutePlayer(line(), 10, false, 0); p.pause(1000);
        assertEquals(10, line().get(0).distanceTo(p.tick(30000)), .001);
        p.resume(40000); assertEquals(20, line().get(0).distanceTo(p.tick(41000)), .001);
    }
    @Test public void speedChangeAccountsForOldSpeedBeforeChange() {
        RoutePlayer p = new RoutePlayer(line(), 10, false, 0); p.setSpeed(20, 1000);
        assertEquals(30, line().get(0).distanceTo(p.tick(2000)), .001);
    }
    @Test public void finishStaysAtExactEndpoint() {
        RoutePlayer p = new RoutePlayer(line(), 10, false, 0);
        assertEquals(.002, p.tick(100000).longitude, 0); assertEquals(RoutePlayer.State.FINISHED, p.state());
        assertEquals(.002, p.tick(200000).longitude, 0); assertEquals(1, p.progress(), 0);
    }
    @Test public void loopConsumesRemainder() {
        RoutePlayer p = new RoutePlayer(line(), 10, true, 0); p.tick(100000);
        assertEquals((1000 % p.lengthMeters()) / p.lengthMeters(), p.progress(), 1e-9);
        assertEquals(RoutePlayer.State.RUNNING, p.state());
    }
    @Test public void duplicatePointsAreSkipped() {
        List<Coordinate> points = Arrays.asList(line().get(0), line().get(0), line().get(1));
        RoutePlayer p = new RoutePlayer(points, 10, false, 0); assertEquals(10, points.get(0).distanceTo(p.tick(1000)), .001);
    }
    @Test public void separatedSegmentsDoNotGenerateConnectingMovement() {
        Coordinate a = new Coordinate(0, 0, 0, null, 0, "");
        Coordinate b = new Coordinate(.001, 0, 0, null, 0, "");
        Coordinate c = new Coordinate(100, 20, 0, null, 1, "");
        Coordinate d = new Coordinate(100.001, 20, 0, null, 1, "");
        RoutePlayer p = new RoutePlayer(Arrays.asList(a, b, c, d), 10, false, 0);
        assertEquals(a.distanceTo(b) + c.distanceTo(d), p.lengthMeters(), .001);
        assertTrue(p.tick(12000).longitude > 99.9);
    }
    @Test public void clockGoingBackwardDoesNotReverseMovement() {
        RoutePlayer p = new RoutePlayer(line(), 10, false, 0); Coordinate a = p.tick(1000);
        assertEquals(a.longitude, p.tick(500).longitude, 0);
    }
    @Test public void datelineInterpolationTakesShortPath() {
        Coordinate midpoint = new Coordinate(179.9, 0, 0).interpolate(new Coordinate(-179.9, 0, 0), .5);
        assertEquals(180, Math.abs(midpoint.longitude), .00001);
    }
    @Test public void coordinateValidationRejectsNonFiniteAndOutOfRange() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 181, -181}) {
            assertThrows(IllegalArgumentException.class, () -> new Coordinate(value, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new Coordinate(0, 91, 0));
    }
    @Test public void zeroLengthAndInvalidSpeedAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RoutePlayer(Arrays.asList(line().get(0), line().get(0)), 1, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new RoutePlayer(line(), 0, false, 0));
    }
    @Test public void simulationHasOnlyOneCoordinateOwner() {
        SimulationEngine e = new SimulationEngine(); e.startRoute(new RoutePlayer(line(), 10, false, 0)); e.tick(1000);
        e.move(1, .001, 0, 90); assertEquals(SimulationEngine.Mode.JOYSTICK, e.mode()); assertNull(e.route());
        Coordinate a = e.tick(2000); assertEquals(a.longitude, e.tick(10000).longitude, 0);
        e.startRoute(new RoutePlayer(line(), 10, false, 10000)); e.setPosition(new Coordinate(116, 39, 55));
        assertEquals(SimulationEngine.Mode.FIXED, e.mode()); assertEquals(116, e.tick(50000).longitude, 0);
    }
    @Test public void routeSpeedIsZeroWhenPausedOrFinished() {
        SimulationEngine e = new SimulationEngine(); e.startRoute(new RoutePlayer(line(), 10, false, 0));
        assertEquals(10, e.speed(), 0); e.pause(1000); assertEquals(0, e.speed(), 0);
        e.resume(2000); assertEquals(10, e.speed(), 0); e.tick(999999); assertEquals(0, e.speed(), 0);
    }
    @Test public void zeroJoystickCallbackDoesNotCancelRoute() {
        SimulationEngine e = new SimulationEngine(); e.startRoute(new RoutePlayer(line(), 10, false, 0));
        e.move(1, 0, 0, 0); assertEquals(SimulationEngine.Mode.ROUTE, e.mode());
    }
}
