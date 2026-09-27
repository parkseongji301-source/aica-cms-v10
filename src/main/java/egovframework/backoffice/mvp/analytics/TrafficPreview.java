package egovframework.backoffice.mvp.analytics;
import egovframework.backoffice.mvp.common.BusinessException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;
/** Illustrative data only. Every consuming screen must label these values as samples. */
@Component
public class TrafficPreview {
    private final java.time.Clock clock;
    public TrafficPreview(java.time.Clock clock){this.clock=clock;}
    public TrafficOverview overview(int days) {
        if (days != 7 && days != 30) throw new BusinessException("조회 기간은 7일 또는 30일을 선택하세요.");
        LocalDate today = LocalDate.now(clock), start = today.minusDays(days - 1);
        int[] counts = {142, 186, 158, 224, 198, 276, 248};
        var series = new ArrayList<TrafficDay>();
        for (int i = 0; i < days; i++) {
            long visitors = counts[Math.floorMod(i - days + 7, 7)];
            series.add(new TrafficDay(start.plusDays(i), visitors, visitors * 3));
        }
        long views = series.stream().mapToLong(TrafficDay::views).sum();
        long visitors = days == 7 ? 1036 : 3824;
        long a = views * 40 / 100, b = views * 25 / 100, c = views * 15 / 100, d = views * 12 / 100;
        return new TrafficOverview(248, 276, visitors, views, start, today, series,
            List.of(new TrafficRank("/ · 메인 페이지", a), new TrafficRank("/education · 교육과정 안내", b),
                new TrafficRank("/notice · 공지사항", c), new TrafficRank("/apply · 지원 안내", d),
                new TrafficRank("/about · 사관학교 소개", views - a - b - c - d)),
            List.of(new TrafficRank("검색", views * 52 / 100), new TrafficRank("직접 방문", views * 28 / 100),
                new TrafficRank("SNS", views * 14 / 100), new TrafficRank("기타", views - views * 52 / 100 - views * 28 / 100 - views * 14 / 100)),
            true, false);
    }
}
