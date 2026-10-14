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

/** A product's own size chart for one of its fits, when it should differ from the fit's chart. */
@Entity
@Table(name = "product_fit_size_chart")
@IdClass(ProductFitSizeChart.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductFitSizeChart {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "fit_code", length = 40)
    private String fitCode;

    @Column(name = "size_chart_id", nullable = false)
    private Long sizeChartId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Long productId;
        private String fitCode;
    }
}
