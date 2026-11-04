# Syncs fashion-api permissions and default groups from realm-fashion-store.json into a running Keycloak
# whose realm was imported before the RBAC groups existed (and again whenever code adds a permission).
# Idempotent. See platform/keycloak/README.md.
param(
    [string]$AdminUser = 'admin',
    [string]$AdminPassword = 'admin'
)

$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$ErrorActionPreference = 'Stop'

$RealmName = 'fashion-store'
$ApiClientId = 'fashion-api'
$AdminGroupName = 'Admin'
$CustomerGroupName = 'Customer'
$AdminRealmRole = 'ADMIN'
$IdentityServiceAccount = 'service-account-identity-service'
$RealmFile = Join-Path $PSScriptRoot '..\..\platform\keycloak\realm-fashion-store.json'

function Read-RbacDesiredState {
    param([string]$Path = $RealmFile)
    $realm = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
    [pscustomobject]@{
        Permissions = @($realm.roles.client.$ApiClientId)
        Groups      = @($realm.groups)
    }
}

# Group names are never passed as command-line arguments (Vietnamese would be mangled on PS 5.1):
# bodies go through stdin, existing groups are addressed by id.
function Invoke-Kcadm {
    param([string[]]$KcadmArguments, [string]$Body)
    $command = @('compose', 'exec', '-T', 'keycloak', '/opt/keycloak/bin/kcadm.sh') + $KcadmArguments
    if ($Body) {
        $output = $Body | & docker @command
    } else {
        $output = & docker @command
    }
    if ($LASTEXITCODE -ne 0) {
        throw "kcadm failed (exit $LASTEXITCODE): $($KcadmArguments -join ' ')"
    }
    $output
}

function Get-KcadmJson {
    param([string[]]$KcadmArguments)
    $text = (Invoke-Kcadm $KcadmArguments) -join "`n"
    if (-not $text.Trim()) {
        return @()
    }
    # PS 5.1 emits a JSON array as one object, so enumerate explicitly.
    $parsed = $text | ConvertFrom-Json
    @(foreach ($item in $parsed) { $item })
}

function Find-GroupId {
    param([string]$Name)
    $group = Get-KcadmJson @('get', 'groups', '-r', $RealmName, '--fields', 'id,name', '-q', 'max=1000') |
        Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if ($group) { $group.id }
}

function New-Group {
    param($Group)
    $body = @{ name = $Group.name }
    if ($Group.attributes) {
        $body.attributes = $Group.attributes
    }
    $groupId = (Invoke-Kcadm @('create', 'groups', '-r', $RealmName, '-i', '-f', '-') ($body | ConvertTo-Json -Depth 5 -Compress)) -join ''
    $groupId.Trim()
}

function Add-GroupRoles {
    param([string]$GroupId, $Group)
    $realmRoles = @($Group.realmRoles | Where-Object { $_ })
    if ($realmRoles.Count -gt 0) {
        $arguments = @('add-roles', '-r', $RealmName, '--gid', $GroupId)
        foreach ($role in $realmRoles) { $arguments += @('--rolename', $role) }
        Invoke-Kcadm $arguments | Out-Null
    }
    $clientRoles = @($Group.clientRoles.$ApiClientId | Where-Object { $_ })
    if ($clientRoles.Count -gt 0) {
        $arguments = @('add-roles', '-r', $RealmName, '--gid', $GroupId, '--cclientid', $ApiClientId)
        foreach ($role in $clientRoles) { $arguments += @('--rolename', $role) }
        Invoke-Kcadm $arguments | Out-Null
    }
}

function Add-UserToGroup {
    param([string]$UserId, [string]$GroupId)
    Invoke-Kcadm @('update', "users/$UserId/groups/$GroupId", '-r', $RealmName,
        '-s', "realm=$RealmName", '-s', "userId=$UserId", '-s', "groupId=$GroupId", '-n') | Out-Null
}

function Sync-PermissionRoles {
    param($State, [string]$ApiClientUuid)
    $existing = Get-KcadmJson @('get', "clients/$ApiClientUuid/roles", '-r', $RealmName, '--fields', 'name', '-q', 'max=1000') |
        ForEach-Object { $_.name }
    foreach ($permission in $State.Permissions) {
        if ($existing -contains $permission.name) { continue }
        Write-Host "Creating client role $($permission.name)"
        $body = @{ name = $permission.name; description = $permission.description } | ConvertTo-Json -Compress
        Invoke-Kcadm @('create', "clients/$ApiClientUuid/roles", '-r', $RealmName, '-f', '-') $body | Out-Null
    }
}

# Admin (the rerun marker) is created LAST so a failure midway leaves it absent and a rerun completes the work.
# Staff groups are created together with Admin only: once Admin exists, a staff group an admin deleted stays deleted.
function Sync-AdminGroups {
    param($State)
    $adminGroup = $State.Groups | Where-Object { $_.name -eq $AdminGroupName }
    $adminGroupId = Find-GroupId $AdminGroupName
    if (-not $adminGroupId) {
        Write-Host 'Creating staff groups and Admin'
        $adminMembers = Get-KcadmJson @('get', "roles/$AdminRealmRole/users", '-r', $RealmName, '--fields', 'id', '-q', 'max=1000')
        foreach ($group in $State.Groups | Where-Object { $_.name -ne $CustomerGroupName -and $_.name -ne $AdminGroupName }) {
            $groupId = Find-GroupId $group.name
            if (-not $groupId) { $groupId = New-Group $group }
            Add-GroupRoles $groupId $group
        }
        $adminGroupId = New-Group $adminGroup
        foreach ($member in $adminMembers) { Add-UserToGroup $member.id $adminGroupId }
    }
    Add-GroupRoles $adminGroupId $adminGroup
}

# Being a default group is the completion marker, set last, so a rerun finishes a half-created Customer.
# Existing users are not backfilled into Customer.
function Sync-CustomerGroup {
    param($State)
    $group = $State.Groups | Where-Object { $_.name -eq $CustomerGroupName }
    $groupId = Find-GroupId $CustomerGroupName
    if ($groupId -and (Test-DefaultGroup $groupId)) { return }
    Write-Host 'Creating Customer group'
    if (-not $groupId) { $groupId = New-Group $group }
    Add-GroupRoles $groupId $group
    Invoke-Kcadm @('update', "default-groups/$groupId", '-r', $RealmName,
        '-s', "realm=$RealmName", '-s', "groupId=$groupId", '-n') | Out-Null
}

function Test-DefaultGroup {
    param([string]$GroupId)
    $defaults = Get-KcadmJson @('get', 'default-groups', '-r', $RealmName, '--fields', 'id')
    [bool]($defaults | Where-Object { $_.id -eq $GroupId })
}

function Sync-IdentityServiceAccount {
    Invoke-Kcadm @('add-roles', '-r', $RealmName, '--uusername', $IdentityServiceAccount,
        '--cclientid', 'realm-management', '--rolename', 'view-clients') | Out-Null
}

function Invoke-RbacSync {
    $state = Read-RbacDesiredState
    Set-Location (Join-Path $PSScriptRoot '..\..')
    Invoke-Kcadm @('config', 'credentials', '--server', 'http://localhost:8080', '--realm', 'master',
        '--user', $AdminUser, '--password', $AdminPassword) | Out-Null

    $apiClient = Get-KcadmJson @('get', 'clients', '-r', $RealmName, '-q', "clientId=$ApiClientId", '--fields', 'id') |
        Select-Object -First 1
    if (-not $apiClient) {
        throw "Client '$ApiClientId' not found in realm '$RealmName'. Is the realm imported?"
    }
    Sync-PermissionRoles $state $apiClient.id
    Sync-AdminGroups $state
    Sync-CustomerGroup $state
    Sync-IdentityServiceAccount
    Write-Host 'RBAC sync done'
}

# Dot-sourcing (tests) only loads the functions.
if ($MyInvocation.InvocationName -ne '.') {
    Invoke-RbacSync
}
