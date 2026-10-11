<#ftl auto_esc=false>
<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${title?html}</title><link rel="stylesheet" href="/css/bot-experiments.css"></head><body>
<nav aria-label="Эксперименты"><a href="/admin/bot-analytics">Обзор серий</a><a href="/admin/bot-experiments/${run.series.id?c}">Ход серии</a><a href="${seriesUrl?html}">Результаты серии #${run.series.id?c}</a><#if (seriesMode!'') == 'EVALUATE'><a href="/admin/bot-analytics/evaluations/${run.series.id?c}">Парная оценка</a></#if><a href="${backUrl?html}">← ${backLabel?html}</a></nav><h1>${title?html}</h1>
<div class="cards"><div class="card"><strong>${run.status?html}</strong>Состояние игры</div><div class="card"><strong>${run.completedDays?c} / ${run.horizon?c}</strong>Дни / горизонт</div><div class="card"><strong>${run.bots?c}</strong>Ботов</div><div class="card"><strong>${run.market?html}</strong>Рынок</div></div><p>Seed (точная строка): ${run.seed?c}; режим: ${(seriesMode!'нет данных')?html}; модель: ${(run.series.modelVersion!'нет данных')?html}. <a href="#players">Сравнить ботов</a> · <a href="#daily">Графики и дни</a> · <a href="#cycles">Циклы</a> · <a href="#decisions">Решения</a> · <a href="#payments">Платежи</a> · <a href="#episodes">Учебные эпизоды</a>.</p>
<#if run.error?has_content><p><strong>Записанная ошибка:</strong> ${run.error?html}</p><#elseif run.status == 'INCOMPLETE'><p>Игра не прошла критерий полноты; отдельная причина в старом запуске не сохранена.</p></#if>
<details><summary>Все параметры рынка (скрыты от ботов)</summary><table><#list game?keys as k><#if game[k]??><tr><th>${k?html}</th><td>${game[k]?string?html}</td></tr></#if></#list></table></details>
<h2 id="players">Сравнение ботов в этой игре</h2><p>Итог — конечный результат после долгов; количества товаров — сумма записей торговых дней. У незавершённой игры итог ещё может отсутствовать.</p><div class="table-wrap"><table><tr><th>Бот / выбрать</th><th>Политика</th><th>Принято / склад / кредит</th><th>Циклы</th><th>Произведено / продано</th><th>Конечный остаток</th><th>Текущий баланс</th><th>Итог после долгов</th></tr>
<#list players as p><tr><td><a href="${botSelectionUrl?html}&amp;bot=${p.id?c}">${p.name?html} (#${p.id?c})</a><#if bot?? && bot == p.id> — выбран</#if></td><td>${(p.policy!'нет данных')?html}</td><td>${p.accepted?c} / ${p.stock_skips?c} / ${p.credit_skips?c}</td><td>${p.cycles?c}</td><td>${p.produced?c} / ${p.sold?c}</td><td>${p.current_product_count?c}</td><td>${(p.balance!'—')?string?html}</td><td>${(p.result_after_debt!'нет данных')?string?html}</td></tr><#else><tr><td colspan="8">Участников нет.</td></tr></#list></table></div>
<#if bot??><h2 id="daily">Бот #${bot?c}: торговые дни</h2><p>Товары: штук; баланс, цена, налог и долги: игровые денежные единицы. Вертикальные метки — начало цикла (пунктир) и рекламы (точки), не доказательство их влияния на продажи. Таблица ниже содержит точные числа.</p>
<form method="get" action="/admin/bot-analytics/runs/${run.id?c}"><input type="hidden" name="bot" value="${bot?c}"><input type="hidden" name="from" value="${((from!'series'))?html}"><input type="hidden" name="page" value="${(page!0)?c}"><input type="hidden" name="status" value="${(statusFilter!'')?html}"><input type="hidden" name="market" value="${(marketFilter!'')?html}"><label>Сравнить продажи с другим ботом<select name="compareBot"><option value="">Без сравнения</option><#list players as p><#if p.id != bot><option value="${p.id?c}" <#if compareBot?? && compareBot == p.id>selected</#if>>${p.name?html} (#${p.id?c}, ${(p.policy!'нет данных')?html})</option></#if></#list></select></label><button>Сравнить</button></form>
<#if compareBot??><h3>Продажи двух ботов по дням (штук)</h3><p>Синий — бот #${bot?c}, зелёный — бот #${compareBot?c}. Одинаковая шкала; пропущенные дни не превращаются в нулевые продажи.</p><canvas id="compare" class="chart" width="900" height="220" role="img" aria-label="Сравнение продаж двух ботов по дням"></canvas><div class="table-wrap"><table><thead><tr><th>День</th><th>Бот #${bot?c}: продано</th><th>Бот #${compareBot?c}: продано</th></tr></thead><tbody id="compare-rows"><#list days as d><#assign other='—'><#list compareDays as x><#if x.trade_date == d.trade_date><#assign other=x.products_sold></#if></#list><tr data-day="${d.trade_date?c}" data-first="${(d.products_sold!'')?string?html}" data-second="${other?string?html}"><td>${d.trade_date?c}</td><td>${(d.products_sold!'—')?string?html}</td><td>${other?string?html}</td></tr></#list></tbody></table></div></#if>
<#if days?has_content><canvas id="goods" class="chart" width="900" height="220" role="img" aria-label="Произведено, продано, остаток по дням"></canvas><p>Товары, шт.: <span style="color:#136aab">● выпущено</span> · <span style="color:#13884f">● продано</span> · <span style="color:#c1720e">● остаток</span></p>
<canvas id="cash" class="chart" width="900" height="220" role="img" aria-label="Баланс и долги по дням"></canvas><p>Деньги: <span style="color:#136aab">● баланс</span> · <span style="color:#be3651">● инвестиционный долг</span> · <span style="color:#c1720e">● оборотный долг</span></p><#else><p>Дневных записей пока нет.</p></#if>
<div class="table-wrap"><table id="daily-data"><tr><th>День</th><th>Произведено</th><th>Продано</th><th>Остаток</th><th>Цена</th><th>Баланс</th><th>Налог</th><th>Выплаты инвест. / оборот.</th><th>Долги инвест. / оборот.</th></tr>
<#list days as d><tr id="day-${d.trade_date?c}" data-investment="${(d.current_investment_credit_debt_amount!'')?string?html}" data-business="${(d.current_business_credit_debt_amount!'')?string?html}"><td>${d.trade_date?c}</td><td>${(d.products_produced!'—')?string?html}</td><td>${(d.products_sold!'—')?string?html}</td><td>${(d.products_in_stock!'—')?string?html}</td><td>${(d.price!'—')?string?html}</td><td>${(d.balance!'—')?string?html}</td><td>${(d.paid_taxes_amount!'—')?string?html}</td><td>${(d.repaid_investment_credit_amount!'—')?string?html} / ${(d.repaid_business_credit_amount!'—')?string?html}</td><td>${(d.current_investment_credit_debt_amount!'—')?string?html} / ${(d.current_business_credit_debt_amount!'—')?string?html}</td></tr></#list></table></div>
<h2 id="cycles">Принятые производственные циклы</h2><p>Наблюдение: от дня после старта до конца цикла; выпуск и продажи — факты, выручка — сумма проданных товаров по записанной цене. Выплаты включают налоги и два кредита; изменение баланса — от последнего дня перед циклом до последнего наблюдаемого дня (для первого цикла исходной точки может не быть). Эти величины не равны прибыли и не доказывают влияние плана на продажи.</p><div class="table-wrap"><table><tr><th>Начало / дней</th><th>Партия</th><th>Цена / себестоимость</th><th>Качество / ассортимент</th><th>Оборотный кредит</th><th>Наблюдение: дней; выпущено / продано</th><th>Выручка / выплаты</th><th>Остаток / изменение баланса</th></tr><#list cycles as c><tr id="cycle-${c.start_date?c}" data-cycle="${c.start_date?c}"><td><a href="#day-${(c.start_date+1)?c}">${c.start_date?c}</a> / ${(c.time_to_market!'—')?string?html}</td><td>${(c.product_count!'—')?string?html}</td><td>${(c.price!'—')?string?html} / ${(c.cost_price!'—')?string?html}</td><td>${(c.quality_index!'—')?string?html} / ${(c.assortment!'—')?string?html}</td><td>${(c.business_credit_amount!'—')?string?html}</td><td>${c.observed_days?c} / ${c.time_to_market?c}; ${c.observed_produced?c} / ${c.observed_sold?c}<#if c.observed_days lt c.time_to_market> — цикл наблюдался не полностью</#if></td><td>${(c.observed_revenue!'—')?string?html} / ${(c.observed_payments!'—')?string?html}</td><td>${(c.ending_stock!'—')?string?html} / ${(c.balance_change!'нет исходного дня')?string?html}</td></tr><#else><tr><td colspan="8">Нет принятых циклов.</td></tr></#list></table></div>
<h2>Реклама</h2><#list ads as a><p data-ad="${a.start_date?c}">Дни ${a.start_date?c}–${a.end_date?c}: интенсивность ${a.intensity_index?c}, стоимость ${a.cost?string?html}</p><#else><p>Нет записей.</p></#list>
<details><summary>Исходные записи торгов (до 1000 записей)</summary><p>Износ относится к ранее купленному товару; не прибавлять эти продажи к статистике дня повторно.</p><table><tr><th>День</th><th>Товаров</th><th>Цена</th><th>Качество</th><th>Износ</th></tr><#list trades as t><tr><td>${t.trade_date?c}</td><td>${(t.product_number!'—')?string?html}</td><td>${(t.price!'—')?string?html}</td><td>${(t.quality_index!'—')?string?html}</td><td>${(t.is_worn_out!'—')?string?html}</td></tr></#list></table></details>
<h2 id="payments">Платежи (фактические даты отдельно от будущих)</h2><details><summary>Инвестиционный кредит</summary><#list investmentPayments as p><p>День ${(p.date!'—')?string?html}: тело ${(p.principal_payment!'—')?string?html}, проценты ${(p.interest_amount!'—')?string?html}; следующий срок ${(p.next_date!'—')?string?html}</p><#else><p>Нет записей.</p></#list></details>
<details><summary>Оборотный кредит</summary><#list businessPayments as p><p>День ${(p.date!'—')?string?html}: выплачено ${(p.amount!'—')?string?html}; следующий срок ${(p.next_date!'—')?string?html}, план ${(p.next_amount!'—')?string?html}; цикл начался в день ${(p.cycle_day!'—')?string?html}</p><#else><p>Нет записей.</p></#list></details>
<details><summary>Налог с продаж</summary><#list taxes as p><p>День ${(p.date!'—')?string?html}: ${(p.amount!'—')?string?html}</p><#else><p>Нет записей.</p></#list></details>
<h2 id="decisions">Хронология решений до торговли</h2><p>Снимок содержит только доступное боту до решений этого дня. Предложение со статусом <code>STOCK_SKIP</code> или <code>CREDIT_SKIP</code> не было выполнено; когда повторная попытка отложена, записи решения нет. Журнал в старых играх может отсутствовать.</p>
<#list decisions as d><details><summary>День ${d.decision_day?c} · ${d.outcome?html} · ${(d.recommendation_source!'нет данных')?html} · ${(d.plan!'План в деталях')?html}</summary><p>Политика: ${d.policy?html}; <#if d.used_model && d.outcome == 'ACCEPTED'>модель применена<#elseif d.used_model>модель предложила план, но он не принят<#elseif d.fallback>fallback к правилам<#else>решение по правилам</#if>. <#if d.fallback>Причина fallback: ${(d.fallback_reason!'нет данных')?html}.</#if> <#if d.outcome == 'ACCEPTED'><a href="#cycle-${d.decision_day?c}">К принятому циклу</a> · <a href="#day-${(d.decision_day+1)?c}">К последующим продажам</a><#else>Этот план не создавал новый цикл.</#if></p><p>Версия модели: ${(d.model_version!'нет данных')?html}</p><h3>Что видел бот до решения</h3><pre>${d.snapshot_json?html}</pre><h3>Предложенный план</h3><pre>${d.proposed_action_json?html}</pre><h3>Рассмотренные карточки</h3><pre>${(d.candidates_json!'нет данных')?html}</pre></details><#else><p>Журнал решений для этой игры не сохранён. Фактические циклы и продажи доступны выше; причины выбора плана восстановить нельзя.</p></#list>
<h2 id="episodes">Сохранённые учебные эпизоды</h2><#list episodes as e><details><summary>День ${e.decision_day?c}: дней метки ${e.observed_days?c} / ${e.cycle_days?c}, дубли ${e.duplicate_days?c}, продажи <#if e.observed_days == e.cycle_days && e.duplicate_days == 0>${(e.sold!0)?string?html}<#else>нет достоверной полной метки</#if></summary><pre>Снимок: ${e.snapshot_json?html}
Принятое действие: ${e.action_json?html}</pre></details><#else><p>Нет записанных эпизодов.</p></#list>
</#if>
<script>
const data = [...document.querySelectorAll('#daily-data tr')].slice(1).map(row =>
  [...row.cells].map(c => Number(c.textContent.trim().replace(',', '.')))
    .concat([row.dataset.investment ? Number(row.dataset.investment) : NaN,
             row.dataset.business ? Number(row.dataset.business) : NaN]));
function plot(id, columns, colors) {
  const canvas = document.getElementById(id); if (!canvas || !data.length) return;
  const ctx = canvas.getContext('2d'), left = 55, top = 15, width = canvas.width - 70, height = canvas.height - 48;
  const values = data.flatMap(row => columns.map(i => row[i])).filter(Number.isFinite);
  if (!values.length) return;
  const min = Math.min(0, ...values), max = Math.max(1, ...values);
  ctx.strokeStyle = '#aaa'; ctx.beginPath(); ctx.moveTo(left, top); ctx.lineTo(left, top + height);
  ctx.lineTo(left + width, top + height); ctx.stroke(); ctx.fillStyle = '#333'; ctx.font = '12px system-ui';
  ctx.fillText(String(max.toFixed(0)), 2, top + 10); ctx.fillText(String(min.toFixed(0)), 2, top + height);
  for (const [selector,key,color,dashes] of [['[data-cycle]','cycle','#677588',[6,3]],['[data-ad]','ad','#aa7191',[2,4]]]) {
    ctx.strokeStyle=color;ctx.setLineDash(dashes);
    for (const item of document.querySelectorAll(selector)) {
      const index=data.findIndex(row => row[0]>=Number(item.dataset[key]));
      if(index<0 || data[index][0]>Number(item.dataset[key])+1)continue;
      const x=left+width*index/Math.max(1,data.length-1);
      ctx.beginPath();ctx.moveTo(x,top);ctx.lineTo(x,top+height);ctx.stroke();
    }
    ctx.setLineDash([]);
  }
  for (let j = 0; j < columns.length; j++) {
    ctx.strokeStyle = colors[j]; ctx.lineWidth = 2; ctx.beginPath(); let started = false;
    data.forEach((row, i) => { const value = row[columns[j]]; if (!Number.isFinite(value)) { started = false; return; }
      const x = left + width * i / Math.max(1, data.length - 1);
      const y = top + height * (max - value) / (max - min);
      if (!started) ctx.moveTo(x, y); else ctx.lineTo(x, y); started = true;
    }); ctx.stroke();
  }
  ctx.fillText('День ' + data[0][0], left, top + height + 22);
  ctx.fillText('День ' + data[data.length - 1][0], left + width - 70, top + height + 22);
}
plot('goods', [1,2,3], ['#136aab','#13884f','#c1720e']);
plot('cash', [5,9,10], ['#136aab','#be3651','#c1720e']);
const compare=document.querySelector('#compare');
if(compare){
 const rows=[...document.querySelectorAll('#compare-rows tr')],ctx=compare.getContext('2d');
 const values=rows.flatMap(row=>[Number(row.dataset.first),Number(row.dataset.second)]).filter(Number.isFinite);
 if(rows.length&&values.length){const left=55,top=15,width=compare.width-70,height=compare.height-48,max=Math.max(1,...values);
  ctx.strokeStyle='#888';ctx.beginPath();ctx.moveTo(left,top);ctx.lineTo(left,top+height);ctx.lineTo(left+width,top+height);ctx.stroke();
  ctx.fillStyle='#333';ctx.font='12px system-ui';ctx.fillText(String(max),4,top+10);ctx.fillText('0',25,top+height);
  for(const [key,color] of [['first','#136aab'],['second','#13884f']]){ctx.strokeStyle=color;ctx.lineWidth=2;ctx.beginPath();let started=false;
   rows.forEach((row,i)=>{const value=row.dataset[key]===''||row.dataset[key]==='—'?NaN:Number(row.dataset[key]);
    if(!Number.isFinite(value)){started=false;return}const x=left+width*i/Math.max(1,rows.length-1),y=top+height*(max-value)/max;
    if(!started)ctx.moveTo(x,y);else ctx.lineTo(x,y);started=true});ctx.stroke()}
  ctx.fillText('День '+rows[0].dataset.day,left,top+height+22);ctx.fillText('День '+rows[rows.length-1].dataset.day,left+width-75,top+height+22);
 }
}
</script></body></html>
