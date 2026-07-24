package com.fashionstore.product.model;

import com.fashionstore.common.persistence.BaseEntity;
import com.fashionstore.product.model.enumeration.Gender;
import com.fashionstore.product.model.enumeration.ProductType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@Table(name = "size_chart")
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SizeChart extends BaseEntity {

    @Column(nullable = false, length = 255)
    String name;

    @Column(nullable = false, length = 20)
    String unit;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 50)
    ProductType productType;

    @Column(nullable = false)
    @Builder.Default
    Boolean active = true;

    @OneToMany(mappedBy = "sizeChart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<SizeChartRow> rows = new ArrayList<>();
}
