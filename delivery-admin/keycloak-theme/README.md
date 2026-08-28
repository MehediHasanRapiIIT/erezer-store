# Erezer Keycloak Theme

Custom Keycloak login theme for the **Erezer** admin panel — minimal monochrome,
matching the storefront's `EREZER` wordmark and black/white/neutral palette.

## How to Install

1. Copy the `erezer` folder to your Keycloak themes directory:

   ```
   Copy: delivery-admin/keycloak-theme/erezer/
   To:   C:\keycloak\themes\erezer\
   ```

   Final structure:
   ```
   C:\keycloak\themes\
     └── erezer\
         └── login\
             ├── login.ftl
             ├── theme.properties
             └── resources\
                 └── css\
                     └── login.css
   ```

2. Restart Keycloak.

3. Apply the theme in the Keycloak Admin Console:
   - Open http://localhost:9090
   - Realm: **delivery-admin**
   - Realm Settings → Themes
   - Set **Login theme = erezer**
   - Save

## Design

- Two-panel layout: light form panel (left) + charcoal brand panel (right); on
  tablet/mobile the brand panel collapses and the card centres.
- Monochrome palette — near-black `#0c0a09`, white, warm neutral greys. No colour accent.
- `EREZER` wordmark (letter-spaced) instead of the old delivery-truck logo.
- White card with a thin border + soft shadow, Inter font, neutral inputs with a
  black focus ring, black "Sign in" button.
- Right panel lists the admin's real areas: catalog/variants, orders, customers, reports.

> Renamed from the legacy **amarbazaar** theme (green delivery branding). After
> installing, remember to switch the realm's Login theme to `erezer`.
