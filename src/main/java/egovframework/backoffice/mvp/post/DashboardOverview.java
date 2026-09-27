package egovframework.backoffice.mvp.post;

import java.time.LocalDate;
import java.util.List;

public record DashboardOverview(long total, long todayCount, long weekCount, LocalDate today,
                                List<DailyPostCount> days, List<Post> recentPosts) {
    public long chartMax() { return Math.max(1, days.stream().mapToLong(DailyPostCount::count).max().orElse(0)); }
}
