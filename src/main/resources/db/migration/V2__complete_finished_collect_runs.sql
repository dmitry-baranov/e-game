UPDATE experiment_run r
SET status = 'COMPLETED'
FROM experiment_series s
WHERE r.series_id = s.id
  AND s.mode = 'COLLECT'
  AND r.status = 'INCOMPLETE';

UPDATE experiment_series s
SET status = 'DONE'
WHERE s.mode = 'COLLECT'
  AND s.status = 'EXHAUSTED'
  AND (
      SELECT COUNT(*)
      FROM experiment_run r
      WHERE r.series_id = s.id
        AND r.status = 'COMPLETED'
  ) >= s.target;
