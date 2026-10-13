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

    <h1 class="bo-title">Quên mật khẩu? 🔒</h1>
    <p class="bo-subtitle">Nhập email tài khoản để nhận liên kết khôi phục mật khẩu</p>

    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <div class="bo-alert bo-alert-${message.type}">
            ${kcSanitize(message.summary)?no_esc}
        </div>
    </#if>

    <form id="kc-reset-password-form" action="${url.loginAction}" method="post" class="bo-form">
        <div class="bo-field">
            <label for="username" class="bo-label">Email quản trị</label>
            <input
                type="text"
                id="username"
                name="username"
                class="bo-input"
                autofocus
                value="${(auth.attemptedUsername!'')}"
                placeholder="admin@fashion.local"
                required
            />
        </div>

        <button
            class="bo-btn-submit"
            type="submit"
        >
            Gửi Liên Kết Khôi Phục
        </button>

        <div style="text-align: center; margin-top: 16px;">
            <a href="${url.loginUrl}" class="bo-link" style="display: inline-flex; align-items: center; gap: 6px;">
                <span>&larr;</span> Quay lại đăng nhập
            </a>
        </div>
    </form>
</div>
</#if>
</@layout.registrationLayout>
