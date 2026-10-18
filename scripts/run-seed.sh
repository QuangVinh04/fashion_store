#!/usr/bin/env bash
# scripts/run-seed.sh
# Script tự động thực thi các file SQL seed vào 5 PostgreSQL container tương ứng (Linux/macOS/Git Bash)
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_DIR="$DIR/seed"

echo "=========================================================="
echo "  FASHION STORE - BỘ NẠP DỮ LIỆU SEED CHO TOÀN HỆ THỐNG   "
echo "=========================================================="

if ! docker info >/dev/null 2>&1; then
    echo ""
    echo "[LỖI] Docker Desktop chưa được bật hoặc chưa khởi động xong!"
    echo "Vui lòng mở ứng dụng Docker Desktop trên Windows và đợi trạng thái Engine running."
    echo ""
    exit 1
fi

echo "-> [1/5] Nạp Identity Service..."
docker compose exec -T identity-postgres psql -U root -d identity_database < "$SEED_DIR/01-identity-seed.sql"

echo "-> [2/5] Nạp Catalog Service..."
docker compose exec -T catalog-postgres psql -U root -d catalog_database < "$SEED_DIR/02-catalog-seed.sql"

echo "-> [3/5] Nạp Order Service..."
docker compose exec -T order-postgres psql -U root -d order_database < "$SEED_DIR/03-order-seed.sql"

echo "-> [4/5] Nạp Payment Service..."
docker compose exec -T payment-postgres psql -U root -d payment_database < "$SEED_DIR/04-payment-seed.sql"

echo "-> [5/5] Nạp Notification Service..."
docker compose exec -T notification-postgres psql -U root -d notification_database < "$SEED_DIR/05-notification-seed.sql"

if [ -f "$SEED_DIR/keycloak-users.json" ]; then
    echo "-> [6/6] Nạp 10 tài khoản vào Keycloak realm fashion-store..."
    docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin 2>/dev/null || true
    docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create partialImport -r fashion-store -f - < "$SEED_DIR/keycloak-users.json"
fi

echo "=========================================================="
echo "  HOÀN TẤT NẠP DỮ LIỆU SEED CHO TOÀN HỆ THỐNG!"
echo "  Tất cả 5 database và Keycloak đều đã được nạp dữ liệu đầy đủ."
echo "=========================================================="
