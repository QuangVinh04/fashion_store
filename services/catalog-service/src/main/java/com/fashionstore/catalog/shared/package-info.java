/**
 * Shared kernel of catalog-service: outbox, Rabbit topology, security, OpenAPI, utilities.
 * Open so every feature module may use its internals; it must not depend on any feature module.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN)
package com.fashionstore.catalog.shared;

import org.springframework.modulith.ApplicationModule;
