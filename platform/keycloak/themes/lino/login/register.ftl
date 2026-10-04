<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
<#if section = "form">
<div class="lino-auth-wrapper">
    <!-- Left Banner -->
    <div class="lino-auth-banner register-banner">
        <img
            src="https://images.unsplash.com/photo-1558769132-cb1aea458c5e?auto=format&fit=crop&w=1600&q=80"
            alt="LINO fashion"
            class="lino-banner-img"
        />
        <div class="lino-banner-content">
            <p class="lino-banner-sub">Tham gia LINO</p>
            <h2 class="lino-banner-title">
                ƯU ĐÃI THÀNH VIÊN
            </h2>
            <div class="lino-benefit-list">
                <div class="lino-benefit-item">
                    <div class="lino-benefit-badge">
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                    </div>
                    <span class="lino-benefit-text">Giảm 10% đơn hàng đầu tiên</span>
                </div>
                <div class="lino-benefit-item">
                    <div class="lino-benefit-badge">
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                    </div>
                    <span class="lino-benefit-text">Tích điểm mỗi lần mua</span>
                </div>
                <div class="lino-benefit-item">
                    <div class="lino-benefit-badge">
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                    </div>
                    <span class="lino-benefit-text">Ưu tiên xem hàng mới</span>
                </div>
            </div>
        </div>
    </div>

    <!-- Right Form -->
    <div class="lino-auth-form-container">
        <div class="lino-auth-card">
        <a href="${(client.baseUrl)?has_content?then(client.baseUrl, 'http://localhost:8083/')}" id="lino-back-home-link" onclick="goBackToStorefront(event)" class="lino-back-home">
            &larr; Trang Chủ
        </a>

        <p class="lino-form-sub">Tài Khoản LINO</p>
        <h1 class="lino-form-title">ĐĂNG KÝ</h1>
        <p class="lino-form-switch">
            Đã có tài khoản? <a href="${url.loginUrl}">Đăng nhập</a>
        </p>

        <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="lino-alert lino-alert-${message.type}">
                ${kcSanitize(message.summary)?no_esc}
            </div>
        </#if>

        <div id="js-form-error" class="lino-alert lino-alert-error" style="display: none;"></div>

        <form id="kc-register-form" action="${url.registrationAction}" method="post" class="lino-form" onsubmit="return validateRegisterForm(event)">
            <div class="lino-grid-2">
                <div class="lino-field">
                    <label for="firstName" class="lino-label">Họ <span class="lino-req">*</span></label>
                    <input
                        type="text"
                        id="firstName"
                        class="lino-input"
                        name="firstName"
                        value="${(register.formData.firstName!'')}"
                        placeholder="Nguyễn"
                        required
                    />
                </div>
                <div class="lino-field">
                    <label for="lastName" class="lino-label">Tên <span class="lino-req">*</span></label>
                    <input
                        type="text"
                        id="lastName"
                        class="lino-input"
                        name="lastName"
                        value="${(register.formData.lastName!'')}"
                        placeholder="Văn An"
                        required
                    />
                </div>
            </div>

            <div class="lino-field">
                <label for="email" class="lino-label">Email <span class="lino-req">*</span></label>
                <input
                    type="email"
                    id="email"
                    class="lino-input"
                    name="email"
                    value="${(register.formData.email!'')}"
                    autocomplete="email"
                    placeholder="email@example.com"
                    required
                />
            </div>

            <div class="lino-field">
                    <label for="password" class="lino-label">Mật Khẩu <span class="lino-req">*</span></label>
                    <div class="lino-password-wrap">
                        <input
                            type="password"
                            id="password"
                            class="lino-input"
                            name="password"
                            autocomplete="new-password"
                            placeholder="Tối thiểu 8 ký tự"
                            required
                            oninput="checkStrength(this.value)"
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

                    <div id="password-strength-container" style="display:none;">
                        <div class="lino-strength-bar">
                            <div id="str-seg-0" class="lino-strength-segment"></div>
                            <div id="str-seg-1" class="lino-strength-segment"></div>
                            <div id="str-seg-2" class="lino-strength-segment"></div>
                        </div>
                        <p class="lino-strength-text">Độ mạnh: <span id="str-label" class="lino-strength-val">Yếu</span></p>
                        <div class="lino-strength-checks">
                            <div id="check-len" class="lino-check-item">
                                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                <span>Ít nhất 8 ký tự</span>
                            </div>
                            <div id="check-upper" class="lino-check-item">
                                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                <span>Chứa chữ hoa</span>
                            </div>
                            <div id="check-num" class="lino-check-item">
                                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                <span>Chứa số</span>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="lino-field">
                    <label for="password-confirm" class="lino-label">Xác Nhận Mật Khẩu <span class="lino-req">*</span></label>
                    <div class="lino-password-wrap">
                        <input
                            type="password"
                            id="password-confirm"
                            class="lino-input"
                            name="password-confirm"
                            autocomplete="new-password"
                            placeholder="Nhập lại mật khẩu"
                            required
                        />
                        <button type="button" class="lino-toggle-pw" onclick="togglePasswordVisibility('password-confirm', this)" aria-label="Ẩn hiện mật khẩu">
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

            <div>
                <label class="lino-checkbox-label">
                    <input type="checkbox" id="terms-agree" class="lino-checkbox" required />
                    <span>
                        Tôi đồng ý với <a href="/policy" target="_blank">Điều Khoản Dịch Vụ</a> và <a href="/policy" target="_blank">Chính Sách Bảo Mật</a> của LINO.
                    </span>
                </label>
            </div>

            <button
                class="lino-btn-submit"
                name="register"
                id="kc-register"
                type="submit"
            >
                Tạo Tài Khoản
            </button>
        </form>

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

function checkStrength(pw) {
    const container = document.getElementById('password-strength-container');
    if (!container) return;
    if (!pw) {
        container.style.display = 'none';
        return;
    }
    container.style.display = 'block';

    const checkLen = pw.length >= 8;
    const checkUpper = /[A-Z]/.test(pw);
    const checkNum = /[0-9]/.test(pw);

    const lenEl = document.getElementById('check-len');
    const upperEl = document.getElementById('check-upper');
    const numEl = document.getElementById('check-num');

    if (lenEl) lenEl.classList.toggle('ok', checkLen);
    if (upperEl) upperEl.classList.toggle('ok', checkUpper);
    if (numEl) numEl.classList.toggle('ok', checkNum);

    const score = (checkLen ? 1 : 0) + (checkUpper ? 1 : 0) + (checkNum ? 1 : 0);
    const colors = ['#E5001B', '#FB923C', '#2D5A3D'];
    const labels = ['Yếu', 'Trung bình', 'Mạnh'];

    const seg0 = document.getElementById('str-seg-0');
    const seg1 = document.getElementById('str-seg-1');
    const seg2 = document.getElementById('str-seg-2');
    const labelEl = document.getElementById('str-label');

    const activeColor = score > 0 ? colors[score - 1] : '#e0e0e0';
    if (seg0) seg0.style.backgroundColor = score >= 1 ? activeColor : '#e0e0e0';
    if (seg1) seg1.style.backgroundColor = score >= 2 ? activeColor : '#e0e0e0';
    if (seg2) seg2.style.backgroundColor = score >= 3 ? activeColor : '#e0e0e0';

    if (labelEl) {
        labelEl.textContent = score > 0 ? labels[score - 1] : 'Yếu';
        labelEl.style.color = activeColor;
    }
}

function validateRegisterForm(e) {
    const errorBox = document.getElementById('js-form-error');
    const pw = document.getElementById('password')?.value;
    const confirm = document.getElementById('password-confirm')?.value;
    const terms = document.getElementById('terms-agree')?.checked;

    if (pw && confirm && pw !== confirm) {
        e.preventDefault();
        if (errorBox) {
            errorBox.textContent = 'Mật khẩu xác nhận không khớp.';
            errorBox.style.display = 'block';
        }
        return false;
    }

    if (!terms) {
        e.preventDefault();
        if (errorBox) {
            errorBox.textContent = 'Vui lòng đồng ý với Điều Khoản Dịch Vụ và Chính Sách Bảo Mật.';
            errorBox.style.display = 'block';
        }
        return false;
    }

    if (errorBox) errorBox.style.display = 'none';
    const btn = document.getElementById('kc-register');
    if (btn) btn.disabled = true;
    return true;
}
</script>
</#if>
</@layout.registrationLayout>
