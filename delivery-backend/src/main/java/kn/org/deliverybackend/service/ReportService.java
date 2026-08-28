package kn.org.deliverybackend.service;

import kn.org.deliverybackend.dto.report.CustomerLifetimeValueDTO;
import kn.org.deliverybackend.dto.report.RevenuePointDTO;
import kn.org.deliverybackend.dto.report.SalesSummaryDTO;
import kn.org.deliverybackend.dto.report.TopCategoryDTO;
import kn.org.deliverybackend.dto.report.TopProductDTO;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    enum Granularity { DAY, WEEK, MONTH }

    SalesSummaryDTO summary(LocalDate from, LocalDate to);

    List<RevenuePointDTO> revenueTimeseries(LocalDate from, LocalDate to, Granularity granularity);

    List<TopProductDTO> topProducts(LocalDate from, LocalDate to, int limit);

    List<TopCategoryDTO> topCategories(LocalDate from, LocalDate to, int limit);

    /** Customer LTV across the whole order history (date filter is intentionally absent). */
    List<CustomerLifetimeValueDTO> customerLtv(int limit, int offset);

    long totalCustomersWithOrders();
}
