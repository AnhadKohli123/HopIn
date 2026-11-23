package com.hopin.service;

import com.hopin.model.Ride;
import com.hopin.model.RideStop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RouteUtil {

    public record Point(String name, Double lat, Double lng) {
    }

    public static List<Point> points(Ride r) {
        List<Point> list = new ArrayList<>();
        list.add(new Point(r.source, r.srcLat, r.srcLng));
        for (RideStop s : r.stops) {
            list.add(new Point(s.name, s.lat, s.lng));
        }
        list.add(new Point(r.destination, r.dstLat, r.dstLng));
        return list;
    }

    public record End(String name, Double lat, Double lng, double radiusKm) {
        public boolean geo() {
            return lat != null && lng != null;
        }
    }

    public record Match(int from, int to, Double fromKm, Double toKm) {
    }

    public static Match match(List<Point> pts, End a, End b) {
        int last = pts.size() - 1;
        Match best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < last; i++) {
            Double da = endDistance(pts.get(i), a);
            if (da == null) {
                continue;
            }
            for (int j = i + 1; j <= last; j++) {
                Double db = endDistance(pts.get(j), b);
                if (db == null) {
                    continue;
                }
                if (da + db < bestScore) {
                    bestScore = da + db;
                    best = new Match(i, j, a.geo() ? round1(da) : null, b.geo() ? round1(db) : null);
                }
            }
        }
        return best;
    }

    private static Double endDistance(Point p, End e) {
        if (e.geo()) {
            if (p.lat() == null || p.lng() == null) {
                return null;
            }
            double d = km(p, new Point(null, e.lat(), e.lng()));
            return d <= e.radiusKm() ? d : null;
        }
        if (e.name() == null || e.name().isBlank()) {
            return 0.0;
        }
        return p.name().toLowerCase().contains(e.name().trim().toLowerCase()) ? 0.0 : null;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static boolean multipleOf10(Double v) {
        return v != null && v > 0 && v == Math.floor(v) && ((long) v.doubleValue()) % 10 == 0;
    }

    public static double[] legWeights(List<Point> pts) {
        int legs = pts.size() - 1;
        double[] eq = new double[legs];
        Arrays.fill(eq, 1.0 / legs);
        for (Point p : pts) {
            if (p.lat() == null || p.lng() == null) {
                return eq;
            }
        }
        double[] d = new double[legs];
        double total = 0;
        for (int i = 0; i < legs; i++) {
            d[i] = km(pts.get(i), pts.get(i + 1));
            total += d[i];
        }
        if (total <= 0) {
            return eq;
        }
        for (int i = 0; i < legs; i++) {
            d[i] = d[i] / total;
        }
        return d;
    }

    public static long[] legUnits(List<Point> pts, long units) {
        int legs = pts.size() - 1;
        double[] w = legWeights(pts);
        long base = units >= legs ? 1 : 0;
        long rest = units - base * legs;
        long[] out = new long[legs];
        double[] rem = new double[legs];
        long used = 0;
        for (int i = 0; i < legs; i++) {
            double exact = rest * w[i];
            long fl = (long) Math.floor(exact);
            out[i] = base + fl;
            rem[i] = exact - fl;
            used += fl;
        }
        Integer[] order = new Integer[legs];
        for (int i = 0; i < legs; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> rem[a] != rem[b] ? Double.compare(rem[b], rem[a]) : Integer.compare(a, b));
        long left = Math.max(0, rest - used);
        for (int k = 0; k < left && k < legs; k++) {
            out[order[k]]++;
        }
        return out;
    }

    public static double price(double pricePerSeat, List<Point> pts, int from, int to) {
        long[] u = legUnits(pts, Math.round(pricePerSeat / 10.0));
        long sum = 0;
        for (int i = from; i < to; i++) {
            sum += u[i];
        }
        return Math.max(10, sum * 10);
    }

    public static double roadKm(List<Point> pts, int from, int to) {
        double d = 0;
        for (int i = from; i < to; i++) {
            Point a = pts.get(i);
            Point b = pts.get(i + 1);
            if (a.lat() == null || a.lng() == null || b.lat() == null || b.lng() == null) {
                return 0;
            }
            d += km(a, b);
        }
        return round1(d * 1.25);
    }

    public static double km(Point a, Point b) {
        double r = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(x));
    }
}
