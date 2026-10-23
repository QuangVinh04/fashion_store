/**
 * Shared kernel of order-service: outbox, Rabbit topology, security, OpenAPI, Catalog/Identity clients, error codes.
 * Open so every feature module may use its internals; it must not depend on any feature module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.fashionstore.order.shared;

import org.springframework.modulith.ApplicationModule;
