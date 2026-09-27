package egovframework.backoffice.mvp.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

public record TrafficOverview(long todayVisitors, long yesterdayVisitors, long visitors, long views,
                              LocalDate start, LocalDate end, List<TrafficDay> days,
                              List<TrafficRank> pages, List<TrafficRank> sources,
                              boolean hasData, boolean collectionEnabled) {
    public long chartMax() { return Math.max(1, days.stream().mapToLong(TrafficDay::visitors).max().orElse(0)); }
    public String chartPoints() {
        return IntStream.range(0, days.size()).mapToObj(i -> (30 + i * 600 / (days.size() - 1)) + ","
                + (162 - days.get(i).visitors() * 126 / chartMax())).collect(Collectors.joining(" "));
    }
    public String areaPoints() { return "30,162 " + chartPoints() + " 630,162"; }
}
