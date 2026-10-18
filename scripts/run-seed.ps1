# scripts/run-seed.ps1
# Script tu dong thuc thi cac file SQL seed vao 5 PostgreSQL container tuong ung
param (
    [switch]$Force
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  FASHION STORE - BO NAP DU LIEU SEED CHO TOAN HE THONG   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$SeedDir = Join-Path $PSScriptRoot "seed"

$Databases = @(
    @{
        Service = "identity-postgres"
        Database = "identity_database"
        File = "01-identity-seed.sql"
        Port = 5434
        Label = "1/5: Identity Service (Users, Roles, Addresses chuan GHN)"
    },
    @{
        Service = "catalog-postgres"
        Database = "catalog_database"
        File = "02-catalog-seed.sql"
        Port = 5433
        Label = "2/5: Catalog Service (Brands, Categories, Products, Images, Stock)"
    },
    @{
        Service = "order-postgres"
        Database = "order_database"
        File = "03-order-seed.sql"
        Port = 5435
        Label = "3/5: Order Service (Carts, Orders, Shipments, Returns, Promos)"
    },
    @{
        Service = "payment-postgres"
        Database = "payment_database"
        File = "04-payment-seed.sql"
        Port = 5436
        Label = "4/5: Payment Service (Payments, Refunds, Wallets, Ledgers)"
    },
    @{
        Service = "notification-postgres"
        Database = "notification_database"
        File = "05-notification-seed.sql"
        Port = 5437
        Label = "5/5: Notification Service (In-App Notifications)"
    }
)

# Kiem tra Docker daemon co dang chay khong
$serverVersion = $null
try {
    $serverVersion = docker version --format "{{.Server.Version}}" 2>$null
} catch {
    $serverVersion = $null
}

if (-not $serverVersion) {
    Write-Host "`n[LOI] Docker Desktop chua duoc bat hoac chua khoi dong xong!" -ForegroundColor Red
    Write-Host "Vui long mo ung dung Docker Desktop tren Windows, doi bieu tuong Docker chuyen sang mau xanh (Engine running) roi thu lai.`n" -ForegroundColor Yellow
    exit 1
}

$hasError = $false

foreach ($db in $Databases) {
    $FilePath = Join-Path $SeedDir $db.File
    if (-not (Test-Path $FilePath)) {
        Write-Warning "Khong tim thay file: $FilePath"
        continue
    }

    Write-Host "`nDang nap: $($db.Label)..." -ForegroundColor Yellow
    Write-Host "  Container: $($db.Service) | DB: $($db.Database) | Port: $($db.Port)" -ForegroundColor DarkGray

    # Copy file truc tiep vao container de dam bao 100% UTF-8 khong bi loi font tieng Viet qua pipe
    docker compose cp $FilePath "$($db.Service):/tmp/$($db.File)" 2>$null | Out-Null
    docker compose exec -T $db.Service psql -U root -d $db.Database -f "/tmp/$($db.File)"
    if ($LASTEXITCODE -eq 0) {
        docker compose exec -T $db.Service rm -f "/tmp/$($db.File)" 2>$null | Out-Null
        Write-Host "  -> [THANH CONG] Da nap $($db.File)" -ForegroundColor Green
    }
    else {
        docker compose exec -T $db.Service rm -f "/tmp/$($db.File)" 2>$null | Out-Null
        Write-Host "  -> [THAT BAI] Loi khi nap $($db.File) (Exit code: $LASTEXITCODE)" -ForegroundColor Red
        $hasError = $true
    }
}

# Nap 10 user vao Keycloak realm fashion-store
$KeycloakFile = Join-Path $SeedDir "keycloak-users.json"
if (Test-Path $KeycloakFile) {
    Write-Host "`nDang nap: 6/6: Keycloak Realm (10 tai khoan khach hang)..." -ForegroundColor Yellow
    docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 --realm master --user admin --password admin 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Get-Content $KeycloakFile -Raw -Encoding UTF8 | docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh create partialImport -r fashion-store -f - 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "  -> [THANH CONG] Da nap 10 user vao Keycloak realm fashion-store" -ForegroundColor Green
        }
        else {
            Write-Host "  -> [THAT BAI] Loi khi import user vao Keycloak (Exit code: $LASTEXITCODE)" -ForegroundColor Red
            $hasError = $true
        }
    }
    else {
        Write-Host "  -> [THAT BAI] Khong the ket noi Keycloak admin CLI (Container co dang chay khong?)" -ForegroundColor Red
        $hasError = $true
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
if (-not $hasError) {
    Write-Host "  HOAN TAT NAP DU LIEU SEED CHO TOAN BO HE THONG!" -ForegroundColor Green
    Write-Host "  Tat ca 5 co so du lieu va Keycloak deu da co du lieu day du." -ForegroundColor Green
}
else {
    Write-Host "  CO LOI XAY RA TRONG QUA TRINH NAP DU LIEU!" -ForegroundColor Red
    Write-Host "  Vui long kiem tra cac service containers va thu lai." -ForegroundColor Yellow
}
Write-Host "==========================================================" -ForegroundColor Cyan
