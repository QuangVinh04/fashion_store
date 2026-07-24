# Product Service

Owns product, category, and product variant data.

Stock is owned by inventory-service. Product variant APIs only expose catalog
metadata such as size, color, SKU, and price.

## Run locally

Start PostgreSQL dependencies from the repository root:

```powershell
docker compose up -d product-postgres
```

Run the service:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run
```

Default HTTP port: `8087`.

## Package layout

The service keeps product and category ownership together under one flat root
package. It does not split product and category into nested modules:

```text
com.fashionstore.product
|-- config
|-- controller
|-- dto
|-- entity
|-- mapper
|-- repository
|-- service
`-- ProductServiceApplication
```
