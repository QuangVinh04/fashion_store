# Cart Service

Owns user carts and cart items. Product and inventory data are accessed through
HTTP clients and stored only as cart item snapshots.

## Package layout

```text
com.fashionstore.cart
|-- client
|-- config
|-- controller
|-- dto
|-- entity
|-- event
|-- repository
|-- service
`-- CartServiceApplication
```

The service uses a flat layer-based structure and does not contain nested
domain modules.
