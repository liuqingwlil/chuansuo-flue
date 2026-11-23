package com.chuansuo.gpx;

import com.chuansuo.model.Coordinate;
import com.chuansuo.model.LibraryItem;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GpxCodec {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_POINTS = 10000;
    private static final String NS = "http://www.topografix.com/GPX/1/1";
    private GpxCodec() { }

    public static List<LibraryItem> read(InputStream input) throws IOException {
        if (input == null) throw new IOException("无法打开文件");
        ByteArrayOutputStream data = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
        int size;
        while ((size = input.read(buffer)) != -1) {
            if (data.size() + size > MAX_BYTES) throw new IOException("GPX 文件不能超过 8 MB");
            data.write(buffer, 0, size);
        }
        byte[] bytes = data.toByteArray();
        // Before the XML parser sees any input: also catches UTF-16/32 ASCII markup.
        String markup = new String(bytes, StandardCharsets.ISO_8859_1).replace("\u0000", "").toUpperCase(Locale.ROOT);
        if (markup.contains("<!DOCTYPE") || markup.contains("<!ENTITY")) throw new IOException("不支持包含 DTD 或实体定义的 GPX");
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance(); factory.setNamespaceAware(true);
            XmlPullParser p = factory.newPullParser();
            try { p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false); }
            catch (org.xmlpull.v1.XmlPullParserException unsupported) {
                // KXml on some Android versions cannot set an already-disabled feature.
                if (p.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL)) throw unsupported;
            }
            p.setInput(new ByteArrayInputStream(bytes), null);
            List<LibraryItem> result = new ArrayList<>();
            LibraryItem current = null;
            int pointDepth = -1, segment = 0, count = 0;
            double lng = 0, lat = 0, alt = 0; Long time = null; String pointName = "";
            boolean root = false;
            int event;
            while ((event = p.nextToken()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.DOCDECL || event == XmlPullParser.ENTITY_REF && p.getText() == null) throw new IOException("不支持的 XML 实体");
                if (p.getDepth() > 32) throw new IOException("GPX 嵌套层级过深");
                if (event == XmlPullParser.START_TAG) {
                    String tag = p.getName(); int depth = p.getDepth();
                    if (depth == 1) {
                        if (root || !"gpx".equals(tag) || (!p.getNamespace().isEmpty() && !NS.equals(p.getNamespace()))) throw new IOException("请选择 GPX 1.1 文件");
                        root = true;
                    } else if (depth == 2 && ("wpt".equals(tag) || "rte".equals(tag) || "trk".equals(tag))) {
                        current = new LibraryItem(("wpt".equals(tag) ? "地点 " : "线路 ") + (result.size() + 1), !"wpt".equals(tag));
                        segment = "trk".equals(tag) ? -1 : 0;
                    } else if (current != null && depth == 3 && "trkseg".equals(tag)) segment++;
                    if (current != null && ((depth == 2 && "wpt".equals(tag))
                            || (depth == 3 && "rtept".equals(tag)) || (depth == 4 && "trkpt".equals(tag)))) {
                        if (++count > MAX_POINTS) throw new IOException("GPX 最多支持 10000 个节点");
                        lng = Double.parseDouble(p.getAttributeValue(null, "lon"));
                        lat = Double.parseDouble(p.getAttributeValue(null, "lat"));
                        alt = 0; time = null; pointName = ""; pointDepth = depth;
                    } else if (current != null && pointDepth > 0 && depth == pointDepth + 1) {
                        if ("ele".equals(tag)) alt = Double.parseDouble(p.nextText().trim());
                        else if ("time".equals(tag)) time = Instant.parse(p.nextText().trim()).toEpochMilli();
                        else if ("name".equals(tag)) pointName = p.nextText();
                    } else if (current != null && depth == 3 && "name".equals(tag)) current.name = p.nextText();
                } else if (event == XmlPullParser.END_TAG && current != null) {
                    if (p.getDepth() == pointDepth) {
                        current.points.add(new Coordinate(lng, lat, alt, time, Math.max(0, segment), pointName));
                        if (!current.route && !pointName.trim().isEmpty()) current.name = pointName;
                        pointDepth = -1;
                    }
                    if (p.getDepth() == 2) {
                        current.validate(); result.add(current); current = null;
                        if (result.size() > 1000) throw new IOException("GPX 条目过多");
                    }
                }
            }
            if (!root || result.isEmpty()) throw new IOException("文件中没有可导入的地点或线路");
            return result;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("GPX 内容无效：" + e.getMessage(), e); }
    }

    public static void write(OutputStream output, List<LibraryItem> items) throws IOException {
        if (output == null) throw new IOException("无法创建文件");
        if (items.isEmpty()) throw new IOException("没有可导出的条目");
        int count = 0;
        for (LibraryItem item : items) { item.validate(); count += item.points.size(); }
        if (count > MAX_POINTS) throw new IOException("一次最多导出 10000 个节点");
        try {
            XmlSerializer x = XmlPullParserFactory.newInstance().newSerializer(); x.setOutput(output, "UTF-8");
            x.startDocument("UTF-8", true); x.setPrefix("", NS); x.startTag(NS, "gpx");
            x.attribute(null, "version", "1.1"); x.attribute(null, "creator", "ChuanSuo");
            List<LibraryItem> ordered = new ArrayList<>(items);
            ordered.sort((left, right) -> Boolean.compare(left.route, right.route));
            for (LibraryItem item : ordered) {
                if (!item.route) writePoint(x, "wpt", item.points.get(0), item.name);
                else {
                    x.startTag(NS, "trk"); text(x, "name", item.name);
                    int previous = -1;
                    for (Coordinate point : item.points) {
                        if (point.segment != previous) {
                            if (previous != -1) x.endTag(NS, "trkseg");
                            x.startTag(NS, "trkseg"); previous = point.segment;
                        }
                        writePoint(x, "trkpt", point, point.name);
                    }
                    if (previous != -1) x.endTag(NS, "trkseg");
                    x.endTag(NS, "trk");
                }
            }
            x.endTag(NS, "gpx"); x.endDocument(); x.flush();
        } catch (Exception e) { if (e instanceof IOException) throw (IOException) e; throw new IOException("无法导出 GPX", e); }
    }
    private static void writePoint(XmlSerializer x, String tag, Coordinate p, String name) throws IOException {
        x.startTag(NS, tag); x.attribute(null, "lat", Double.toString(p.latitude)); x.attribute(null, "lon", Double.toString(p.longitude));
        text(x, "ele", Double.toString(p.altitude));
        if (p.timeMillis != null) text(x, "time", Instant.ofEpochMilli(p.timeMillis).toString());
        if (name != null && !name.isEmpty()) text(x, "name", name);
        x.endTag(NS, tag);
    }
    private static void text(XmlSerializer x, String tag, String value) throws IOException {
        x.startTag(NS, tag); x.text(value); x.endTag(NS, tag);
    }
}
