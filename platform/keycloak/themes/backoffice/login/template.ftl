<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false showAnotherWayIfPresent=true>
<!DOCTYPE html>
<html lang="${locale.currentLanguageTag!'vi'}">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("loginTitle",(realm.displayName!'LINO Backoffice'))}</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
    <link href="${url.resourcesPath}/css/backoffice.css" rel="stylesheet" />
</head>
<body class="backoffice-body ${bodyClass!}">
    <img src="${url.resourcesPath}/img/tree-1.png" alt="illustration-left" class="bo-tree-left" />
    <img src="${url.resourcesPath}/img/auth-v1-mask-light.png" alt="illustration-mask" class="bo-mask-bg" />
    <img src="${url.resourcesPath}/img/tree-2.png" alt="illustration-right" class="bo-tree-right" />

    <#nested "content">
    <#nested "form">
</body>
</html>
</#macro>
