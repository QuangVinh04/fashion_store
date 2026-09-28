
  # Website bán quần áo (Community)

  This is a code bundle for Website bán quần áo (Community). The original project is available at https://www.figma.com/design/K0BEh1kH6VgPskqQcNBxAg/Website-b%C3%A1n-qu%E1%BA%A7n-%C3%A1o--Community-.

  ## Running the code

  Run `npm i` to install the dependencies.

  Run `npm run dev` to start the development server.

  ## Storefront integration

  The customer UI now uses the existing storefront BFF and backend APIs. Start the platform (including Keycloak, gateway, catalog, identity, order, payment and storefront-bff), then open `http://localhost:8083`. The BFF serves/proxies the Vite application on port 3000 in development. Opening port 3000 directly bypasses the BFF session and API proxy.

  Run `npm run build` to type-check and build the UI. The BFF handles the Keycloak session; the SPA never stores access tokens. API mutations use the BFF CSRF cookie. Login and registration use Keycloak, and logout performs a full-page OIDC redirect.

  Configure `GHN_TOKEN` and `GHN_SHOP_ID` on order-service for province/district/ward selection and shipping quotes. The current order/shipping contract uses GHN's legacy district/ward catalogue; migrating to the current two-level province/ward model requires a coordinated backend contract change. Configure the payment providers and media storage for live checkout and uploads. A new Keycloak realm import uses the `lino` login theme; for an already-imported realm, select `lino` as its login theme in Keycloak administration.

  Full end-to-end checkout and payment return testing requires the Docker stack and sandbox credentials. The UI shows API errors when a backend integration is unavailable instead of substituting fake data.
