package com.chuansuo.model;

import java.util.ArrayList;
import java.util.List;

public final class LibraryItem {
    public long id;
    public String name;
    public boolean route;
    public long groupId;
    public boolean pinned;
    public double speed = 1.2;
    public boolean loop;
    public final List<Coordinate> points = new ArrayList<>();

    public LibraryItem(String name, boolean route) {
        this.name = name;
        this.route = route;
    }

    public void validate() {
        if (name == null || name.trim().isEmpty() || name.length() > 200) {
            throw new IllegalArgumentException("名称需为 1～200 个字符");
        }
        if (points.size() < (route ? 2 : 1) || (!route && points.size() != 1)) {
            throw new IllegalArgumentException(route ? "线路至少需要两个节点" : "地点需要一个坐标");
        }
        if (points.size() > 10000 || points.contains(null)) throw new IllegalArgumentException("节点无效或超过 10000 个");
        if (!Double.isFinite(speed) || speed <= 0 || speed > 100) {
            throw new IllegalArgumentException("速度需大于 0 且不超过 100 米/秒");
        }
        if (route && lengthMeters() < 0.01) throw new IllegalArgumentException("线路需要不同的位置节点");
    }

    public double lengthMeters() {
        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).segment == points.get(i - 1).segment) {
                length += points.get(i - 1).distanceTo(points.get(i));
            }
        }
        return length;
    }
}
