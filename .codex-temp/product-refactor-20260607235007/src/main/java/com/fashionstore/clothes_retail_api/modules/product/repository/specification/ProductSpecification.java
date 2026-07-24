package com.fashionstore.clothes_retail_api.modules.product.repository.specification;

import com.fashionstore.clothes_retail_api.modules.category.entity.Category;
import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import com.fashionstore.clothes_retail_api.modules.product.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;


@Getter
@AllArgsConstructor
public class ProductSpecification implements Specification<Product> {

    private SpecSearchCriteria criteria;



    @Override
    public Predicate toPredicate(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        String key = criteria.getKey();
        Object value = criteria.getValue();

        // Xử lý đặc biệt cho một số trường
        return switch (key) {
            case "category" -> {
                Join<Object, Category> categoryJoin = root.join("category");

                yield cb.like(cb.lower(categoryJoin.get("name")), "%" + value.toString().toLowerCase() + "%");
            }

            case "priceRange" -> {
                String rangeStr = criteria.getValue().toString();


                if (rangeStr.endsWith("+")) {
                    BigDecimal min = new BigDecimal(rangeStr.replace("+", ""));
                    yield cb.greaterThanOrEqualTo(root.get("price"), min);
                } else {
                    String[] bounds = rangeStr.split("-");
                    if (bounds.length == 2) {
                        BigDecimal min = new BigDecimal(bounds[0]);
                        BigDecimal max = new BigDecimal(bounds[1]);
                        yield cb.between(root.get("price"), min, max);
                    } else yield null;
                }
            }


            case "color", "size" -> {
                Join<Product, ProductVariant> variantJoin = root.join("variants");
                yield cb.equal(variantJoin.get(key), value);
            }

            default -> getPredicate(cb, root.get(key), value);
        };
    }

    private Predicate getPredicate(CriteriaBuilder cb, Path path, Object value) {
        Class<?> type = path.getJavaType();

        // Convert giá trị String từ URL sang đúng kiểu dữ liệu của cột
        Object castedValue = value;
        if (type != String.class) {
            if (type == BigDecimal.class) castedValue = new BigDecimal(value.toString());
            else if (type == Integer.class || type == int.class) castedValue = Integer.parseInt(value.toString());
            else if (type == Long.class || type == long.class) castedValue = Long.parseLong(value.toString());
            else if (type == Double.class || type == double.class) castedValue = Double.parseDouble(value.toString());
        }

        return switch (criteria.getOperation()) {
            case EQUALITY -> cb.equal(path, castedValue);
            case NEGATION -> cb.notEqual(path, castedValue);
            case GREATER_THAN -> cb.greaterThan(path, (Comparable) castedValue);
            case LESS_THAN -> cb.lessThan(path, (Comparable) castedValue);
            case LIKE, CONTAINS -> cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase() + "%");
            case STARTS_WITH -> cb.like(cb.lower(path.as(String.class)), value.toString().toLowerCase() + "%");
            case ENDS_WITH -> cb.like(cb.lower(path.as(String.class)), "%" + value.toString().toLowerCase());
            default -> null;
        };
    }
}
