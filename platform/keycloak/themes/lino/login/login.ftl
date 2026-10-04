<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
<#if section = "form">
<div class="lino-auth-wrapper">
    <!-- Left Banner -->
    <div class="lino-auth-banner login-banner">
        <img
            src="https://images.unsplash.com/photo-1665832102316-9fd8e8f77c88?auto=format&fit=crop&w=1600&q=80"
            alt="LINO fashion"
            class="lino-banner-img"
        />
        <div class="lino-banner-content">
            <p class="lino-banner-sub">Chào mừng trở lại</p>
            <h2 class="lino-banner-title">
                PHONG CÁCH TỐI GIẢN
            </h2>
        </div>
    </div>

    <!-- Right Form -->
    <div class="lino-auth-form-container">
        <div class="lino-auth-card">
        <a href="${(client.baseUrl)?has_content?then(client.baseUrl, 'http://localhost:8083/')}" id="lino-back-home-link" onclick="goBackToStorefront(event)" class="lino-back-home">
            &larr; Trang Chủ
        </a>

        <p class="lino-form-sub">Tài Khoản LINO</p>
        <h1 class="lino-form-title">ĐĂNG NHẬP</h1>
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <p class="lino-form-switch">
                Chưa có tài khoản? <a href="${url.registrationUrl}">Đăng ký ngay</a>
            </p>
        </#if>

        <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="lino-alert lino-alert-${message.type}">
                ${kcSanitize(message.summary)?no_esc}
            </div>
        </#if>

        <#if realm.password>
            <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" class="lino-form">
                <#if !usernameHidden??>
                    <div class="lino-field">
                        <label for="username" class="lino-label">Email</label>
                        <input
                            tabindex="1"
                            id="username"
                            class="lino-input"
                            name="username"
                            value="${(login.username!'')}"
                            type="text"
                            autofocus
                            autocomplete="username"
                            placeholder="email@example.com"
                            required
                        />
                    </div>
                </#if>

                <div class="lino-field">
                    <div class="lino-field-header">
                        <label for="password" class="lino-label">Mật Khẩu</label>
                        <#if realm.resetPasswordAllowed>
                            <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="lino-forgot-link">Quên mật khẩu?</a>
                        </#if>
                    </div>
                    <div class="lino-password-wrap">
                        <input
                            tabindex="2"
                            id="password"
                            class="lino-input"
                            name="password"
                            type="password"
                            autocomplete="current-password"
                            placeholder="••••••••"
                            required
                        />
                        <button type="button" class="lino-toggle-pw" onclick="togglePasswordVisibility('password', this)" aria-label="Ẩn hiện mật khẩu">
                            <svg class="eye-open" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"></path>
                                <circle cx="12" cy="12" r="3"></circle>
                            </svg>
                            <svg class="eye-closed" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:none;">
                                <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"></path>
                                <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"></path>
                                <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"></path>
                                <line x1="2" x2="22" y1="2" y2="22"></line>
                            </svg>
                        </button>
                    </div>
                </div>

                <#if realm.rememberMe && !usernameHidden??>
                    <label class="lino-checkbox-label compact">
                        <input
                            tabindex="3"
                            id="rememberMe"
                            name="rememberMe"
                            type="checkbox"
                            class="lino-checkbox"
                            <#if login.rememberMe??>checked</#if>
                        />
                        <span>Ghi nhớ đăng nhập</span>
                    </label>
                </#if>

                <button
                    tabindex="4"
                    class="lino-btn-submit"
                    name="login"
                    id="kc-login"
                    type="submit"
                >
                    Đăng Nhập
                </button>
            </form>
        </#if>

        <div class="lino-divider">
            <div class="lino-divider-line"></div>
            <span class="lino-divider-text">hoặc</span>
            <div class="lino-divider-line"></div>
        </div>

        <div class="lino-social-grid">
            <#if realm.identityProviders?? && realm.identityProviders?size gt 0>
                <#list realm.identityProviders as p>
                    <a href="${p.loginUrl}" class="lino-social-btn <#if p.alias == 'facebook'>facebook</#if>">
                        ${p.displayName}
                    </a>
                </#list>
            <#else>
                <button type="button" class="lino-social-btn" onclick="alert('Đăng nhập mạng xã hội chưa được kích hoạt trên hệ thống.')">
                    Google
                </button>
                <button type="button" class="lino-social-btn facebook" onclick="alert('Đăng nhập mạng xã hội chưa được kích hoạt trên hệ thống.')">
                    Facebook
                </button>
            </#if>
        </div>

        <p class="lino-ssl-note">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect width="18" height="11" x="3" y="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            Thông tin được bảo mật theo tiêu chuẩn SSL 256-bit.
        </p>
        </div>
    </div>
</div>

<script>
function getStorefrontUrl() {
    try {
        const urlParams = new URLSearchParams(window.location.search);
        const redirectUri = urlParams.get('redirect_uri');
        if (redirectUri) {
            const parsed = new URL(redirectUri);
            return parsed.origin + '/';
        }
    } catch (e) {}

    if (document.referrer && !document.referrer.includes(':8180')) {
        try {
            const refUrl = new URL(document.referrer);
            return refUrl.origin + '/';
        } catch (e) {}
    }

    return 'http://localhost:8083/';
}

function goBackToStorefront(e) {
    if (e) e.preventDefault();
    window.location.href = getStorefrontUrl();
}

document.addEventListener('DOMContentLoaded', function() {
    const homeLink = document.getElementById('lino-back-home-link');
    if (homeLink) homeLink.href = getStorefrontUrl();
});

function togglePasswordVisibility(fieldId, btn) {
    const input = document.getElementById(fieldId);
    if (!input) return;
    const isPw = input.type === 'password';
    input.type = isPw ? 'text' : 'password';
    const openIcon = btn.querySelector('.eye-open');
    const closedIcon = btn.querySelector('.eye-closed');
    if (openIcon && closedIcon) {
        openIcon.style.display = isPw ? 'none' : 'block';
        closedIcon.style.display = isPw ? 'block' : 'none';
    }
}
</script>
</#if>
</@layout.registrationLayout>
