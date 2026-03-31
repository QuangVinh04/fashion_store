package com.fashionstore.clothes_retail_api.modules.product.repository.specification;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SpecSearchCriteria { // chứa thông tin tìm kiếm
    private String key;
    private SearchOperation operation;
    private Object value;
    private boolean orPredicate;

    // xử lí tiêu chí đơn giản
    public SpecSearchCriteria(final String key, final SearchOperation operation, final Object value) {
        super();
        this.key = key;
        this.operation = operation;
        this.value = value;
    }

    // kiểm tra xem có điều kiện OR hay không
    public SpecSearchCriteria(final String orPredicate, final String key, final SearchOperation operation, final Object value) {
        super();
        this.orPredicate = orPredicate != null && orPredicate.equals(SearchOperation.OR_PREDICATE_FLAG);
        this.key = key;
        this.operation = operation;
        this.value = value;
    }


    //xử lý toán tử like
    public SpecSearchCriteria(String key, String operation, String prefix, String value, String suffix) {
        SearchOperation searchOperation = SearchOperation.getSimpleOperation(operation.charAt(0));
        if (searchOperation != null) {
            if (searchOperation == SearchOperation.EQUALITY) { // the operation may be complex operation
                final boolean startWithAsterisk = prefix != null && prefix.contains(SearchOperation.ZERO_OR_MORE_REGEX); // "*"
                final boolean endWithAsterisk = suffix != null && suffix.contains(SearchOperation.ZERO_OR_MORE_REGEX);

                if (startWithAsterisk && endWithAsterisk) {
                    searchOperation = SearchOperation.CONTAINS; // -> "%s%"
                } else if (startWithAsterisk) {
                    searchOperation = SearchOperation.ENDS_WITH; // -> "%s"
                } else if (endWithAsterisk) {
                    searchOperation = SearchOperation.STARTS_WITH; // -> "s%"
                }
            }
        }
        this.key = key;
        this.operation = searchOperation;
        this.value = value;
    }
}
