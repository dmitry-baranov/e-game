-- Only service bot addresses are changed; account IDs and game history remain intact.
UPDATE account
SET email = left(email, -length('@experiment.invalid')) || '@experiment.example'
WHERE service_account = true
  AND service_account_type = 'EXPERIMENT_BOT'
  AND email LIKE 'bot-%@experiment.invalid';
