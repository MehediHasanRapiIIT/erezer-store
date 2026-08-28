// Development defaults for `ng serve`.
//
// In a container this file is REPLACED at startup by docker-entrypoint.sh with
// values taken from the environment, so do not put anything secret or
// environment-specific here - only sane localhost defaults.
window.__ENV__ = {
  API_BASE_URL: 'http://localhost:8080',
  KEYCLOAK_URL: 'http://localhost:9090',
  KEYCLOAK_REALM: 'delivery-admin',
  KEYCLOAK_CLIENT_ID: 'delivery-admin-ui',
  SENTRY_DSN: '',
  SENTRY_ENV: 'development',
  SENTRY_RELEASE: 'delivery-admin@dev',
};
