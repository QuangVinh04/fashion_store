<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
<#if section = "form">
<div class="bo-card">
    <div class="bo-brand">
        <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path fill-rule="evenodd" clip-rule="evenodd" d="M16 2L2 9L16 16L30 9L16 2Z" fill="currentColor"/>
            <path fill-rule="evenodd" clip-rule="evenodd" d="M2 23L16 30L30 23V16L16 23L2 16V23Z" fill="currentColor" fill-opacity="0.8"/>
        </svg>
        <span class="bo-brand-name">LINO Backoffice</span>
    </div>

    <h1 class="bo-title">Chào mừng trở lại! 👋🏻</h1>
    <p class="bo-subtitle">Vui lòng đăng nhập tài khoản quản trị viên</p>

    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <div class="bo-alert bo-alert-${message.type}">
            ${kcSanitize(message.summary)?no_esc}
        </div>
    </#if>

    <#if realm.password>
        <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post" class="bo-form">
            <#if !usernameHidden??>
                <div class="bo-field">
                    <label for="username" class="bo-label">Email quản trị</label>
                    <input
                        tabindex="1"
                        id="username"
                        class="bo-input"
                        name="username"
                        value="${(login.username!'')}"
                        type="text"
                        autofocus
                        autocomplete="username"
                        placeholder="admin@fashion.local"
                        required
                    />
                </div>
            </#if>

            <div class="bo-field">
                <label for="password" class="bo-label">Mật khẩu</label>
                <div class="bo-password-wrap">
                    <input
                        tabindex="2"
                        id="password"
                        class="bo-input"
                        name="password"
                        type="password"
                        autocomplete="current-password"
                        placeholder="••••••••"
                        required
                    />
                    <button type="button" class="bo-toggle-pw" onclick="togglePasswordVisibility('password', this)" aria-label="Ẩn hiện mật khẩu">
                        <svg class="eye-open" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"></path>
                            <circle cx="12" cy="12" r="3"></circle>
                        </svg>
                        <svg class="eye-closed" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="display:none;">
                            <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"></path>
                            <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"></path>
                            <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"></path>
                            <line x1="2" x2="22" y1="2" y2="22"></line>
                        </svg>
                    </button>
                </div>
            </div>

            <div class="bo-options-row">
                <#if realm.rememberMe && !usernameHidden??>
                    <label class="bo-checkbox-label">
                        <input
                            tabindex="3"
                            id="rememberMe"
                            name="rememberMe"
                            type="checkbox"
                            class="bo-checkbox"
                            <#if login.rememberMe??>checked</#if>
                        />
                        <span>Ghi nhớ đăng nhập</span>
                    </label>
                <#else>
                    <div></div>
                </#if>

                <#if realm.resetPasswordAllowed>
                    <a tabindex="5" href="${url.loginResetCredentialsUrl}" class="bo-link">Quên mật khẩu?</a>
                </#if>
            </div>

            <button
                tabindex="4"
                class="bo-btn-submit"
                name="login"
                id="kc-login"
                type="submit"
            >
                Đăng Nhập
            </button>
        </form>
    </#if>

    <p class="bo-footer-note">
        Khu vực quản trị nội bộ. Chỉ tài khoản được cấp quyền mới có thể truy cập.
    </p>
</div>

<script>
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
