<#ftl auto_esc=false>
<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>Эксперименты ботов</title>
<style>body{font:16px system-ui;max-width:900px;margin:2rem auto;padding:1rem} label{display:inline-block;margin:.5rem} input{width:7rem} textarea{width:min(90vw,850px)} button{margin:.5rem;padding:.5rem} table{border-collapse:collapse} td,th{padding:.5rem;border:1px solid #ddd}code{display:inline-block;padding:.2rem}</style></head>
<body><a href="/profile">← Профиль</a> · <a href="/admin/bot-analytics">Аналитика ботов и моделей</a><h1>Серия автономных игр</h1>
<#if error??><p style="color:red">${error?html}</p></#if>
<form method="post" id="settings">
<label>Завершённых игр <input name="target" type="number" min="1" max="10000" value="${config.target?c}" required></label>
<label>Одновременно <input name="parallel" type="number" min="1" max="10" value="${config.parallel?c}" required></label><br>
<label>Ботов от <input name="minBots" type="number" min="2" max="30" value="${config.minBots?c}" required></label>
<label>до <input name="maxBots" type="number" min="2" max="30" value="${config.maxBots?c}" required></label><br>
<label>Дней от <input name="minDays" type="number" min="30" max="180" value="${config.minDays?c}" required></label>
<label>до <input name="maxDays" type="number" min="30" max="180" value="${config.maxDays?c}" required></label><br>
<label>Seed <input name="seed" type="number" value="${config.seed?c}" required></label>
<label>Лимит попыток (0 = автоматически) <input name="maxAttempts" type="number" min="0" value="${config.maxAttempts?c}" required></label><br>
<label>Режим <select name="mode"><option value="COLLECT">Сбор данных</option><option value="EVALUATE">Парная оценка: правила / модель (чётная цель)</option></select></label><br>
<label>Рынки (через запятую: BUDGET, COMPETITIVE, ADVERTISING)<br>
<input name="marketMix" style="width:25rem" value="${config.marketMix?html}" required></label><br>
<label>Политики ботов (через запятую, по умолчанию все 38; между играми состав чередуется)<br>
<textarea name="policyMix" rows="5" maxlength="1200" required>${config.policyMix?html}</textarea></label><br>
<details><summary>Все ${policyNames?size} политик и их смысл</summary><p>Когда для стратегии пока нет нужной истории, она сохраняет допустимый текущий план. Предпросмотр ниже показывает состав ближайших игр.</p><ul><#list policyNames as name><li><code>${name?html}</code> — ${policyDescriptions[name]?html}</li></#list></ul></details>
<button type="button" onclick="preview()">Предпросмотр пяти игр</button><button type="submit">Запустить</button>
</form><p id="error"></p><table><thead><tr><th>№</th><th>Seed</th><th>Рынок</th><th>Боты</th><th>Дни</th><th>Себестоимость</th><th>Спрос / бюджет</th><th>Политики</th></tr></thead><tbody id="preview"></tbody></table>
<h2>Сохранённые серии</h2><ul id="series"></ul>
<script><#noparse>
fetch('/admin/bot-experiments/series').then(r=>r.json()).then(items=>{
 const list=document.querySelector('#series');for(const item of items){
  const li=document.createElement('li'),link=document.createElement('a');link.href='/admin/bot-experiments/'+item.id;
  link.textContent=`Серия #${item.id}: ${item.status} (${item.mode})`;li.append(link);list.append(li);
 }
});
async function preview(){
 const url='/admin/bot-experiments/preview?'+new URLSearchParams(new FormData(document.querySelector('#settings')));
 const response=await fetch(url);const body=document.querySelector('#preview');body.replaceChildren();
 if(!response.ok){document.querySelector('#error').textContent=await response.text();return}
 document.querySelector('#error').textContent='';
 for(const s of await response.json()){
  const row=body.insertRow();for(const value of [s.number,s.seed,s.market,s.bots,s.horizon,s.baseCost,s.purchaseLimit+' / '+s.dailyBudget,s.policies])row.insertCell().textContent=value;
 }
}
</#noparse></script></body></html>
