package egovframework.backoffice.mvp.post;

import java.time.LocalDate;

public record DailyPostCount(LocalDate day, long count) {}
