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

    <h1 class="bo-title">Đã xảy ra lỗi ⚠️</h1>

    <#if message?has_content>
        <div class="bo-alert bo-alert-error" style="margin-top: 16px;">
            ${message.summary}
        </div>
    </#if>

    <#if (client.baseUrl)?has_content>
        <div style="text-align: center; margin-top: 24px;">
            <a href="${client.baseUrl}" class="bo-btn-submit" style="display: inline-block; text-align: center; text-decoration: none; line-height: 44px;">
                Quay lại ứng dụng
            </a>
        </div>
    <#else>
        <div style="text-align: center; margin-top: 24px;">
            <a href="${url.loginUrl}" class="bo-btn-submit" style="display: inline-block; text-align: center; text-decoration: none; line-height: 44px;">
                Quay lại đăng nhập
            </a>
        </div>
    </#if>
</div>
</#if>
</@layout.registrationLayout>
