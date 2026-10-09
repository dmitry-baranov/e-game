<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Игра</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        .footer {
            position: absolute;
            bottom: 0;
            width: 100%;
            height: 60px; /* Высота футера */
        }

        html {
            position: relative;
            min-height: 100%;
        }

        #tab1-image {
            background-image: url('https://courseburg.ru/obuchenie_personala_v_organizacii/images/tild3238-6166-4132-b463-393434663337__clip-calculating.png');
            background-size: cover;
            background-position: center;
            height: 450px;
            width: 500px;
            display: none;
        }

    </style>
</head>
<body>
<nav class="navbar navbar-expand-lg navbar-dark bg-primary">
    <div class="container-fluid">
        <a class="navbar-brand" href="/">Economic Concurrent Game</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav"
                aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navbarNav">
            <ul class="navbar-nav ms-auto">
                <li class="nav-item">
                    <a class="nav-link active" aria-current="page" href="/profile">Профиль</a>
                </li>
                <li class="nav-item">
                    <a class="nav-link" href="/logout">Выйти</a>
                </li>
            </ul>
        </div>
    </div>
</nav>

<div class="container-fluid mt-5" style="margin-bottom: 150px">
    <ul class="nav nav-tabs" style="margin-left:50px; margin-right:50px" id="myTab" role="tablist">
        <li class="nav-item" role="presentation">
            <a class="nav-link active" id="tab1-tab" data-bs-toggle="tab" href="#tab1" role="tab" aria-controls="tab1"
               aria-selected="true">Главная</a>
        </li>
        <li class="nav-item" role="presentation">
            <a class="nav-link" id="tab2-tab" data-bs-toggle="tab" href="#tab2" role="tab" aria-controls="tab2"
               aria-selected="false">Моя Статистика</a>
        </li>
        <li class="nav-item" role="presentation">
            <a class="nav-link" id="tab3-tab" data-bs-toggle="tab" href="#tab3" role="tab" aria-controls="tab3"
               aria-selected="false">Конкуренты</a>
        </li>
    </ul>
    <div class="tab-content">
        <div class="tab-pane fade show active" id="tab1" role="tabpanel"
             aria-labelledby="tab1-tab">
            <div class="row justify-content-center" style="margin-left: 50px; margin-right: 50px">
                <#if game.status == 'FINISHED'>
                    <#if !experimentEnabled><p><a href="/game/${game.id?c}/analysis">Разбор моей завершённой игры</a></p></#if>
                    <div class="col-6 d-flex justify-content-center">
                        <div id="game_results" class="align-self-center image-container">
                            <img width="580" height="430"
                                 src="https://courseburg.ru/obuchenie_personala_v_organizacii/images/tild3730-3365-4562-b332-643837363439__clip-working-on-busi.png"
                                 alt="">
                        </div>
                    </div>
                    <div class="col-6" style="margin-top:70px">
                        <h2>Параметры игры</h2>
                        <p>Модельное время(Длительность 1 суток) - ${game.timeUnit} мин</p>
                        <p>Цена создания единицы мощности производства - ${game.productPower}</p>
                        <p>Налог с продаж - ${game.salesTax}%</p>
                        <p>Процентная ставка инвестиционного кредита - ${game.interestRateInvestmentCredit}%</p>
                        <p>Срок инвестиционного кредита - ${game.investmentCreditTermMonths} мес</p>
                    </div>
                    <div class="col-md-12">
                        <h2>Результат игры:</h2>
                        <div class="table-responsive" style="height: 200px">
                            <table style="margin-top: 25px" class="table table-striped">
                                <thead>
                                <tr>
                                    <th>Место</th>
                                    <th>Игрок</th>
                                    <th>Итоговое финансовое состояние</th>
                                </tr>
                                </thead>
                                <tbody>
                                <#list gameResults?sort_by("result")?reverse as gameResult>
                                    <tr>
                                        <td>${gameResult_index + 1}</td>
                                        <td>${gameResult.manufacturer.account.fullName}</td>
                                        <td>${gameResult.result}</td>
                                    </tr>
                                </#list>
                                </tbody>
                            </table>
                        </div>
                    </div>
                <#else>
                    <div class="col-4 d-flex justify-content-center">
                        <div id="tab1-image" class="align-self-center image-container"></div>
                    </div>
                    <div class="col-4" style="margin-top:50px">
                        <h2>Финансовое состояние</h2>
                        <p id="day">День: -</p>
                        <p id="balance">Баланс: -</p>
                        <p>Сумма инвестиционного кредита: ${manufacturer.investmentCreditAmount!}</p>
                        <p id="investmentCreditDebt">Долг по инвестиционному кредиту: -</p>
                        <p id="businessCreditDebt">Долг по оборотным кредитам: -</p>
                        <p id="todayRevenue">Выручка за сегодня: -</p>
                        <button id="refreshTab1" style="margin-top: 10px" class="btn btn-primary">Обновить</button>
                    </div>

                    <div class="col-4" style="margin-top:50px">
                        <h2>Параметры игры</h2>
                        <p>Модельное время(Длительность 1 суток) - ${game.timeUnit} мин</p>
                        <p>Цена создания единицы мощности производства - ${game.productPower}</p>
                        <p>Налог с продаж - ${game.salesTax}%</p>
                        <p>Процентная ставка инвестиционного кредита - ${game.interestRateInvestmentCredit}%</p>
                        <p>Срок инвестиционного кредита - ${game.investmentCreditTermMonths} мес</p>
                        <p>Базовая себестоимость: ${game.baseCostPrice}; базовая цена рекламы: ${game.baseAdvertisementPrice}</p>
                        <p>Ставка оборотного кредита: ${game.interestRateBusinessCredit}%</p>
                    </div>
                </#if>
                <#--                <div class="container-fluid mt-5">-->
                <#--                    <div class="row justify-content-center" style="margin-left: 50px; margin-right: 50px">-->
                <div class="col-md-12" style="margin-top: 30px">
                    <h2>Параметры производства:</h2>
                    <div class="table-responsive" style="height: 300px;">
                        <table id="dataTable" class="table table-striped">
                            <thead>
                            <tr>
                                <th>День начала выпуска продукции</th>
                                <th>Число дней выпуска продукции</th>
                                <th>Количество продукции</th>
                                <th>Цена(шт)</th>
                                <th>Себестоимость(шт)</th>
                                <th>Ассортимент</th>
                                <th>Индекс качества</th>
                                <th>Мощность производства(ед/сутки)</th>
                                <th>День начала рекламы</th>
                                <th>День завершения рекламы</th>
                                <th>Стоимость рекламы</th>
                                <th>Индекс интенсивности рекламы</th>
                                <th>Сумма оборотного кредита</th>
                                <th>Процентная ставка оборотного кредита</th>
                            </tr>
                            </thead>
                            <tbody>
                            <#list productionParameters as parameters>
                                <tr>
                                    <td>${parameters.startDate + 1}</td>
                                    <td>${parameters.timeToMarket!}</td>
                                    <td>${parameters.productCount!}</td>
                                    <td>${parameters.price!}</td>
                                    <td>${parameters.costPrice!}</td>
                                    <td>${parameters.assortment!}</td>
                                    <td>${parameters.qualityIndex!}</td>
                                    <td>${parameters.productionCapacityPerDay!}</td>
                                    <td>${parameters.advertisingStartDate!}</td>
                                    <td>${parameters.advertisingEndDate!}</td>
                                    <td>${parameters.advertisingCost!}</td>
                                    <td>${parameters.advertisingIntensityIndex!}</td>
                                    <td>${parameters.businessCreditAmount!}</td>
                                    <td>${parameters.interestRateBusinessCredit!}</td>
                                </tr>
                            </#list>
                            </tbody>
                        </table>
                    </div>
                    <#if game.status != 'FINISHED'>
                        <h4 id="timeToMarketEndDay">Последний день выпуска текущей партии продукции: -</h4>
                    <#--                        Последний день выпуска продукции текущего производственного цикла-->
                    </#if>
                </div>
                <#if game.status != 'FINISHED'>
                    <div class="col-md-12 my-3"><a class="btn btn-outline-primary" href="/game/${game.id}/strategies">Получить рекомендации и управлять своими сценариями</a>
                        <p>Выбранный курс: ${manufacturer.selectedStrategy!"не выбран"}. Выбор курса не меняет параметры производства.</p></div>
                    <div class="input-group">
                        <button class="btn btn-primary" type="button" id="enterParametersButton"
                                onclick="window.location.href='/game/${game.id}/production-parameters';">Определить
                            новые параметры производства
                        </button>
                    </div>
                </#if>
            </div>
        </div>
        <div class="tab-pane fade" id="tab2" role="tabpanel" aria-labelledby="tab2-tab">
            <div class="container d-flex justify-content-center">
                <div class="row">
                    <div class="col-12 mt-3"><h3>Мои продажи и остатки</h3>
                        <p>Сейчас на складе: ${ownSnapshot.getStock()} шт.</p>
                        <table class="table"><thead><tr><th>День</th><th>Продано</th><th>Выпущено</th><th>Остаток</th></tr></thead><tbody>
                        <#list dailySales as sale><tr><td>${sale.getDay()}</td><td>${sale.getSold()}</td><td>${sale.getProduced()}</td><td>${sale.getStock()}</td></tr>
                        <#else><tr><td colspan="4">Пока нет завершённых торговых дней</td></tr></#list>
                        </tbody></table>
                        <h3>Мои ближайшие платежи</h3>
                        <table class="table"><thead><tr><th>День</th><th>Платёж</th><th>Сумма</th></tr></thead><tbody>
                        <#list ownSnapshot.getPayments() as payment><tr><td>${payment.getDay()}</td><td>${payment.getLabel()}</td><td>${payment.getAmount()}</td></tr>
                        <#else><tr><td colspan="3">Известных платежей нет</td></tr></#list>
                        </tbody></table>
                    </div>
                    <div class="col-12">
                        <div style="margin-top: 15px">
                            <canvas width="800" height="450" id="chart1"></canvas>
                        </div>
                    </div>
                    <div class="col-12">
                        <div style="margin-top: 10px">
                            <canvas width="800" height="450" id="chart2"></canvas>
                        </div>
                    </div>

                    <div>
                        <button id="refreshTab2" style="margin-top: 10px" class="btn btn-primary">Обновить</button>
                    </div>
                </div>
            </div>
        </div>

        <div class="tab-pane fade" id="tab3" role="tabpanel" aria-labelledby="tab3-tab">
            <div class="container d-flex justify-content-center">
                <div class="row">
                    <div id="competitorsContainer">
                    </div>
                    <!-- Здесь будут появляться блоки конкурентов -->

                    <div class="col-12 mt-3">
                        <button id="updateCompetitorsButton" onclick="updateCompetitorsData()" class="btn btn-primary">
                            Обновить
                        </button>
                    </div>
                </div>

                <#--                <div class="row">-->
                <#--                    <#assign competitorsLen = competitors?size>-->
                <#--                    <#if (competitorsLen > 0)>-->
                <#--                        <#list competitorsList as competitor>-->
                <#--                            <div style="margin-top: 15px">-->
                <#--                                <h3>${competitor.fullName}</h3>-->
                <#--                                <h4>Цена товара: ${competitor.price}</h4>-->
                <#--                                <h4>Ассортимент: ${competitor.assortment}</h4>-->
                <#--                                <h4>Индекс интенсивности рекламы: ${competitor.advertisingIntensityIndex}</h4>-->
                <#--                                <canvas id="chartt${competitor.id}" width="800" height="450"></canvas>-->
                <#--                            </div>-->
                <#--                        </#list>-->
                <#--                        <div class="col-12 mt-3">-->
                <#--                            <button id="updateCompetitorsButton" class="btn btn-primary" onclick="updateCompetitorsData()">Обновить</button>-->
                <#--                        </div>-->
                <#--                    <#else>-->
                <#--                        <h4>Как только конкуренты введут параметры производства, вы сможете их увидеть здесь!</h4>-->
                <#--                        <div class="col-12 mt-3">-->
                <#--                            <button id="updateCompetitorsButton" class="btn btn-primary" onclick="updateCompetitorsData()">Обновить</button>-->
                <#--                        </div>-->
                <#--                    </#if>-->
                <#--                </div>-->
            </div>
        </div>
    </div>
</div>


<footer class="footer d-flex flex-column flex-md-row text-center text-md-start justify-content-between py-4 px-4 px-xl-5 bg-primary">
    <div class="text-white mb-3">
        Copyright © 2023. All rights reserved.
    </div>
</footer>

<script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@popperjs/core@2.11.6/dist/umd/popper.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/js/bootstrap.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script>
    let charts = {};
    const tabContent = document.getElementById('tab-content');
    const tabLinks = document.querySelectorAll('.nav-link');

    tabLinks.forEach(link => {
        link.addEventListener('shown.bs.tab', () => {
            // Получаем id отображаемой вкладки
            const tabId = link.getAttribute('href');

            // Очищаем содержимое предыдущей вкладки, если её id не соответствует текущей вкладке
            if (tabContent) {
                const previousTabContent = tabContent.querySelector('.tab-pane.show');
                if (previousTabContent && previousTabContent.id !== tabId.slice(1)) {
                    tabContent.removeChild(previousTabContent);
                }
            }
        });
    });

    function updateCompetitorsData() {
        fetch('/manufacturer/' + ${manufacturer.id} + '/competitors-data')
            .then(response => response.json())
            .then(competitors => {
                const competitorsContainer = document.getElementById("competitorsContainer");
                // Очистим контейнер
                competitorsContainer.innerHTML = '';
                // Чистим объект графиков
                charts = {};
                if (competitors.length > 0) {
                    let competitorNumber = 1;

                    // Строим графики для каждого конкурента
                    for (let competitor of competitors) {
                        // Создаем элементы для информации о конкуренте
                        const competitorDiv = document.createElement('div');
                        competitorDiv.style.marginTop = "15px";
                        const h3 = document.createElement('h3');
                        h3.textContent = competitorNumber + ". " + competitor.fullName;
                        const h4Price = document.createElement('h4');
                        h4Price.textContent = 'Цена товара: ' + competitor.price;
                        const h4Assortment = document.createElement('h4');
                        h4Assortment.textContent = 'Ассортимент: ' + competitor.assortment;
                        const h4Intensity = document.createElement('h4');
                        h4Intensity.textContent = 'Индекс интенсивности рекламы: ' + competitor.advertisingIntensityIndex;
                        const h4Quality = document.createElement('h4');
                        h4Quality.textContent = 'Индекс качества: ' + competitor.qualityIndex;
                        const canvas = document.createElement('canvas');
                        canvas.id = 'chartt' + competitor.id;
                        canvas.width = 800;
                        canvas.height = 450;
                        // Добавляем элементы в div
                        competitorDiv.appendChild(h3);
                        competitorDiv.appendChild(h4Price);
                        competitorDiv.appendChild(h4Assortment);
                        competitorDiv.appendChild(h4Intensity);
                        competitorDiv.appendChild(h4Quality);
                        competitorDiv.appendChild(canvas);
                        // Добавляем div в контейнер
                        competitorsContainer.appendChild(competitorDiv);
                        // Создаем график
                        const chart = createChart(competitor.id);
                        charts[competitor.id] = chart;
                        getChartDataFromItem(competitor.id, chart);
                        competitorNumber++;

                    }
                } else {
                    // Если нет данных о конкурентах, отобразим сообщение
                    const messageContainer = document.createElement('div');
                    messageContainer.style.marginTop = "15px";
                    messageContainer.innerHTML =
                        '<h4>Как только конкуренты введут параметры производства, вы сможете их увидеть здесь!</h4>';
                    competitorsContainer.appendChild(messageContainer);

                    // competitorsContainer.innerHTML = '<h4>Как только конкуренты введут параметры производства, вы сможете их увидеть здесь!</h4>';
                }
            });
    }

    <#--document.getElementById("updateCompetitorsButton").addEventListener("click", function () {-->
    <#--    fetch('/manufacturer/' + ${game.id} + '/competitors-data')-->
    <#--        .then(response => response.json())-->
    <#--        .then(competitors => {-->
    <#--            const competitorsContainer = document.getElementById("competitorsContainer");-->
    <#--            // Очистим контейнер-->
    <#--            competitorsContainer.innerHTML = '';-->
    <#--            // Чистим объект графиков-->
    <#--            charts = {};-->
    <#--            // Строим графики для каждого конкурента-->
    <#--            for (let competitor of competitors) {-->
    <#--                // Создаем элементы для информации о конкуренте-->
    <#--                const competitorDiv = document.createElement('div');-->
    <#--                competitorDiv.style.marginTop = "15px";-->
    <#--                const h3 = document.createElement('h3');-->
    <#--                h3.textContent = competitor.fullName;-->
    <#--                const h4Price = document.createElement('h4');-->
    <#--                h4Price.textContent = 'Цена товара: ' + competitor.price;-->
    <#--                const h4Assortment = document.createElement('h4');-->
    <#--                h4Assortment.textContent = 'Ассортимент: ' + competitor.assortment;-->
    <#--                const h4Intensity = document.createElement('h4');-->
    <#--                h4Intensity.textContent = 'Индекс интенсивности рекламы: ' + competitor.advertisingIntensityIndex;-->
    <#--                const canvas = document.createElement('canvas');-->
    <#--                canvas.id = 'chartt' + competitor.id;-->
    <#--                canvas.width = 800;-->
    <#--                canvas.height = 450;-->
    <#--                // Добавляем элементы в div-->
    <#--                competitorDiv.appendChild(h3);-->
    <#--                competitorDiv.appendChild(h4Price);-->
    <#--                competitorDiv.appendChild(h4Assortment);-->
    <#--                competitorDiv.appendChild(h4Intensity);-->
    <#--                competitorDiv.appendChild(canvas);-->
    <#--                // Добавляем div в контейнер-->
    <#--                competitorsContainer.appendChild(competitorDiv);-->
    <#--                // Создаем график-->
    <#--                const chart = createChart(competitor.id);-->
    <#--                charts[competitor.id] = chart;-->
    <#--                getChartDataFromItem(competitor.id, chart);-->
    <#--            }-->
    <#--        });-->
    <#--});-->

    function fetchBalanceAndDebt() {
        $.ajax({
            url: '/game/' + ${game.id} + '/financial-status/' + ${account.id},
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                document.getElementById('day').innerText = 'День: ' + data.day;
                document.getElementById('balance').innerText = 'Баланс: ' + data.balance;
                document.getElementById('investmentCreditDebt').innerText = 'Долг по инвестиционному кредиту: ' + data.investmentCreditDebt;
                document.getElementById('businessCreditDebt').innerText = 'Долг по оборотному кредиту: ' + data.businessCreditDebt;
                document.getElementById('todayRevenue').innerText = 'Выручка за сегодня: ' + data.todayRevenue;
                document.getElementById('timeToMarketEndDay').innerText = 'Последний день выпуска продукции текущего производственного цикла: ' + data.timeToMarketEndDay;
                const enterParametersButton = document.getElementById('enterParametersButton');
                enterParametersButton.disabled = data.activeManufacturingCycle;
            },
            error: function (error) {
                console.log('Ошибка: ', error);
            },
        });
    }


    // Получаем данные по HTTP-запросу
    let data = {};

    function fetchData() {
        fetch('/game/revenue-data/' + ${manufacturer.id})
            .then(response => response.json())
            .then(newData => {
                // создаем массив данных для графика
                let chartData = [];
                // определяем максимальный ключ в мапе
                let maxKey = Math.max.apply(Math, Object.keys(newData));
                // const labels = Array.from({length: maxKey}, (_, i) => i);
                const labels = Array.from(Array(maxKey + 1).keys());
                // проходим циклом по всем возможным ключам
                for (let i = 0; i <= maxKey; i++) {
                    // если значение для ключа существует в мапе, добавляем его в массив данных
                    if (i in newData) {
                        chartData.push(newData[i]);
                    } else { // иначе добавляем ноль
                        chartData.push(0);
                    }
                }
                // Обновляем график
                chart1.data.labels = labels;
                chart1.data.datasets[0].data = chartData;
                chart1.update();
            });

        fetch('/game/' + ${game.id} + '/advertising-intensity/' + ${account.id})
            .then(response => response.json())
            .then(data => {
                // Находим максимальный номер дня в списке объектов
                const maxDay = Math.max(...data.map(item => item.endDate));

                // Создаем массив с метками по оси x от 1 до максимального номера дня
                const labels = Array.from({length: maxDay}, (_, i) => i + 1);

                // Создаем массив данных для графика
                const chartData = labels.map(day => {
                    // Находим объект в списке, для которого дни включают текущий день
                    const obj = data.find(item => day >= item.startDate && day <= item.endDate);
                    // Если объект найден, возвращаем его интенсивность, иначе 0
                    return obj ? obj.intensityIndex : 0;
                });

                // Обновляем данные графика
                chart2.data.labels = labels;
                chart2.data.datasets[0].data = chartData;
                chart2.update();
            });

    }


    // Отрисовываем график на странице с помощью Chart.js
    const chart1 = new Chart('chart1', {
        type: 'line',
        data: {
            labels: Object.keys(data).sort(),
            datasets: [{
                label: 'Выручка по дням',
                data: Object.values(data).sort(),
                borderColor: 'rgb(255, 99, 132)',
                borderWidth: 1
            }]
        },
        options: {
            legend: {
                display: false
            },
            responsive: false,
            scales: {
                x: {
                    title: {
                        display: true,
                        text: 'День'
                    }
                },
                y: {
                    title: {
                        display: true,
                        text: 'Сумма'
                    },
                    beginAtZero: true
                }
            },
        }
    });
    // Отрисовываем график на странице с помощью Chart.js
    const chart2 = new Chart('chart2', {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: 'Интенсивность рекламы',
                data: [],
                borderColor: 'rgb(255, 99, 132)',
                borderWidth: 1
            }]
        },
        options: {
            legend: {
                display: false
            },
            responsive: false,
            scales: {
                x: {
                    title: {
                        display: true,
                        text: 'День'
                    }
                },
                y: {
                    min: 0,
                    max: 8,
                    stepSize: 1,
                    title: {
                        display: true,
                        text: 'Интенсивность'
                    },
                    beginAtZero: true
                }
            },

        }
    });

    function getChartDataFromItem(competitorId, chart) {
        fetch('/game/revenue-data/' + competitorId)
            .then(response => response.json())
            .then(newData => {
                // создаем массив данных для графика
                let chartData = [];
                // определяем максимальный ключ в мапе
                let maxKey = Math.max.apply(Math, Object.keys(newData));
                // const labels = Array.from({length: maxKey}, (_, i) => i + 1);
                // проходим циклом по всем возможным ключам
                const labels = Array.from(Array(maxKey + 1).keys());
                for (let i = 0; i <= maxKey; i++) {
                    // если значение для ключа существует в мапе, добавляем его в массив данных
                    if (i in newData) {
                        chartData.push(newData[i]);
                    } else { // иначе добавляем ноль
                        chartData.push(0);
                    }
                }
                // Обновляем график
                chart.data.labels = labels;
                chart.data.datasets[0].data = chartData;
                chart.update();
            });
    }

    function createChart(competitorId) {
        return new Chart('chartt' + competitorId, {
            type: 'line',
            data: {
                labels: [],
                datasets: [{
                    label: 'Выручка по дням',
                    data: [],
                    borderColor: 'rgb(255, 99, 132)',
                    borderWidth: 1
                }]
            },
            options: {
                legend: {
                    display: false
                },
                responsive: false,
                scales: {
                    x: {
                        title: {
                            display: true,
                            text: 'День'
                        }
                    },
                    y: {
                        title: {
                            display: true,
                            text: 'Сумма'
                        },
                        beginAtZero: true
                    }
                }
            }
        });
    }

    <#if game.status != 'FINISHED'>
    document.getElementById('refreshTab1').addEventListener('click', fetchBalanceAndDebt);
    </#if>
    document.getElementById('refreshTab2').addEventListener('click', fetchData);
    document.getElementById('myTab').addEventListener('shown.bs.tab', function (event) {
        const targetTabId = event.target.getAttribute('aria-controls');
        switch (targetTabId) {
            case 'tab1':
                if ('${game.status}' !== 'FINISHED') {
                    fetchBalanceAndDebt();
                    document.getElementById('tab1-image').style.display = 'block';
                }
                break;
            case 'tab2':
                fetchData();
                if (document.getElementById('tab1-image') != null) {
                    document.getElementById('tab1-image').style.display = 'none';
                }
                break;
            case 'tab3':
                updateCompetitorsData()
                break;
            default:
                break;
        }
    });

    // Загрузить данные при загрузке страницы
    if ('${game.status}' !== 'FINISHED') {
        fetchBalanceAndDebt();
    }


    if (document.getElementById('tab1-image') != null) {
        document.getElementById('tab1-image').style.display = 'block';
    }
</script>
</body>
</html>
