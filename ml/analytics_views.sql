-- Applied only to the isolated experimental database after the first training attempt.
-- The full immutable JSON stays in training_run_report; these are read-only aggregates for Grafana.
CREATE OR REPLACE VIEW training_run_models AS
SELECT r.training_run_id, r.created_at, r.status, r.report->>'dataset' AS dataset,
       r.report->>'feature_version' AS feature_version,
       r.report->>'champion' AS champion,
       r.artifact_sha256,
       model.key AS algorithm, part.key AS split,
       (part.value->>'n')::integer AS n,
       (part.value->>'mae')::numeric AS mae,
       (part.value->>'median_absolute_error')::numeric AS median_absolute_error,
       (part.value->>'zero_sales_n')::integer AS zero_sales_n,
       (part.value->>'zero_sales_mae')::numeric AS zero_sales_mae,
       (part.value->>'overestimated')::integer AS overestimated,
       (part.value->>'underestimated')::integer AS underestimated
FROM training_run_report r
CROSS JOIN LATERAL jsonb_each(coalesce(r.report->'models','{}'::jsonb)) model
CROSS JOIN LATERAL jsonb_each(model.value) part
WHERE part.key IN ('train','validation','test');

CREATE OR REPLACE VIEW training_run_corpus AS
SELECT training_run_id, created_at, status, report->>'dataset' AS dataset,
       (report->>'games')::integer AS games,
       (report->>'cycles')::integer AS cycles,
       (report->'selection'->>'candidate_episodes')::integer AS candidate_episodes,
       (report->'selection'->>'eligible_cycles')::integer AS eligible_cycles,
       (report->'selection'->>'incomplete_cycles')::integer AS incomplete_cycles,
       report->>'champion' AS champion, report->>'version' AS version, artifact_sha256,
       (report->'selection'->>'duplicate_cycles')::integer AS duplicate_cycles
FROM training_run_report;

CREATE OR REPLACE VIEW training_run_slices AS
SELECT r.training_run_id, r.created_at,
       model.key AS algorithm, split.key AS split,
       dimension.key AS dimension, category.key AS category,
       (category.value->>'n')::integer AS n,
       (category.value->>'mae')::numeric AS mae,
       (category.value->>'median_absolute_error')::numeric AS median_absolute_error,
       (category.value->>'zero_sales_n')::integer AS zero_sales_n
FROM training_run_report r
CROSS JOIN LATERAL jsonb_each(coalesce(r.report->'models','{}'::jsonb)) model
CROSS JOIN LATERAL jsonb_each(coalesce(model.value->'slices','{}'::jsonb)) split
CROSS JOIN LATERAL jsonb_each(split.value) dimension
CROSS JOIN LATERAL jsonb_each(dimension.value) category;

CREATE OR REPLACE VIEW training_run_feature_ranges AS
SELECT r.training_run_id, r.created_at, r.report->>'feature_version' AS feature_version,
       feature.key AS feature,
       (feature.value->>'min')::numeric AS train_min,
       (feature.value->>'max')::numeric AS train_max
FROM training_run_report r
CROSS JOIN LATERAL jsonb_each(coalesce(r.report->'feature_ranges','{}'::jsonb)) feature;

CREATE OR REPLACE VIEW experiment_evaluation_pairs AS
SELECT es.id AS evaluation_series_id, es.model_version,
       (baseline.number + 1) / 2 AS pair_number,
       baseline.seed::text AS seed, baseline.market, baseline.horizon,
       baseline.id AS rules_run_id, evaluation.id AS model_run_id,
       baseline.status AS rules_status, evaluation.status AS model_status,
       evaluation.model_decisions, evaluation.model_fallbacks,
       br.result AS rules_result_after_debt, mr.result AS model_result_after_debt,
       CASE WHEN baseline.status='COMPLETED' AND evaluation.status='COMPLETED'
                  AND baseline.seed=evaluation.seed AND evaluation.model_decisions>0
            THEN mr.result - br.result ELSE NULL END AS model_minus_rules
FROM experiment_series es
JOIN experiment_run baseline ON baseline.series_id=es.id AND baseline.number % 2=1
LEFT JOIN experiment_run evaluation ON evaluation.series_id=es.id
                                   AND evaluation.number=baseline.number + 1
LEFT JOIN manufacturer bm ON bm.id=(SELECT min(m.id) FROM manufacturer m
                                     WHERE m.game_id=baseline.game_id)
LEFT JOIN game_result br ON br.manufacturer_id=bm.id
LEFT JOIN manufacturer mm ON mm.id=(SELECT min(m.id) FROM manufacturer m
                                     WHERE m.game_id=evaluation.game_id)
LEFT JOIN game_result mr ON mr.manufacturer_id=mm.id
WHERE es.mode='EVALUATE';

CREATE OR REPLACE VIEW experiment_fallback_reasons AS
SELECT d.run_id, r.series_id AS evaluation_series_id, r.market,
       coalesce(d.fallback_reason,'UNKNOWN_OLD_RUN') AS reason, count(*) AS opportunities
FROM bot_decision_log d JOIN experiment_run r ON r.id=d.run_id
WHERE d.policy='MODEL' AND d.fallback=true
GROUP BY d.run_id, r.series_id, r.market, d.fallback_reason;
