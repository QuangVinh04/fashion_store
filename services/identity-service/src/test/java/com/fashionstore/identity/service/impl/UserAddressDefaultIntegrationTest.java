package com.fashionstore.identity.service.impl;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.identity.dto.user.UserAddressRequest;
import com.fashionstore.identity.entity.User;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fashionstore.identity.mapper.UserAddressMapperImpl;
import com.fashionstore.identity.repository.UserAddressRepository;
import com.fashionstore.identity.service.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database=h2",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:address-defaults;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserAddressServiceImpl.class, UserAddressMapperImpl.class})
class UserAddressDefaultIntegrationTest {
    @Autowired UserAddressServiceImpl service;
    @Autowired UserAddressRepository addresses;
    @Autowired EntityManager entityManager;
    @MockitoBean CurrentUserProvider currentUser;

    @BeforeEach
    void setUp() {
        selectUser("user-a");
    }

    @Test
    void firstAddressIsDefaultAndEditingCannotUnsetIt() {
        var first = service.createAddress(request("Home", false));
        assertThat(first.getIsDefault()).isTrue();
        flushAndClear();

        var updated = service.updateAddress(first.getId(), request("Updated home", false));
        assertThat(updated.getIsDefault()).isTrue();
        flushAndClear();
        assertThat(service.getMyAddresses()).singleElement().satisfies(address -> {
            assertThat(address.getRecipientName()).isEqualTo("Updated home");
            assertThat(address.getIsDefault()).isTrue();
        });
    }

    @Test
    void switchingDefaultThroughEditAndDedicatedActionKeepsExactlyOneDefault() {
        var first = service.createAddress(request("Home", false));
        var second = service.createAddress(request("Office", false));
        flushAndClear();
        service.updateAddress(second.getId(), request("Office", true));
        flushAndClear();
        assertDefault(second.getId());

        service.updateAddress(first.getId(), request("Updated home", false));
        flushAndClear();
        assertDefault(second.getId());

        service.setDefaultAddress(first.getId());
        flushAndClear();
        assertDefault(first.getId());
    }

    @Test
    void deletingDefaultPromotesRemainingAddressAndDeletingLastLeavesAnEmptyList() {
        var first = service.createAddress(request("Home", false));
        var second = service.createAddress(request("Office", false));
        flushAndClear();
        service.deleteAddress(first.getId());
        flushAndClear();
        assertDefault(second.getId());
        service.deleteAddress(second.getId());
        flushAndClear();
        assertThat(service.getMyAddresses()).isEmpty();
    }

    @Test
    void anotherUserCannotChangeTheDefaultOfAnAddressTheyDoNotOwn() {
        var first = service.createAddress(request("Home", false));
        flushAndClear();
        selectUser("user-b");

        assertThatThrownBy(() -> service.setDefaultAddress(first.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.ADDRESS_NOT_FOUND));
        assertThat(addresses.findById(first.getId())).get()
                .satisfies(address -> assertThat(address.getIsDefault()).isTrue());
    }

    private void assertDefault(String id) {
        assertThat(service.getMyAddresses()).filteredOn(address -> Boolean.TRUE.equals(address.getIsDefault()))
                .extracting(address -> address.getId()).containsExactly(id);
    }

    private void selectUser(String id) {
        User user = User.builder().build();
        user.setId(id);
        when(currentUser.getCurrentUserId()).thenReturn(id);
        when(currentUser.getCurrentUserForUpdate()).thenReturn(user);
    }

    private UserAddressRequest request(String name, boolean isDefault) {
        return new UserAddressRequest(name, "0912345678", "Province", null, "Ward", "Street",
                1, 2, null, null, isDefault);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
