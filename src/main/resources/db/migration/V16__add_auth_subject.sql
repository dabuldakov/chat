-- Единый IdP (auth-service): привязка локального пользователя к субъекту OIDC.
-- auth_subject — UUID пользователя в auth-service (claim sub access-токена).
ALTER TABLE users ADD COLUMN auth_subject UUID NULL;

-- Пользователи из auth-service находятся по sub; NULL допускается для legacy.
ALTER TABLE users ADD CONSTRAINT uk_users_auth_subject UNIQUE (auth_subject);
