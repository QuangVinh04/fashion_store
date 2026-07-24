package com.fashionstore.clothes_retail_api.modules.product.repository.specification;

import com.fashionstore.clothes_retail_api.modules.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class ProductSpecificationsBuilder {

    public final List<SpecSearchCriteria> params;

    public ProductSpecificationsBuilder() {
        params = new ArrayList<>();
    }

    // khi không có toán tử or
    public ProductSpecificationsBuilder with(final String key, final String operation, Object value, final String prefix, final String suffix) {
        return with(null, key, operation, value, prefix, suffix);
    }

    public ProductSpecificationsBuilder with(final String orPredicate, final String key, final String operation, Object value, final String prefix, final String suffix) {
        //Chuyển đổi ký tự đầu tiên của phép toán (operation) thành SearchOperation (enum).
        SearchOperation searchOperation = SearchOperation.getSimpleOperation(operation.charAt(0));
        if (searchOperation != null) {
            if (searchOperation == SearchOperation.EQUALITY) {
                String strValue = value.toString();

                // Tự động kiểm tra dấu * trong giá trị truyền vào
                boolean startWithAsterisk = strValue.startsWith(SearchOperation.ZERO_OR_MORE_REGEX);
                boolean endWithAsterisk = strValue.endsWith(SearchOperation.ZERO_OR_MORE_REGEX);

                if (startWithAsterisk && endWithAsterisk) {
                    searchOperation = SearchOperation.CONTAINS;
                    value = strValue.substring(1, strValue.length() - 1); // Cắt bỏ 2 dấu *
                } else if (startWithAsterisk) {
                    searchOperation = SearchOperation.ENDS_WITH;
                    value = strValue.substring(1); // Cắt bỏ dấu * ở đầu
                } else if (endWithAsterisk) {
                    searchOperation = SearchOperation.STARTS_WITH;
                    value = strValue.substring(0, strValue.length() - 1); // Cắt bỏ dấu * ở cuối
                }
            }
            params.add(new SpecSearchCriteria(orPredicate, key, searchOperation, value));
        }
        return this;
    }

    // tạo 1 Specification kết hợp các điều kiện
    public Specification<Product> build() {
        if (params.isEmpty())
            return null;

        // Tạo danh sách các Specification từ params, loại bỏ các giá trị null tiềm ẩn
        List<ProductSpecification> specs = params.stream()
                .map(ProductSpecification::new)
                .toList();

        // khởi tạo 1 Specification từ đk tìm kiếm đầu tiên
        Specification<Product> result = specs.getFirst();

        for (int i = 1; i < params.size(); i++) {
            // Kiểm tra xem tiêu chí hiện tại có phải là toán tử OR không
            if (params.get(i).isOrPredicate()) {
                result = result.or(specs.get(i));
            } else {
                result = result.and(specs.get(i));
            }
        }
        return result;
    }

    public ProductSpecificationsBuilder with(ProductSpecification spec) {
        params.add(spec.getCriteria());
        return this;
    }

    public ProductSpecificationsBuilder with(SpecSearchCriteria criteria) {
        params.add(criteria);
        return this;
    }
}
