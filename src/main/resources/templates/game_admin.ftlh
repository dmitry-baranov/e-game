<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-GLhlTQ8iRABdZLl6O3oVMWSktQOp6b7In1Zl3/Jr59b6EGGoI1aFkw7cmDA6j6gD" crossorigin="anonymous">
    <title>Управление игрой</title>
    <style>
        .btn {
            margin: 7px
        }

        th {
            vertical-align: middle;
        }

        html {
            position: relative;
            min-height: 100%;
        }

        .footer {
            position: absolute;
            bottom: 0;
            width: 100%;
            height: 60px; /* Высота футера */
        }
    </style>
</head>
<body onload="loadManufacturersData()">
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

<div class="container-fluid mt-5">
    <div class="row justify-content-center" style="margin-left: 50px; margin-right: 50px">
        <h2>Параметры игры:</h2>
        <div class="row" style="margin-top: 20px">
            <div class="col-4">
                <p>Модельное время(Длительность 1 суток) - ${game.timeUnit} мин</p>
                <p>Базовая себестоимость производства единицы продукции - ${game.baseCostPrice} руб</p>
                <p>Базовая цена рекламы - ${game.baseAdvertisementPrice} руб</p>
                <p>Цена создания единицы мощности производства - ${game.productPower} руб</p>
                <p>Срок эксплуатации абсолютно качественного товара - ${game.absoluteQualityProductLife} дней</p>
            </div>

            <div class="col-4">
                <p>Величина насыщения покупателя - ${game.purchaseLimit} ед.</p>
                <p>Максимальная ежедневная сумма средств покупателя - ${game.dailySpendingLimit} ед.</p>
                <p>Период для расчета привыкания покупателя - ${game.habitTrackingDays} дней</p>
                <p>Вес ассортимента - ${game.assortmentWeight}</p>
                <p>Вес рекламы - ${game.advertisementWeight}</p>
                <p>Вес качества - ${game.qualityWeight}</p>
                <p>Вес привыкания покупателя к производителю - ${game.habitWeight}</p>
            </div>

            <div class="col-4">
                <p>Налог с продаж - ${game.salesTax}%</p>
                <p>Процентная ставка инвестиционного кредита - ${game.interestRateInvestmentCredit}%</p>
                <p>Срок инвестиционного кредита - ${game.investmentCreditTermMonths} мес</p>
                <p>Процентная ставка оборотного кредита - ${game.interestRateBusinessCredit}%</p>
            </div>
        </div>
        <#if experimentEnabled && experimentRunId??>
            <p><a href="/admin/bot-analytics/runs/${experimentRunId?c}?from=game">Разбор игры ботов</a></p>
        <#elseif !experimentEnabled && game.status == 'FINISHED'>
            <p><a href="/game/${game.id?c}/analysis">Разбор завершённой игры</a></p>
        </#if>
        <#if game.status != 'FINISHED'>
            <p>Торговый день: ${game.currentDay}. <#if !startedTradingSessions>Таймер на паузе; для продолжения нажмите «Запустить торговые сессии».</#if></p>
            <div class="col-md-12" style="margin-top: 50px">
                <h2>Данные производителей:</h2>
                <div class="table-responsive" style="height: 400px">
                    <table style="margin-top: 25px" id="dataTable" class="table table-striped">
                        <thead>
                        <tr>
                            <th>Имя</th>
                            <th>Баланс</th>
                            <th>Сумма инвестиционного кредита</th>
                            <th>День начала выпуска продукции</th>
                            <th>Число дней выпуска продукции</th>
                            <th>Количество продукции</th>
                            <th>Цена(ед)</th>
                            <th>Себестоимость(ед)</th>
                            <th>Ассортимент</th>
                            <th>Индекс качества(0-1)</th>
                            <th>Мощность производства(ед/сутки)</th>
                            <th>День начала рекламы</th>
                            <th>День завершения рекламы</th>
                            <th>Стоимость рекламы</th>
                            <th>Индекс интенсивности рекламы(1-7)</th>
                            <th>Сумма оборотного кредита</th>
                            <th>Процентная ставка оборотного кредита</th>
                        </tr>
                        </thead>
                        <tbody>

                        </tbody>
                    </table>
                </div>

                <div class="row">
                    <div class="col-md-4 d-flex justify-content-center">
                        <button id="refreshButton" class="btn btn-primary">Обновить таблицу</button>
                    </div>

                    <div class="col-md-4 d-flex justify-content-center">
                        <button id="schedulerButton" class="btn btn-success" onclick="toggleScheduler()">
                            <#if startedTradingSessions == true>Остановить торговые сессии<#else>Запустить торговые сессии</#if>
                        </button>
                    </div>

                    <div class="col-md-4 d-flex justify-content-center">
                        <form method="post" action="/game/${game.id}/finish">
                            <button id="finishGame" class="btn btn-danger">
                                Завершить игру
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        <#else>
            <div class="col-md-12" style="margin-top: 50px">
                <h2>Результат игры:</h2>
                <div class="table-responsive" style="height: 374px">
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
        </#if>
    </div>
</div>
<footer class="footer d-flex flex-column flex-md-row text-center text-md-start justify-content-between py-4 px-4 px-xl-5 bg-primary">
    <div class="text-white mb-3">
        Copyright © 2023. All rights reserved.
    </div>
</footer>

<script>
    function loadManufacturersData() {
        // Выполнить HTTP-запрос для загрузки данных
        fetch('/game/${game.id}/manufacturers-parameters')
            .then(response => response.json())
            .then(data => {
                // Получить ссылки на элементы таблицы
                const table = document.getElementById('dataTable');
                const tbody = table.getElementsByTagName('tbody')[0];

                // Очистить содержимое таблицы перед добавлением новых данных
                tbody.innerHTML = '';

                // Добавить данные в таблицу
                data.forEach(item => {
                    const row = tbody.insertRow();
                    // const cell1 = row.insertCell(0);
                    // cell1.innerHTML = item.id;
                    const cell0 = row.insertCell(0);
                    cell0.innerHTML = item.fullName;
                    const cell1 = row.insertCell(1);
                    cell1.innerHTML = item.balance;
                    const cell2 = row.insertCell(2);
                    cell2.innerHTML = item.investmentCreditAmount;
                    const cell3 = row.insertCell(3);
                    if (item.startDate != null) {
                        cell3.innerHTML = item.startDate + 1;
                    } else {
                        cell3.innerHTML = item.startDate;
                    }
                    const cell4 = row.insertCell(4);
                    cell4.innerHTML = item.timeToMarket;
                    const cell5 = row.insertCell(5);
                    cell5.innerHTML = item.productCount;
                    const cell6 = row.insertCell(6);
                    cell6.innerHTML = item.price;
                    const cell7 = row.insertCell(7);
                    cell7.innerHTML = item.costPrice;
                    const cell8 = row.insertCell(8);
                    cell8.innerHTML = item.assortment;
                    const cell9 = row.insertCell(9);
                    cell9.innerHTML = item.qualityIndex;
                    const cell10 = row.insertCell(10);
                    cell10.innerHTML = item.productionCapacityPerDay;
                    const cell11 = row.insertCell(11);
                    cell11.innerHTML = item.advertisingStartDate;
                    const cell12 = row.insertCell(12);
                    cell12.innerHTML = item.advertisingEndDate;
                    const cell13 = row.insertCell(13);
                    cell13.innerHTML = item.advertisingCost;
                    const cell14 = row.insertCell(14);
                    cell14.innerHTML = item.advertisingIntensityIndex;
                    const cell15 = row.insertCell(15);
                    cell15.innerHTML = item.businessCreditAmount;
                    const cell16 = row.insertCell(16);
                    cell16.innerHTML = item.interestRateBusinessCredit;
                });
            });

    }
</script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.0/umd/popper.min.js"
        integrity="sha384-cs/chFZiN24E4KMATLdqdvsezGxaGsi4hLGOzlXwp5UZB1LY//20VyM2taTB4QvJ"
        crossorigin="anonymous"></script>
<script src="https://unpkg.com/axios/dist/axios.min.js"></script>
<script>
    // Обработчик нажатия на кнопку "Обновить таблицу"
    const refreshButton = document.getElementById('refreshButton');
    refreshButton.addEventListener('click', loadManufacturersData);
</script>
<script>
    let startedTradingSessions = ${startedTradingSessions?c};
    let schedulerPeriod = ${game.timeUnit};

    function toggleScheduler() {
        if (!startedTradingSessions) {
            startScheduler();
        } else {
            stopScheduler();
        }
    }

    function startScheduler() {
        fetch("/game/${game.id}/trading-sessions/start/" + schedulerPeriod)
            .then(response => response.text())
            .then(result => {
                console.log(result);
                startedTradingSessions = true;
                document.getElementById("schedulerButton").innerText = "Остановить торговые сессии";
            })
            .catch(error => console.error("Error:", error));
    }

    function stopScheduler() {
        fetch("/game/${game.id}/trading-sessions/stop")
            .then(response => response.text())
            .then(result => {
                console.log(result);
                startedTradingSessions = false;
                document.getElementById("schedulerButton").innerText = "Запустить торговые сессии";
            })
            .catch(error => console.error("Error:", error));
    }
</script>
</body>
</html>
