-- OAuth (Authorization Code) config for Calendly (example)
INSERT INTO external_system_config (name, base_url, user_endpoint, auth_type, auth_config_json, pagination_config_json)
VALUES (
           'calendly',
           'https://api.calendly.com',
           '/users',
           'OAUTH2_CLIENT_CREDENTIALS', -- or use 'OAUTH2' (use consistent string with your code)
           '{
              "client_id":"js48qnojZ70ORuDScew2BoC_evrCXs1F6NHkR-31cZ0",
              "client_secret":"LfsMcPDdyCzex3KAR-iGRvR2Clf1KF7MWPsIXT5L1YY",
              "authorize_url":"https://auth.calendly.com/oauth/authorize",
              "token_url":"https://auth.calendly.com/oauth/token",
              "redirect_uri":"http://localhost:8080/api/integrations/oauth/callback"
            }',
           '{"type":"none"}'
       );