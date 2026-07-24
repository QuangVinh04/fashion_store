package com.fashionstore.product.model;

import com.fashionstore.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@Entity
@Table(name = "size_chart_row")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SizeChartRow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "size_chart_id", nullable = false)
    SizeChart sizeChart;

    @Column(name = "size_code", nullable = false, length = 50)
    String sizeCode;

    @Column(precision = 10, scale = 2)
    BigDecimal chest;

    @Column(precision = 10, scale = 2)
    BigDecimal waist;

    @Column(precision = 10, scale = 2)
    BigDecimal hip;

    @Column(precision = 10, scale = 2)
    BigDecimal shoulder;

    @Column(precision = 10, scale = 2)
    BigDecimal length;

    @Column(precision = 10, scale = 2)
    BigDecimal inseam;
}
