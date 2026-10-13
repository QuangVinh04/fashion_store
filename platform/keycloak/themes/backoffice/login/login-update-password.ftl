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

    <h1 class="bo-title">Đổi mật khẩu 🔑</h1>
    <p class="bo-subtitle">Vui lòng thiết lập mật khẩu mới cho tài khoản của bạn</p>

    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
        <div class="bo-alert bo-alert-${message.type}">
            ${kcSanitize(message.summary)?no_esc}
        </div>
    </#if>

    <form id="kc-passwd-update-form" action="${url.loginAction}" method="post" class="bo-form">
        <div class="bo-field">
            <label for="password-new" class="bo-label">Mật khẩu mới</label>
            <input
                type="password"
                id="password-new"
                name="password-new"
                class="bo-input"
                autofocus
                autocomplete="new-password"
                placeholder="••••••••"
                required
            />
        </div>

        <div class="bo-field">
            <label for="password-confirm" class="bo-label">Xác nhận mật khẩu mới</label>
            <input
                type="password"
                id="password-confirm"
                name="password-confirm"
                class="bo-input"
                autocomplete="new-password"
                placeholder="••••••••"
                required
            />
        </div>

        <button
            class="bo-btn-submit"
            type="submit"
        >
            Cập Nhật Mật Khẩu
        </button>
    </form>
</div>
</#if>
</@layout.registrationLayout>
