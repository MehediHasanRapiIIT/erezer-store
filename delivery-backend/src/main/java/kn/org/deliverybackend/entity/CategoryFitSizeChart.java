package kn.org.deliverybackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** A category's chart for one of the fits: its products (and those of the categories under it) show it for that fit. */
@Entity
@Table(name = "category_fit_size_chart")
@IdClass(CategoryFitSizeChart.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryFitSizeChart {

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    @Id
    @Column(name = "fit_code", length = 40)
    private String fitCode;

    @Column(name = "size_chart_id", nullable = false)
    private Long sizeChartId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long categoryId;
        private String fitCode;
    }
}
