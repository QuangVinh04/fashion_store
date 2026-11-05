#!/bin/bash
# Cấu hình xác thực mà realm import không làm được, chạy sau mỗi lần Keycloak khởi động (service keycloak-init).
# Idempotent. Áp dụng cho cả realm mới import lẫn realm đã import từ trước (import chỉ chạy một lần).
#
#  - Flow `backoffice-browser`: mật khẩu + OTP bắt buộc, gán làm browser flow của client backoffice-bff.
#    Import tạo client trước flow nên authenticationFlowBindingOverrides trong realm JSON bị bỏ qua im lặng.
#  - Events, password policy, event listener: realm cũ (import trước khi có) cũng nhận.
set -euo pipefail

REALM=fashion-store
FLOW=backoffice-browser
KCADM=/opt/keycloak/bin/kcadm.sh

$KCADM config credentials --server "${KEYCLOAK_URL:-http://keycloak:8080}" --realm master \
    --user "$KEYCLOAK_ADMIN_USERNAME" --password "$KEYCLOAK_ADMIN_PASSWORD"

# Giữ khớp với realm-fashion-store.json
$KCADM update "realms/$REALM" \
    -s 'passwordPolicy=length(8) and upperCase(1) and lowerCase(1) and digits(1) and notUsername and notEmail and passwordHistory(3)' \
    -s eventsEnabled=true -s eventsExpiration=2592000 -s adminEventsEnabled=true \
    -s "eventsListeners=${KEYCLOAK_EVENT_LISTENERS:-[\"jboss-logging\",\"fashion-rabbit\"]}"

flow_id() {
    $KCADM get authentication/flows -r "$REALM" --fields id,alias --format csv --noquotes \
        | grep ",$FLOW\$" | cut -d, -f1 || true
}

if [ -z "$(flow_id)" ]; then
    echo "Creating flow $FLOW"
    $KCADM create authentication/flows -r "$REALM" -s alias="$FLOW" -s providerId=basic-flow \
        -s topLevel=true -s builtIn=false \
        -s 'description=Backoffice: mật khẩu + OTP bắt buộc, không SSO cookie (phiên storefront không bỏ qua được OTP)'
    for provider in auth-username-password-form auth-otp-form; do
        $KCADM create "authentication/flows/$FLOW/executions/execution" -r "$REALM" -s provider="$provider"
    done
fi

# Execution mới tạo là DISABLED; luôn đưa về REQUIRED (sửa cả khi ai đó đổi trong admin console)
for execution in $($KCADM get "authentication/flows/$FLOW/executions" -r "$REALM" --fields id --format csv --noquotes); do
    $KCADM update "authentication/flows/$FLOW/executions" -r "$REALM" -b "{\"id\":\"$execution\",\"requirement\":\"REQUIRED\"}"
done

client_id=$($KCADM get clients -r "$REALM" -q clientId=backoffice-bff --fields id --format csv --noquotes)
$KCADM update "clients/$client_id" -r "$REALM" -s "authenticationFlowBindingOverrides.browser=$(flow_id)"

echo "Realm auth sync done"
