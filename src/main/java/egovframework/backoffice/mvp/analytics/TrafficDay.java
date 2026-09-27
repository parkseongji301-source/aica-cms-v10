package egovframework.backoffice.mvp.analytics;

import java.time.LocalDate;
public record TrafficDay(LocalDate day, long visitors, long views) {}
