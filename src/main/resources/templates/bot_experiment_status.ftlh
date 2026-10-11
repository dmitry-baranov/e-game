<#ftl auto_esc=false>
<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Управление серией #${seriesId?c}</title><link rel="stylesheet" href="/css/bot-experiments.css"></head><body>
<nav aria-label="Эксперименты"><a href="/admin/bot-analytics">Обзор серий</a><a href="/admin/bot-experiments">Создать серию</a><a href="/admin/bot-experiments/${seriesId?c}" aria-current="page">Ход серии #${seriesId?c}</a><a href="/admin/bot-analytics/series/${seriesId?c}">Результаты и все игры</a><a href="/admin/bot-analytics/training">Обучение</a></nav><h1>Ход серии #${seriesId?c}</h1>
<#if error??><p role="alert" class="error">${error?html}</p></#if>
<p id="loading" role="status">Загрузка данных серии...</p><div class="cards" id="summary"></div><p id="details" class="muted"></p><p id="updated" class="muted"></p>
<div class="actions" id="controls"><form method="post" action="/admin/bot-experiments/${seriesId?c}/pause" id="pause"><button>Пауза</button></form><form method="post" action="/admin/bot-experiments/${seriesId?c}/resume" id="resume"><button>Продолжить</button></form><form method="post" action="/admin/bot-experiments/${seriesId?c}/stop" id="stop" onsubmit="return confirm('Остановить серию #${seriesId?c}? Ожидающие игры будут отменены.')"><button>Остановить серию</button></form></div>
<section id="training-section" hidden><h2>Обучение модели</h2><p>Запускается вручную; требуется не менее 12 завершённых игр сбора данных. Оценочные игры не входят в выборку.</p><form method="post" action="/admin/bot-experiments/${seriesId?c}/train"><button>Запустить обучение</button></form><details><summary>Последний отчёт ML-сервиса</summary><pre id="training">Загрузка по запросу...</pre></details></section>
<h2>Последние 30 игр</h2><p>Полный список и фильтры доступны в <a href="/admin/bot-analytics/series/${seriesId?c}">результатах серии</a>.</p><div class="table-wrap"><table><thead><tr><th>Игра</th><th>Статус</th><th>Рынок</th><th>Ботов</th><th>День / горизонт</th><th>Циклов</th><th>Модель / fallback</th><th>Склад / кредит</th><th>Итог после долгов</th></tr></thead><tbody id="runs"></tbody></table></div>
<h2>Результаты</h2><p>Подробный отчёт по политикам, рынкам и играм: <a href="/admin/bot-analytics/series/${seriesId?c}">статистика серии #${seriesId?c}</a>.</p><details id="raw-report"><summary>Диагностический JSON-отчёт</summary><pre id="report">Загрузить по запросу</pre></details>
<script><#noparse>
let lastState='',loading=false;
const stateNames={RUNNING:'Выполняется',PAUSED:'На паузе',DONE:'Цель достигнута',EXHAUSTED:'Лимит попыток исчерпан',STOPPED:'Остановлена',COMPLETED:'Завершена',INCOMPLETE:'Неполная',ERROR:'Ошибка',QUEUED:'В очереди',WAITING_PAIR:'Ожидает пару',CANCELLED:'Отменена'};
function card(value,label){const el=document.createElement('div');el.className='card';const number=document.createElement('strong');number.textContent=value;el.append(number,document.createTextNode(label));return el}
async function refresh(){
 if(loading)return;loading=true;
 try{
  const response=await fetch(location.pathname+'/status');if(!response.ok)throw new Error('Не удалось получить состояние серии');
  const s=await response.json(),cfg=JSON.parse(s.config);document.querySelector('#loading').textContent='';
  const cards=document.querySelector('#summary');cards.replaceChildren(card(stateNames[s.state]??s.state,'Состояние'),card(s.completed+' / '+cfg.target,'Завершено / цель'),card(s.active,'Активно'),card(s.queued,'В очереди'),card(s.incomplete,'Неполных'),card(s.errors,'Ошибок'));
  document.querySelector('#details').textContent=`Игровых дней: ${s.processedDays}. ${s.etaSeconds===null?'Время завершения пока не рассчитано.':'Ориентир: '+Math.ceil(s.etaSeconds/60)+' мин. '}Генератор: ${s.generatorVersion??'нет данных'}. ${s.modelVersion?'Модель: '+s.modelVersion+'.':''}`;
  document.querySelector('#updated').textContent='Обновлено: '+new Date().toLocaleTimeString('ru-RU');
  for(const [name,visible] of [['pause',s.state==='RUNNING'],['resume',s.state==='PAUSED'],['stop',['RUNNING','PAUSED'].includes(s.state)]])document.getElementById(name).hidden=!visible;
  document.querySelector('#training-section').hidden=cfg.mode!=='COLLECT';
  const body=document.querySelector('#runs');body.replaceChildren();
  for(const r of s.runs){const row=body.insertRow(),link=document.createElement('a');link.href='/admin/bot-analytics/runs/'+r.id+'?from=status';link.textContent='Игра № '+r.number;row.insertCell().append(link);
   for(const value of [stateNames[r.status]??r.status,r.market,r.bots,r.days+' / '+r.horizon,r.cycles,r.modelDecisions+' / '+r.modelFallbacks,r.stockSkips+' / '+r.creditSkips,r.bestResult??'нет данных'])row.insertCell().textContent=value}
  if(!s.runs.length){const row=body.insertRow();row.insertCell().textContent='Игры ещё не созданы.'}
  if(lastState!==s.state){lastState=s.state;document.querySelector('#report').textContent='Загрузить по запросу'}
 }catch(error){document.querySelector('#loading').textContent=error.message}finally{loading=false}
}
async function loadJson(path,target){const el=document.querySelector(target);el.textContent='Загрузка...';try{const r=await fetch(path);if(!r.ok)throw Error('Отчёт недоступен');el.textContent=JSON.stringify(await r.json(),null,2)}catch(e){el.textContent=e.message}}
document.querySelector('#raw-report').addEventListener('toggle',event=>{if(event.target.open)loadJson(location.pathname+'/report','#report')});
document.querySelector('#training-section details').addEventListener('toggle',event=>{if(event.target.open)loadJson('/admin/bot-experiments/training-report','#training')});
refresh();setInterval(refresh,5000);
</#noparse></script></body></html>
