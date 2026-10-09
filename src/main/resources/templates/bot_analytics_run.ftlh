<#ftl auto_esc=false>
<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>${title?html}</title>
<style>body{font:16px system-ui;max-width:1200px;margin:2rem auto;padding:1rem}table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:.4rem}a{color:#154990}pre{white-space:pre-wrap;overflow-wrap:anywhere;max-width:1000px}summary{cursor:pointer}</style></head><body>
<nav><a href="${backUrl?html}">← ${backLabel?html}</a> · <a href="${seriesUrl?html}">Список игр серии #${run.series.id?c}</a> · <a href="/admin/bot-analytics">Все серии</a></nav><h1>${title?html}</h1>
<p>Seed: ${run.seed?c} · Рынок: ${run.market?html} · Состояние: ${run.status?html} · Дни: ${run.completedDays?c} / ${run.horizon?c} · Ботов: ${run.bots?c}. Модель: ${(run.series.modelVersion!'нет данных')?html}.</p>
<#if run.error?has_content><p><strong>Записанная ошибка:</strong> ${run.error?html}</p><#elseif run.status == 'INCOMPLETE'><p>Игра не прошла критерий полноты; отдельная причина в старом запуске не сохранена.</p></#if>
<details><summary>Все параметры рынка (скрыты от ботов)</summary><table><#list game?keys as k><#if game[k]??><tr><th>${k?html}</th><td>${game[k]?string?html}</td></tr></#if></#list></table></details>
<h2>Боты</h2><table><tr><th>Бот</th><th>Политика</th><th>Начальный кредит</th><th>Мощность</th><th>Циклы</th><th>Продано</th><th>Итог после долгов</th></tr>
<#list players as p><tr><td><a href="${botSelectionUrl?html}&amp;bot=${p.id?c}">${p.name?html} (#${p.id?c})</a><#if bot?? && bot == p.id> — выбран</#if></td><td>${(p.policy!'нет данных')?html}</td><td>${(p.investment_credit_amount!'нет данных')?string?html}</td><td>${(p.production_capacity_per_day!'нет данных')?string?html}</td><td>${p.cycles?c}</td><td>${p.sold?c}</td><td>${(p.result_after_debt!'нет данных')?string?html}</td></tr></#list></table>
<#if bot??><h2>Бот #${bot?c}: торговые дни</h2><p>Товары: штук; баланс, цена, налог и долги: игровые денежные единицы. Продажи и произведённое — факты, не прогноз.</p>
<#if days?has_content><canvas id="goods" width="900" height="220" role="img" aria-label="Произведено, продано, остаток по дням"></canvas><p>Товары, шт.: <span style="color:#136aab">● выпущено</span> · <span style="color:#13884f">● продано</span> · <span style="color:#c1720e">● остаток</span></p>
<canvas id="cash" width="900" height="220" role="img" aria-label="Баланс и долги по дням"></canvas><p>Деньги: <span style="color:#136aab">● баланс</span> · <span style="color:#be3651">● инвестиционный долг</span> · <span style="color:#c1720e">● оборотный долг</span></p></#if>
<table id="daily-data"><tr><th>День</th><th>Произведено</th><th>Продано</th><th>Остаток</th><th>Цена</th><th>Баланс</th><th>Налог</th><th>Выплаты инвест. / оборот.</th><th>Долги инвест. / оборот.</th></tr>
<#list days as d><tr data-investment="${(d.current_investment_credit_debt_amount!'')?string?html}" data-business="${(d.current_business_credit_debt_amount!'')?string?html}"><td>${d.trade_date?c}</td><td>${(d.products_produced!'—')?string?html}</td><td>${(d.products_sold!'—')?string?html}</td><td>${(d.products_in_stock!'—')?string?html}</td><td>${(d.price!'—')?string?html}</td><td>${(d.balance!'—')?string?html}</td><td>${(d.paid_taxes_amount!'—')?string?html}</td><td>${(d.repaid_investment_credit_amount!'—')?string?html} / ${(d.repaid_business_credit_amount!'—')?string?html}</td><td>${(d.current_investment_credit_debt_amount!'—')?string?html} / ${(d.current_business_credit_debt_amount!'—')?string?html}</td></tr></#list></table>
<h2>Принятые циклы</h2><table><tr><th>Начало / дней</th><th>Партия</th><th>Цена / себестоимость</th><th>Качество / ассортимент</th><th>Оборотный кредит</th></tr><#list cycles as c><tr><td>${c.start_date?c} / ${(c.time_to_market!'—')?string?html}</td><td>${(c.product_count!'—')?string?html}</td><td>${(c.price!'—')?string?html} / ${(c.cost_price!'—')?string?html}</td><td>${(c.quality_index!'—')?string?html} / ${(c.assortment!'—')?string?html}</td><td>${(c.business_credit_amount!'—')?string?html}</td></tr></#list></table>
<h2>Реклама</h2><#list ads as a><p>Дни ${a.start_date?c}–${a.end_date?c}: интенсивность ${a.intensity_index?c}, стоимость ${a.cost?string?html}</p><#else><p>Нет записей.</p></#list>
<details><summary>Исходные записи торгов (до 1000 записей)</summary><p>Износ относится к ранее купленному товару; не прибавлять эти продажи к статистике дня повторно.</p><table><tr><th>День</th><th>Товаров</th><th>Цена</th><th>Качество</th><th>Износ</th></tr><#list trades as t><tr><td>${t.trade_date?c}</td><td>${(t.product_number!'—')?string?html}</td><td>${(t.price!'—')?string?html}</td><td>${(t.quality_index!'—')?string?html}</td><td>${(t.is_worn_out!'—')?string?html}</td></tr></#list></table></details>
<h2>Платежи (фактические даты отдельно от будущих)</h2><details><summary>Инвестиционный кредит</summary><#list investmentPayments as p><p>День ${(p.date!'—')?string?html}: тело ${(p.principal_payment!'—')?string?html}, проценты ${(p.interest_amount!'—')?string?html}; следующий срок ${(p.next_date!'—')?string?html}</p><#else><p>Нет записей.</p></#list></details>
<details><summary>Оборотный кредит</summary><#list businessPayments as p><p>День ${(p.date!'—')?string?html}: выплачено ${(p.amount!'—')?string?html}; следующий срок ${(p.next_date!'—')?string?html}, план ${(p.next_amount!'—')?string?html}; цикл начался в день ${(p.cycle_day!'—')?string?html}</p><#else><p>Нет записей.</p></#list></details>
<details><summary>Налог с продаж</summary><#list taxes as p><p>День ${(p.date!'—')?string?html}: ${(p.amount!'—')?string?html}</p><#else><p>Нет записей.</p></#list></details>
<h2>Решения до торгового шага</h2><p>Снимок содержит только доступное боту на тот момент. Журнал отказов ведётся для новых игр; в старых играх может отсутствовать.</p>
<#list decisions as d><details><summary>День ${d.decision_day?c}: ${d.outcome?html}; политика ${d.policy?html}; модель ${d.used_model?string('да','нет')}, fallback ${d.fallback?string('да','нет')}</summary><p>Источник: ${(d.recommendation_source!'нет данных')?html}; причина fallback: ${(d.fallback_reason!'нет данных')?html}; версия модели: ${(d.model_version!'нет данных')?html}</p><h4>Что видел бот до решения</h4><pre>${d.snapshot_json?html}</pre><h4>Предложенный план</h4><pre>${d.proposed_action_json?html}</pre><h4>Реально рассмотренные карточки (если были)</h4><pre>${(d.candidates_json!'нет данных')?html}</pre></details><#else><p>Журнал не сохранён для этой игры.</p></#list>
<h2>Сохранённые учебные эпизоды</h2><#list episodes as e><details><summary>День ${e.decision_day?c}: дней метки ${e.observed_days?c} / ${e.cycle_days?c}, дубли ${e.duplicate_days?c}, продажи <#if e.observed_days == e.cycle_days && e.duplicate_days == 0>${(e.sold!0)?string?html}<#else>нет достоверной полной метки</#if></summary><pre>Снимок: ${e.snapshot_json?html}
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
</script></body></html>
