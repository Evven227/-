\echo 'PostgreSQL version and connection'
SELECT version(), current_user, current_database();

\echo 'Base table count'
SELECT count(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE';

\echo 'Key demo row counts'
SELECT 'PATIENT' AS table_name, count(*) AS row_count FROM "PATIENT"
UNION ALL SELECT 'STAFF', count(*) FROM "STAFF"
UNION ALL SELECT 'DENTIST', count(*) FROM "DENTIST"
UNION ALL SELECT 'ACCOUNT', count(*) FROM "ACCOUNT"
UNION ALL SELECT 'ACCESS_GRANT', count(*) FROM "ACCESS_GRANT"
UNION ALL SELECT 'APPOINTMENT', count(*) FROM "APPOINTMENT"
UNION ALL SELECT 'ENCOUNTER', count(*) FROM "ENCOUNTER"
UNION ALL SELECT 'PAYMENT', count(*) FROM "PAYMENT"
UNION ALL SELECT 'DENTIST_PERFORMANCE', count(*) FROM "DENTIST_PERFORMANCE"
ORDER BY table_name;

\echo 'Role coverage'
SELECT a."login_name", g."role_code", g."scope_type", g."clinic_id"
FROM "ACCOUNT" a
JOIN "ACCESS_GRANT" g ON g."account_id" = a."account_id"
ORDER BY a."account_id";

\echo 'Performance results'
SELECT "performance_id", "dentist_id", "clinic_id", "period_start", "period_end",
       "performance_score", "reward_level", "review_status"
FROM "DENTIST_PERFORMANCE"
ORDER BY "performance_id";
