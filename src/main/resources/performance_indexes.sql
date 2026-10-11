-- Apply manually with psql -f against each database after confirming the table names.
-- CREATE INDEX CONCURRENTLY cannot run inside a transaction; do not execute from Hibernate ddl-auto.
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_advertisement_manufacturer_latest
    ON advertisement (manufacturer_id, start_date DESC, id DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_trading_results_manufacturer_date
    ON trading_session_results (manufacturer_id, trade_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_trading_results_unworn
    ON trading_session_results (manufacturer_id, trade_date)
    WHERE product_number > 0 AND is_worn_out = false;
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_manufacturer_game
    ON manufacturer (game_id, id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_investment_payment_manufacturer_next
    ON investment_credit_payment (manufacturer_id, next_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_business_payment_production_next
    ON business_credit_payment (production_parameters_id, next_date);
CREATE INDEX CONCURRENTLY IF NOT EXISTS ix_experiment_run_series_status_number
    ON experiment_run (series_id, status, number DESC);
