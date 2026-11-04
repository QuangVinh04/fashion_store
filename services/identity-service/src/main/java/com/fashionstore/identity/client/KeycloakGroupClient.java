package com.fashionstore.identity.client;

import static com.fashionstore.common.security.KeycloakJwtAuthoritiesConverter.API_CLIENT_ID;

import com.fashionstore.common.exception.AppException;
import com.fashionstore.common.exception.ErrorCode;
import com.fashionstore.common.redis.CacheRegion;
import com.fashionstore.common.redis.RedisService;
import com.fashionstore.common.resilience.UpstreamFailure;
import com.fashionstore.identity.dto.group.KeycloakGroupRepresentation;
import com.fashionstore.identity.dto.group.KeycloakIdRepresentation;
import com.fashionstore.identity.dto.group.KeycloakRoleRepresentation;
import com.fashionstore.identity.dto.group.KeycloakUserBrief;
import com.fashionstore.identity.dto.staff.KeycloakNewUserRepresentation;
import com.fashionstore.identity.exception.IdentityErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Nhóm, quyền của nhóm (client role của {@code fashion-api}) và thành viên nhóm trong Keycloak. Dùng chung
 * breaker/retry {@code keycloakAdmin} với {@link KeycloakAdminClient}. 404 / 409 / 400 là câu trả lời nghiệp vụ
 * (không retry, không mở circuit); 5xx và mất kết nối thành 502 ở fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakGroupClient {

    private static final Map<Integer, IdentityErrorCode> GROUP_ERRORS =
            Map.of(HttpStatus.NOT_FOUND.value(), IdentityErrorCode.GROUP_NOT_FOUND);
    private static final Map<Integer, IdentityErrorCode> GROUP_WRITE_ERRORS = Map.of(
            HttpStatus.NOT_FOUND.value(), IdentityErrorCode.GROUP_NOT_FOUND,
            HttpStatus.CONFLICT.value(), IdentityErrorCode.GROUP_NAME_TAKEN);
    private static final Map<Integer, IdentityErrorCode> USER_ERRORS =
            Map.of(HttpStatus.NOT_FOUND.value(), IdentityErrorCode.USER_NOT_FOUND);
    private static final Map<Integer, IdentityErrorCode> NEW_USER_ERRORS = Map.of(
            HttpStatus.CONFLICT.value(), IdentityErrorCode.STAFF_EMAIL_TAKEN,
            HttpStatus.BAD_REQUEST.value(), IdentityErrorCode.STAFF_PASSWORD_REJECTED);

    // Keycloak mặc định chỉ trả 100 thành viên mỗi lần gọi.
    private static final int MEMBER_PAGE_SIZE = 1000;

    // Keycloak không có API đếm thành viên nên đếm là đọc hết danh sách. Khách tự đăng ký vào Customer không qua
    // service này nên số của nhóm đó có thể cũ tối đa bằng TTL; các nhóm khác được xoá cache ngay khi đổi thành viên.
    static final CacheRegion<Integer> MEMBER_COUNT =
            new CacheRegion<>("group-member-count", new TypeReference<>() {}, Duration.ofMinutes(5));

    private final KeycloakGroupFeignClient feign;
    private final RedisService redis;

    // Giữ id nội bộ của client fashion-api: nó cố định suốt đời realm nên tra một lần là đủ.
    private volatile String apiClientUuid;

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "listFallback")
    public List<KeycloakGroupRepresentation> listGroups() {
        return feign.listGroups();
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "groupFallback")
    public KeycloakGroupRepresentation getGroup(String groupId) {
        return call(() -> feign.getGroup(groupId), GROUP_ERRORS);
    }

    // Tạo không idempotent: retry sau timeout có thể tạo trùng / báo 409 giả, nên chỉ có breaker, không @Retry.
    @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "stringFallback")
    public String createGroup(String name) {
        return call(() -> idFromLocation(feign.createGroup(KeycloakGroupRepresentation.named(name))), GROUP_WRITE_ERRORS);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "voidFallback")
    public void renameGroup(String groupId, String name) {
        call(() -> {
            feign.updateGroup(groupId, KeycloakGroupRepresentation.named(name));
            return null;
        }, GROUP_WRITE_ERRORS);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "voidFallback")
    public void deleteGroup(String groupId) {
        call(() -> {
            feign.deleteGroup(groupId);
            return null;
        }, GROUP_ERRORS);
        redis.evict(MEMBER_COUNT, groupId);
    }

    /** Đưa quyền của nhóm về đúng {@code authorities}: POST phần thiếu, DELETE phần thừa. */
    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "voidFallback")
    public void setGroupPermissions(String groupId, Set<String> authorities) {
        call(() -> {
            syncPermissions(groupId, authorities);
            return null;
        }, GROUP_ERRORS);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "listFallback")
    public List<String> listMemberIds(String groupId) {
        return fetchMembers(groupId).stream().map(KeycloakUserBrief::id).toList();
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "countFallback")
    public int countMembers(String groupId) {
        return redis.getOrLoad(MEMBER_COUNT, () -> fetchMembers(groupId).size(), groupId);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "listFallback")
    public List<KeycloakUserBrief> listMembers(String groupId) {
        return fetchMembers(groupId);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "listFallback")
    public List<KeycloakUserBrief> searchUsers(String keyword, int first, int max) {
        return feign.searchUsers(keyword, first, max);
    }

    private List<KeycloakUserBrief> fetchMembers(String groupId) {
        List<KeycloakUserBrief> members = new ArrayList<>();
        List<KeycloakUserBrief> page;
        do {
            int first = members.size();
            page = call(() -> feign.listMembers(groupId, first, MEMBER_PAGE_SIZE), GROUP_ERRORS);
            members.addAll(page);
        } while (page.size() == MEMBER_PAGE_SIZE);
        return members;
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "listFallback")
    public List<KeycloakGroupRepresentation> listUserGroups(String userId) {
        return call(() -> feign.listUserGroups(userId), USER_ERRORS);
    }

    // 404 ở hai API dưới là "user hoặc nhóm không có": service gọi đã kiểm tra nhóm bằng getGroup nên coi là user.
    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "voidFallback")
    public void addUserToGroup(String userId, String groupId) {
        call(() -> {
            feign.addUserToGroup(userId, groupId);
            return null;
        }, USER_ERRORS);
        redis.evict(MEMBER_COUNT, groupId);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "voidFallback")
    public void removeUserFromGroup(String userId, String groupId) {
        call(() -> {
            feign.removeUserFromGroup(userId, groupId);
            return null;
        }, USER_ERRORS);
        redis.evict(MEMBER_COUNT, groupId);
    }

    @CircuitBreaker(name = "keycloakAdmin", fallbackMethod = "stringFallback")
    public String createUser(KeycloakNewUserRepresentation user) {
        return call(() -> idFromLocation(feign.createUser(user)), NEW_USER_ERRORS);
    }

    @CircuitBreaker(name = "keycloakAdmin")
    @Retry(name = "keycloakAdmin", fallbackMethod = "voidFallback")
    public void deleteUser(String userId) {
        call(() -> {
            feign.deleteUser(userId);
            return null;
        }, USER_ERRORS);
    }

    private void syncPermissions(String groupId, Set<String> authorities) {
        String clientUuid = apiClientUuid();
        Map<String, KeycloakRoleRepresentation> assignable = rolesByName(feign.listClientRoles(clientUuid));
        if (!assignable.keySet().containsAll(authorities)) {
            throw new AppException(IdentityErrorCode.PERMISSION_NOT_ASSIGNABLE);
        }
        List<KeycloakRoleRepresentation> current = feign.getGroupClientRoles(groupId, clientUuid);
        List<KeycloakRoleRepresentation> toAdd = authorities.stream()
                .filter(name -> current.stream().noneMatch(role -> role.name().equals(name)))
                .map(assignable::get)
                .toList();
        List<KeycloakRoleRepresentation> toRemove = current.stream()
                .filter(role -> !authorities.contains(role.name()))
                .toList();
        if (!toAdd.isEmpty()) {
            feign.addGroupClientRoles(groupId, clientUuid, toAdd);
        }
        if (!toRemove.isEmpty()) {
            feign.removeGroupClientRoles(groupId, clientUuid, toRemove);
        }
    }

    private String apiClientUuid() {
        String cached = apiClientUuid;
        if (cached != null) {
            return cached;
        }
        List<KeycloakIdRepresentation> clients = feign.findClients(API_CLIENT_ID);
        if (clients.isEmpty()) {
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
        apiClientUuid = clients.get(0).id();
        return apiClientUuid;
    }

    private static Map<String, KeycloakRoleRepresentation> rolesByName(List<KeycloakRoleRepresentation> roles) {
        return roles.stream().collect(Collectors.toMap(KeycloakRoleRepresentation::name, Function.identity()));
    }

    private static String idFromLocation(ResponseEntity<Void> response) {
        URI location = response.getHeaders().getLocation();
        if (location == null) {
            throw new AppException(ErrorCode.UPSTREAM_SERVICE_ERROR);
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private <T> T call(Supplier<T> request, Map<Integer, IdentityErrorCode> businessErrors) {
        try {
            return request.get();
        } catch (FeignException e) {
            IdentityErrorCode businessError = businessErrors.get(e.status());
            if (businessError == null) {
                throw e;
            }
            log.warn("[KeycloakGroup] Keycloak answered {} -> {}", e.status(), businessError);
            throw new AppException(businessError);
        }
    }

    private void voidFallback(Throwable ex) {
        throw failed(ex);
    }

    private String stringFallback(Throwable ex) {
        throw failed(ex);
    }

    private KeycloakGroupRepresentation groupFallback(Throwable ex) {
        throw failed(ex);
    }

    private List<?> listFallback(Throwable ex) {
        throw failed(ex);
    }

    private int countFallback(Throwable ex) {
        throw failed(ex);
    }

    private RuntimeException failed(Throwable ex) {
        log.error("[KeycloakGroup] request failed: {}", ex.getMessage());
        return UpstreamFailure.translate(ex);
    }
}
