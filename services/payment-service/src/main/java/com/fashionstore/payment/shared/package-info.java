/**
 * Shared kernel of payment-service: outbox, Rabbit topology, security, OpenAPI, admin paging.
 * Open so every feature module may use its internals; it must not depend on any feature module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.fashionstore.payment.shared;

import org.springframework.modulith.ApplicationModule;
