-- OAuth (Authorization Code) config for Calendly (example)
INSERT INTO external_system_config (name, base_url, user_endpoint, auth_type, auth_config_json, pagination_config_json)
VALUES (
           'calendly',
           'https://api.calendly.com',
           '/users',
           'OAUTH2',
           '{
              "client_id":"YOUR_CLIENT_ID_HERE",
              "client_secret":"YOUR_CLIENT_SECRET_HERE",
              "authorize_url":"https://auth.calendly.com/oauth/authorize",
              "token_url":"https://auth.calendly.com/oauth/token",
              "redirect_uri":"http://localhost:8080/api/integrations/oauth/callback"
            }',
           '{"type":"none"}'
       );