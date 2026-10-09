<#ftl auto_esc=false>
<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><title>Серия ботов</title>
<style>body{font:16px system-ui;max-width:1100px;margin:2rem auto;padding:1rem}td,th{padding:.5rem;border:1px solid #ddd}table{border-collapse:collapse}button{padding:.5rem;margin:.3rem}</style></head>
<body><nav><a href="/admin/bot-experiments">← К списку серий и созданию игры</a> · <a href="/admin/bot-analytics/series/${seriesId?c}">Статистика этой серии</a></nav><h1>Управление серией #${seriesId?c}</h1>
<#if error??><p style="color:red">${error?html}</p></#if>
<p id="summary">Загрузка...</p>
<form method="post" action="/admin/bot-experiments/${seriesId?c}/pause" style="display:inline"><button>Пауза</button></form>
<form method="post" action="/admin/bot-experiments/${seriesId?c}/resume" style="display:inline"><button>Продолжить</button></form>
<form method="post" action="/admin/bot-experiments/${seriesId?c}/stop" style="display:inline"><button>Остановить</button></form>
<form method="post" action="/admin/bot-experiments/${seriesId?c}/train" style="display:inline"><button>Обучить на завершённых играх (от 12)</button></form>
<p>Обучение идёт в отдельном ML-сервисе; оценочные партии не входят в его выборку.</p>
<pre id="training">Отчёт об обучении: загрузка...</pre>
<h2>Последние игры</h2><table><thead><tr><th>Игра / статистика</th><th>Состояние</th><th>Seed</th><th>Рынок</th><th>Ботов</th><th>День / горизонт</th><th>Циклов</th><th>Модель / fallback</th><th>Пропуски: склад / деньги</th><th>Повторы</th><th>Лучший итог после долгов</th><th>Ошибка</th></tr></thead><tbody id="runs"></tbody></table>
<h2>Отчёт по стратегиям</h2><pre id="report">Загрузка...</pre>
<script><#noparse>
let reportedCount=-1;
async function refresh(){
 const response=await fetch(location.pathname+'/status');if(!response.ok)return;
 const s=await response.json(), cfg=JSON.parse(s.config);
 document.querySelector('#summary').textContent=`${s.state}: завершено ${s.completed} / ${cfg.target}; активно ${s.active}; в очереди ${s.queued}; неполных ${s.incomplete}; ошибок ${s.errors}; игровых дней ${s.processedDays}. ${s.etaSeconds===null?'':'Оценка времени: '+Math.ceil(s.etaSeconds/60)+' мин. '}Генератор: ${s.generatorVersion??'market-v1'}. ${s.modelVersion?'Модель: '+s.modelVersion:''}`;
 const body=document.querySelector('#runs');body.replaceChildren();
  for(const r of s.runs){const row=body.insertRow(),link=document.createElement('a');link.href='/admin/bot-analytics/runs/'+r.id+'?from=status';link.textContent='Игра № '+r.number;row.insertCell().append(link);for(const value of [r.status,r.seed,r.market,r.bots,r.days+' / '+r.horizon,r.cycles,r.modelDecisions+' / '+r.modelFallbacks,r.stockSkips+' / '+r.creditSkips,r.attempts,r.bestResult??'',r.error??''])row.insertCell().textContent=value}
 if(['DONE','EXHAUSTED','PAUSED','STOPPED'].includes(s.state) && reportedCount!==s.completed){
  const report=await fetch(location.pathname+'/report');if(report.ok){document.querySelector('#report').textContent=JSON.stringify(await report.json(),null,2);reportedCount=s.completed}
 }else if(s.state==='RUNNING')document.querySelector('#report').textContent='Отчёт появится после завершения или паузы серии.';
 const training=await fetch('/admin/bot-experiments/training-report');if(training.ok)document.querySelector('#training').textContent=JSON.stringify(await training.json(),null,2);
}
refresh();setInterval(refresh,3000);
</#noparse></script></body></html>
