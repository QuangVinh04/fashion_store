package com.fashionstore.contracts.notification;

import java.util.Map;

public record EmailNotificationRequested(
        String recipient,
        String template,
        Map<String, String> variables
) {
}
