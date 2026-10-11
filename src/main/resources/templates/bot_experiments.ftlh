<#ftl auto_esc=false>
<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Создать серию ботов</title><link rel="stylesheet" href="/css/bot-experiments.css"></head>
<body><nav aria-label="Эксперименты"><a href="/profile">Профиль</a><a href="/admin/bot-analytics">Обзор серий</a><a href="/admin/bot-experiments" aria-current="page">Создать серию</a><a href="/admin/bot-analytics/training">Обучение моделей</a></nav><h1>Создать серию игр</h1><p>Проверьте условия и состав ближайших игр в предпросмотре перед запуском.</p>
<#if error??><p role="alert" class="error">${error?html}</p></#if>
<form method="post" id="settings">
<fieldset><legend>Цель и выполнение</legend>
<label>Завершённых игр <input name="target" type="number" min="1" max="10000" value="${config.target?c}" required></label>
<label>Одновременно <input name="parallel" type="number" min="1" max="10" value="${config.parallel?c}" required></label>
<label>Лимит попыток (0 = автоматически) <input name="maxAttempts" type="number" min="0" value="${config.maxAttempts?c}" required></label></fieldset>
<fieldset><legend>Участники и длительность игры</legend>
<label>Ботов от <input name="minBots" type="number" min="2" max="30" value="${config.minBots?c}" required></label>
<label>до <input name="maxBots" type="number" min="2" max="30" value="${config.maxBots?c}" required></label><br>
<label>Дней от <input name="minDays" type="number" min="30" max="180" value="${config.minDays?c}" required></label>
<label>до <input name="maxDays" type="number" min="30" max="180" value="${config.maxDays?c}" required></label><br>
<label>Seed <input name="seed" type="number" value="${config.seed?c}" required></label><p class="muted">При тех же настройках и seed сценарии воспроизводятся.</p></fieldset>
<fieldset><legend>Режим</legend><label>Назначение серии <select name="mode"><option value="COLLECT" <#if config.mode == 'COLLECT'>selected</#if>>Сбор данных</option><option value="EVALUATE" <#if config.mode == 'EVALUATE'>selected</#if>>Парная оценка: правила / модель</option></select></label><p class="muted">Парная оценка требует готовую модель и чётное число игр и попыток: пары получают одинаковый seed и условия рынка.</p></fieldset>
<fieldset><legend>Рынки (можно выбрать несколько)</legend>
<#list markets!['BUDGET','COMPETITIVE','ADVERTISING','QUALITY_FOCUS','LOYALTY','CREDIT_CRUNCH','PERISHABLE','OVERSUPPLY'] as m><label><input type="checkbox" class="market-option" value="${m?html}" <#if config.marketMix?split(',')?seq_contains(m)>checked</#if>>${(marketNames!['Бюджетный','Конкурентный','Дорогая реклама','Ставка на качество','Лояльный покупатель','Дорогие кредиты','Быстрый износ','Избыточное предложение'])[m?index]?html} <small>(${m?html})</small></label></#list>
<input type="hidden" name="marketMix" id="marketMix" value="${config.marketMix?html}"><p class="muted">Выбор влияет на параметры игры, а не на алгоритм покупателя. <a href="#markets">Чем отличаются рынки?</a></p>
<details id="markets"><summary>Что меняется в новых рынках</summary><ul>
<li>QUALITY_FOCUS — больший вес качества при выборе товара;</li>
<li>LOYALTY — сильнее учитываются прошлые покупки и длиннее история привычки;</li>
<li>CREDIT_CRUNCH — более дорогие кредиты и создание мощности;</li>
<li>PERISHABLE — товары изнашиваются быстрее;</li>
<li>OVERSUPPLY — покупатель может приобрести меньше товаров при том же объёме предложения.</li>
</ul></details></fieldset><fieldset><legend>Политики ботов</legend>
<label>Политики ботов (через запятую, по умолчанию все 38; между играми состав чередуется)<br>
<textarea name="policyMix" rows="5" maxlength="1200" required>${config.policyMix?html}</textarea></label><br>
<details><summary>Все ${policyNames?size} политик и их смысл</summary><p>Когда для стратегии пока нет нужной истории, она сохраняет допустимый текущий план. Предпросмотр ниже показывает состав ближайших игр.</p><ul><#list policyNames as name><li><code>${name?html}</code> — ${policyDescriptions[name]?html}</li></#list></ul></details></fieldset>
<div class="actions"><button type="button" onclick="preview()">Предпросмотр пяти игр</button><button type="submit">Запустить серию</button></div>
</form><p id="error" role="alert"></p><div class="table-wrap"><table><thead><tr><th>№</th><th>Seed</th><th>Рынок</th><th>Боты</th><th>Дни</th><th>Себестоимость</th><th>Спрос / бюджет</th><th>Политики</th></tr></thead><tbody id="preview"></tbody></table></div>
<h2>Последние серии</h2><ul id="series"></ul><p><a href="/admin/bot-analytics">Все серии с фильтрами и результатами →</a></p>
<script><#noparse>
function syncMarkets(){document.querySelector('#marketMix').value=[...document.querySelectorAll('.market-option:checked')].map(el=>el.value).join(',')}
document.querySelectorAll('.market-option').forEach(el=>el.addEventListener('change',syncMarkets));
document.querySelector('#settings').addEventListener('submit',syncMarkets);
fetch('/admin/bot-experiments/series').then(r=>r.json()).then(items=>{
 const list=document.querySelector('#series');for(const item of items){
  const li=document.createElement('li'),link=document.createElement('a');link.href='/admin/bot-experiments/'+item.id;
  link.textContent=`Серия #${item.id}: ${item.status} (${item.mode})`;li.append(link);list.append(li);
 }
});
async function preview(){
  syncMarkets();
 const url='/admin/bot-experiments/preview?'+new URLSearchParams(new FormData(document.querySelector('#settings')));
 const response=await fetch(url);const body=document.querySelector('#preview');body.replaceChildren();
 if(!response.ok){document.querySelector('#error').textContent=await response.text();return}
 document.querySelector('#error').textContent='';
 for(const s of await response.json()){
  const row=body.insertRow();for(const value of [s.number,s.seed,s.market,s.bots,s.horizon,s.baseCost,s.purchaseLimit+' / '+s.dailyBudget,s.policies])row.insertCell().textContent=value;
 }
}
</#noparse></script></body></html>
