<!DOCTYPE html>
<html lang="ru">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Стратегии</title><link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="container my-4">
<a href="/game/${gameId}">← Вернуться в игру</a>
<h1>Торговые стратегии</h1>
<p>День ${snapshot.getDay()}; выбранный курс: <strong>${owner.selectedStrategy!"пока не выбран"}</strong>.
    Советы не меняют производство, кредит или настройки игры.</p>
<#if !snapshot.isOpened()><p>Партия — число изделий за цикл; мощность — сколько можно выпустить за день.
    Ассортимент — число видов продукции. Качество и реклама повышают расходы и не гарантируют спрос.
    Для открытия предприятия проверяйте расчёт в форме самостоятельно.</p></#if>
<form method="post" action="/game/${gameId}/strategies/recommend" class="my-3">
    <input type="hidden" name="requestKey" value="${requestKey}">
    <button class="btn btn-primary">${snapshot.isOpened()?then('Получить рекомендации', 'Посмотреть стартовые сценарии')}</button>
</form>
<form method="post" action="/game/${gameId}/strategies/training-preference" class="my-3">
    <label><input type="checkbox" name="enabled" value="true" <#if owner.allowStrategyTraining?? && owner.allowStrategyTraining>checked</#if>> Разрешить использование моих будущих партий для обучения моделей (независимо от истории советов)</label>
    <button class="btn btn-outline-secondary btn-sm">Сохранить согласие</button>
    <small class="d-block">Если снять отметку, записанные для обучения эпизоды этой партии удаляются. Ранее опубликованные модели могут содержать обобщённые закономерности.</small>
</form>
<form method="post" action="/game/${gameId}/strategies/history-preference" class="my-3">
    <label><input type="checkbox" name="enabled" value="true" <#if owner.saveRecommendationHistory?? && owner.saveRecommendationHistory>checked</#if>> Сохранять историю рекомендаций</label>
    <button class="btn btn-outline-secondary btn-sm">Сохранить настройку</button>
</form>
<#if advice??>
    <h2>Сценарии на день ${advice.gameDay} (${advice.generatedAtText})</h2>
    <#if advice.courseMismatch??><p class="alert alert-warning">${advice.courseMismatch}</p></#if>
    <#if advice.aiExplanation??><p class="alert alert-info">Пояснение ИИ: ${advice.aiExplanation}</p></#if>
    <#if advice.modelOpinions?? && (advice.modelOpinions?size > 1)>
        <h3 class="h6">Что предлагают обученные модели</h3>
        <ul><#list advice.modelOpinions as opinion><li>${opinion.model} (${opinion.version}): ${opinion.action}. ${opinion.rationale}</li></#list></ul>
        <p>Модели обучались на общих данных и могут ошибаться вместе. Это варианты для сравнения, не автоматическое решение.</p>
    </#if>
    <p><#if historical??>Сохранённый совет: значения не обновлялись после записи. </#if>Показатели условны: сравнивается следующий производственный цикл, а не результат симуляции покупателей. После изменения рынка или параметров запросите новые советы.</p>
    <div class="row">
    <#list advice.recommendations as card>
        <div class="col-lg-4 mb-3"><div class="card h-100"><div class="card-body">
            <h3 class="h5">${card.name}</h3>
            <p><#if card.source == 'MODEL'>Оценка: ${card.modelName} (${card.modelVersion})<#else>Оценка по правилам</#if></p>
            <p>${card.reason}</p>
            <p>Партия: ${card.productCount}, мощность: ${card.capacity}/день; цикл: ${card.cycleDays} дн.<br>
                Цена: ${card.price}; качество: ${card.quality}; ассортимент: ${card.assortment}.<br>
                Реклама: уровень ${card.advertisingIntensity}, ${card.advertisingDays} дн.</p>
            <p>Расходы: ${card.costs}; <#if !snapshot.isOpened()>ориентир стартового кредита: ${card.credit}<#else>
                новый оборотный кредит: ${card.credit}; остаток после затрат: ${card.remaining}; ориентир резерва: ${card.reserve}</#if>.</p>
            <p>Риск: <strong>${card.riskLevel}</strong>. ${card.risk}</p>
            <p>${card.assumption}</p>
            <#if card.modelTypicalSales??><p>Прогноз наблюдаемых продаж за следующий цикл (низкий / типичный / высокий):
                ${card.modelLowSales} / ${card.modelTypicalSales} / ${card.modelHighSales}. Диапазон оценён на отложенных партиях; изменение цены или рекламы может дать другой результат.</p></#if>
            <#if card.lowSales??><p>Наблюдений: ${card.observations}; низкие / обычные / высокие продажи в сутки:
                ${card.lowSales} / ${card.normalSales} / ${card.highSales}. Это не прогноз спроса при новых условиях.</p>
                <#if card.lowCash??><p>Условные деньги к концу цикла после известной оценки платежей и налога:
                    ${card.lowCash} / ${card.normalCash} / ${card.highCash}. Новые долги и неизвестные расходы не учтены.</p></#if>
            <#else><p>Наблюдений: ${card.observations}; недостаточно данных для числового прогноза продаж.</p></#if>
            <#if card.key != 'PERSONAL:null'>
                <form method="post" action="/game/${gameId}/strategies/select">
                    <input type="hidden" name="course" value="${card.key}">
                    <#if advice.historyId??><input type="hidden" name="historyId" value="${advice.historyId}"></#if>
                    <button class="btn btn-outline-primary">Выбрать стратегию (только курс)</button>
                </form>
            </#if>
        </div></div></div>
    <#else><p>Безопасных числовых альтернатив на текущих данных нет. Можно дождаться новых продаж или изменить собственный план.</p></#list>
    </div>
</#if>
<h2>Мои сценарии</h2>
<p>Сохранённые планы остаются доступными при выключенной истории советов. Расчёты пересчитываются по текущим данным при сравнении.</p>
<ul><#list plans as saved>
    <li><a href="/game/${gameId}/strategies?planId=${saved.id}">${saved.name}</a> —
        <form class="d-inline" method="post" action="/game/${gameId}/strategies/delete/${saved.id}">
            <button class="btn btn-link btn-sm">Удалить</button></form></li>
</#list></ul>
<h3><#if plan.id??>Изменить сценарий<#else>Создать свой сценарий</#if></h3>
<p>Сравнение выполняется без сохранения. Сохранение не создаёт партию и не берёт кредит.</p>
<form method="post" class="row g-2">
    <#if plan.id??><input type="hidden" name="id" value="${plan.id}"></#if>
    <div class="col-md-6"><label>Название <input class="form-control" name="name" maxlength="100" required value="${plan.name!}"></label></div>
    <div class="col-md-3"><label>Партия <input class="form-control" name="productCount" type="number" min="1" required value="${plan.productCount!}"></label></div>
    <div class="col-md-3"><label>Мощность <input class="form-control" name="capacity" type="number" min="1" required value="${plan.capacity!(snapshot.getCapacity()!1)}"></label></div>
    <div class="col-md-3"><label>Цена <input class="form-control" name="price" type="number" step="0.01" min="0.01" required value="${plan.price!}"></label></div>
    <div class="col-md-3"><label>Качество (0–1) <input class="form-control" name="quality" type="number" step="0.01" min="0" max="1" required value="${plan.quality!}"></label></div>
    <div class="col-md-2"><label>Ассортимент <input class="form-control" name="assortment" type="number" min="1" required value="${plan.assortment!}"></label></div>
    <div class="col-md-2"><label>Уровень рекламы <input class="form-control" name="advertisingIntensity" type="number" min="0" max="7" required value="${plan.advertisingIntensity!0}"></label></div>
    <div class="col-md-2"><label>Дни рекламы <input class="form-control" name="advertisingDays" type="number" min="0" required value="${plan.advertisingDays!0}"></label></div>
    <#if snapshot.isOpened()><div class="col-md-3"><label>Новый оборотный кредит <input class="form-control" name="businessCreditAmount" type="number" min="0" step="0.01" value="${plan.businessCreditAmount!0}"></label></div></#if>
    <div><button class="btn btn-outline-primary" formaction="/game/${gameId}/strategies/compare">Сравнить</button>
        <button class="btn btn-outline-secondary" formaction="/game/${gameId}/strategies/save">Сохранить план</button></div>
</form>
<h2 class="mt-4">История рекомендаций</h2>
<#list history as entry><p>День ${entry.gameDay}, ${entry.createdAtText}; курс при расчёте: ${entry.selectedStrategy!"не выбран"};
    выбран после совета: ${entry.chosenStrategy!"не выбран"}. <a href="/game/${gameId}/strategies/history/${entry.id}">Посмотреть совет</a></p>
<#else><p>Сохранённых советов нет.</p></#list>
</body></html>
