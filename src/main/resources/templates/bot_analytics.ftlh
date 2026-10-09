<#ftl auto_esc=false>
<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>${title?html}</title>
<style>body{font:16px system-ui;max-width:1100px;margin:2rem auto;padding:1rem}table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:.4rem}a{color:#154990}</style></head>
<body><nav><a href="/profile">← В профиль</a> · <a href="/admin/bot-experiments">Создать серию</a> · <a href="/admin/bot-analytics/training">История обучения моделей</a></nav><h1>${title?html}</h1>
<p>Результат — конечный баланс после долгов. Циклы считаются только по завершённым партиям. <code>ERROR</code> — ошибка выполнения, <code>INCOMPLETE</code> — игра не прошла критерий полноты/парной оценки; детали смотрите в конкретной игре. Старые ошибки не исчезают после обновления приложения.</p>
<table><thead><tr><th>Серия</th><th>Режим</th><th>Состояние</th><th>Начато / завершено</th><th>Неполных / ошибок</th><th>Циклов</th><th>Генератор / политики / модель</th></tr></thead><tbody>
<#list series as s><tr><td><a href="/admin/bot-analytics/series/${s.id?c}">#${s.id?c}</a></td><td>${(s.mode!'нет данных')?html}</td><td>${s.status?html}</td><td>${s.started?c} / ${s.completed?c}</td><td>${s.incomplete?c} / ${s.errors?c}</td><td>${s.cycles?c}</td><td>${(s.generator_version!'нет данных')?html} / ${(s.policy_version!'нет данных')?html} / ${(s.model_version!'нет данных')?html}</td></tr></#list>
</tbody></table></body></html>
