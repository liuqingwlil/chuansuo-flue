package com.chuansuo.app;

import com.chuansuo.gpx.GpxCodec;
import com.chuansuo.model.Coordinate;
import com.chuansuo.model.LibraryItem;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class GpxCodecTest {
    private List<LibraryItem> read(String body) throws IOException { return GpxCodec.read(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))); }
    private String gpx(String body) { return "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"test\">" + body + "</gpx>"; }
    private LibraryItem route() {
        LibraryItem item = new LibraryItem("线路 & <测试>", true);
        item.points.add(new Coordinate(116.397, 39.908, 55, 1700000000000L, 0, "起点"));
        item.points.add(new Coordinate(116.398, 39.909, 65, null, 0, ""));
        item.points.add(new Coordinate(117, 40, 5, null, 1, "分段"));
        item.points.add(new Coordinate(117.001, 40.001, 6, null, 1, "")); return item;
    }
    @Test public void roundTripPreservesCoordinatesNamesTimesAndSegments() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); GpxCodec.write(out, Collections.singletonList(route()));
        LibraryItem actual = read(out.toString("UTF-8")).get(0); assertEquals(route().name, actual.name); assertEquals(4, actual.points.size());
        for (int i = 0; i < 4; i++) { Coordinate a = route().points.get(i), b = actual.points.get(i); assertEquals(a.longitude, b.longitude, 0); assertEquals(a.latitude, b.latitude, 0); assertEquals(a.altitude, b.altitude, 0); assertEquals(a.timeMillis, b.timeMillis); assertEquals(a.segment, b.segment); assertEquals(a.name, b.name); }
    }
    @Test public void waypointIsImportedAsPlaceWithoutRequiringAltitude() throws Exception {
        LibraryItem place = read(gpx("<wpt lat=\"39\" lon=\"116\"><name>家 &amp; 公司</name></wpt>")).get(0);
        assertFalse(place.route); assertEquals("家 & 公司", place.name); assertEquals(0, place.points.get(0).altitude, 0); assertNull(place.points.get(0).timeMillis);
    }
    @Test public void routePointsAreSupported() throws Exception {
        LibraryItem route = read(gpx("<rte><name>步行</name><rtept lat=\"39\" lon=\"116\"/><rtept lat=\"39.01\" lon=\"116\"/></rte>")).get(0);
        assertTrue(route.route); assertEquals("步行", route.name); assertEquals(2, route.points.size());
    }
    @Test public void separateTracksStaySeparate() throws Exception {
        String track = "<trk><trkseg><trkpt lat=\"39\" lon=\"116\"/><trkpt lat=\"39.01\" lon=\"116\"/></trkseg></trk>";
        assertEquals(2, read(gpx(track + track)).size());
    }
    @Test public void invalidCoordinatesFailEntireImport() {
        for (String lat : new String[]{"91", "NaN", "Infinity", "abc"}) assertThrows(IOException.class, () -> read(gpx("<wpt lat=\"" + lat + "\" lon=\"116\"/>")));
    }
    @Test public void missingLongitudeAndInvalidTimeAreRejected() {
        assertThrows(IOException.class, () -> read(gpx("<wpt lat=\"39\"/>")));
        assertThrows(IOException.class, () -> read(gpx("<wpt lat=\"39\" lon=\"116\"><time>tomorrow</time></wpt>")));
    }
    @Test public void doctypeAndExternalEntitiesAreRejected() {
        String xml = "<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///secret'>]>" + gpx("<wpt lat=\"39\" lon=\"116\"><name>&x;</name></wpt>");
        assertThrows(IOException.class, () -> read(xml));
        assertThrows(IOException.class, () -> GpxCodec.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_16))));
    }
    @Test public void fileAndPointLimitsAreEnforced() {
        assertThrows(IOException.class, () -> GpxCodec.read(new ByteArrayInputStream(new byte[GpxCodec.MAX_BYTES + 1])));
        StringBuilder xml = new StringBuilder("<trk><trkseg>"); for (int i = 0; i <= GpxCodec.MAX_POINTS; i++) xml.append("<trkpt lat=\"39\" lon=\"116\"/>");
        xml.append("</trkseg></trk>");
        assertThrows(IOException.class, () -> read(gpx(xml.toString())));
    }
    @Test public void emptyMalformedAndWrongNamespaceFilesAreRejected() {
        for (String xml : new String[]{"", "<gpx>", "<html/>", "<gpx xmlns='wrong'><wpt lat='39' lon='116'/></gpx>", gpx("")}) assertThrows(IOException.class, () -> read(xml));
    }
    @Test public void exportPlacesBeforeTracksForSchemaOrder() throws Exception {
        LibraryItem place = new LibraryItem("地点", false); place.points.add(new Coordinate(116, 39, 0));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); GpxCodec.write(out, Arrays.asList(route(), place));
        String xml = out.toString("UTF-8"); assertTrue(xml.indexOf("<wpt") < xml.indexOf("<trk>")); assertEquals(2, read(xml).size());
    }
    @Test public void emptyExportIsRejected() { assertThrows(IOException.class, () -> GpxCodec.write(new ByteArrayOutputStream(), Collections.emptyList())); }
}
