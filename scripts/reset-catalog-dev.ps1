param([switch]$ResetCatalogData)

$ErrorActionPreference = 'Stop'
if (-not $ResetCatalogData) {
    throw 'Local/dev only. Pass -ResetCatalogData to erase the catalog public schema and adopt the squashed migration.'
}

$catalogWorkspace = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $catalogWorkspace
try {
    # Docker context overrides DOCKER_HOST; resolve the same effective endpoint as Docker CLI.
    if ($env:DOCKER_CONTEXT) {
        $catalogDockerEndpoint = docker context inspect $env:DOCKER_CONTEXT --format '{{.Endpoints.docker.Host}}'
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the selected Docker context.' }
    } elseif ($env:DOCKER_HOST) {
        $catalogDockerEndpoint = $env:DOCKER_HOST
    } else {
        $catalogDockerEndpoint = docker context inspect --format '{{.Endpoints.docker.Host}}'
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the current Docker context.' }
    }
    if (@($catalogDockerEndpoint).Count -ne 1 -or
        $catalogDockerEndpoint -notmatch '^(npipe:/{2,4}\./pipe/[^\s]+|unix:///[^\s]+)$') {
        throw 'Refusing reset: Docker must use a local named pipe or Unix socket; remote/TCP contexts are not allowed.'
    }

    $catalogContainerId = docker compose ps -q catalog-postgres
    if ($LASTEXITCODE -ne 0 -or -not $catalogContainerId) {
        throw 'The local Compose catalog-postgres service is not running. Start it before resetting catalog.'
    }
    $catalogMetadata = docker inspect $catalogContainerId | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or @($catalogMetadata).Count -ne 1 -or
        $catalogMetadata.Config.Labels.'com.docker.compose.service' -ne 'catalog-postgres') {
        throw 'Refusing reset: the resolved container is not the local catalog-postgres Compose service.'
    }
    $catalogDatabaseSetting = @($catalogMetadata.Config.Env | Where-Object { $_ -like 'POSTGRES_DB=*' })
    if ($catalogDatabaseSetting.Count -ne 1 -or $catalogDatabaseSetting[0] -ne 'POSTGRES_DB=catalog_database') {
        throw 'Refusing reset: the container database must be exactly catalog_database.'
    }

    # Only this container's configured catalog_database public schema is reset.
    docker exec $catalogContainerId sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'
    if ($LASTEXITCODE -ne 0) { throw 'Catalog schema reset failed.' }
    Write-Host 'Catalog schema reset. Start catalog-service to apply V1 and validate its mappings.'
} finally {
    Pop-Location
}
