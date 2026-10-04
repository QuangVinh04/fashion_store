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
            <p class="lino-banner-sub">Hỗ trợ tài khoản</p>
            <h2 class="lino-banner-title">
                KHÔI PHỤC MẬT KHẨU
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
        <h1 class="lino-form-title">QUÊN MẬT KHẨU</h1>
        <p class="lino-form-switch">
            Nhớ mật khẩu? <a href="${url.loginUrl}">Đăng nhập ngay</a>
        </p>

        <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="lino-alert lino-alert-${message.type}">
                ${kcSanitize(message.summary)?no_esc}
            </div>
        </#if>

        <form id="kc-reset-password-form" action="${url.loginAction}" method="post" class="lino-form">
            <div class="lino-field">
                <label for="username" class="lino-label">Email tài khoản <span class="lino-req">*</span></label>
                <input
                    type="text"
                    id="username"
                    name="username"
                    class="lino-input"
                    autofocus
                    value="${(auth.attemptedUsername!'')}"
                    placeholder="email@example.com"
                    required
                />
            </div>

            <button
                class="lino-btn-submit"
                type="submit"
            >
                Gửi Hướng Dẫn Đặt Lại Mật Khẩu
            </button>
        </form>

        <p class="lino-ssl-note">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect width="18" height="11" x="3" y="11" rx="2" ry="2"></rect>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
            </svg>
            Liên kết đặt lại mật khẩu sẽ được gửi an toàn tới email của bạn.
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
</script>
</#if>
</@layout.registrationLayout>
