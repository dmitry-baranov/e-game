<!DOCTYPE html>
<head xmlns="http://www.w3.org/1999/html">
    <title>Открытие предприятия</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-GLhlTQ8iRABdZLl6O3oVMWSktQOp6b7In1Zl3/Jr59b6EGGoI1aFkw7cmDA6j6gD" crossorigin="anonymous">
    <style>
        html {
            position: relative;
            min-height: 100%;
        }

        .footer {
            position: absolute;
            bottom: 0;
            width: 100%;
            height: 60px;
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

<div class="container" style="margin: 100px auto auto;">
    <p><a class="btn btn-outline-primary" href="/game/${game.id}/strategies">Посмотреть стартовые сценарии</a>
        Выбор курса не заполняет форму и не берёт кредит.</p>
    <form class="row g-3 color-rect-border" method="post" action="/game/${game.id}/initial-production-parameters">
        <h1 style="text-align: center">Определите параметры открываемого предприятия</h1>
        <div class="row g-3">
            <h4 style="margin-top: 25px">Параметры производимой продукции:</h4>
            <div class="col-md-4">
                <label for="productCount" class="form-label">Количество продукции</label>
                <input type="number" class="form-control" min="1" name="productCount" id="productCount" value="1">
            </div>
            <div class="col-md-4">
                <label for="assortment" class="form-label">Ассортимент</label>
                <input type="number" class="form-control" min="1" name="assortment" id="assortment" value="1">
            </div>
            <div class="col-md-4">
                <label for="qualityIndex" class="form-label">Индекс качества</label>
                <input type="number" class="form-control" min="0" max="1" step="any" name="qualityIndex"
                       id="qualityIndex"
                       value="0">
            </div>
            <div class="col-md-6">
                <label for="price" class="form-label">Цена единицы продукции</label>
                <input type="number" class="form-control" min="0" step="any" name="price" id="price" value="0">
            </div>
            <div class="col-md-6">
                <label for="productionCapacityPerDay" class="form-label">Мощность производства(ед/сутки)</label>
                <input type="number" class="form-control" min="1" name="productionCapacityPerDay"
                       id="productionCapacityPerDay" value="1">
            </div>
        </div>

        <div class="row g-3">
            <h4>Параметры рекламы:</h4>
            <div class="col-md-6">
                <label for="advertisingIntensityIndex" class="form-label">Индекс интенсивности рекламы</label>
                <input type="number" class="form-control" min="0" max="7" name="advertisingIntensityIndex"
                       id="advertisingIntensityIndex" value="0">
            </div>
            <div class="col-md-6">
                <label for="advertisingDays" class="form-label">Количество дней рекламы</label>
                <input type="number" class="form-control" min="0" max="" name="advertisingDays" id="advertisingDays"
                       value="0">
            </div>
        </div>

        <div class="row g-3">
            <h4>Рассчитываемые экономические показатели:</h4>
            <div class="col-md-3">
                <label for="investmentCreditAmount" class="form-label">Сумма инвестиционного кредита</label>
                <input value="0" type="number" class="form-control" min="0" step="any" name="investmentCreditAmount"
                       id="investmentCreditAmount">
            </div>
            <div class="col-md-3">
                <label for="advertisingCost" class="form-label">Затраты на рекламу</label>
                <input value="0" type="number" class="form-control" step="any" name="advertisingCost"
                       id="advertisingCost">
            </div>
            <div class="col-md-3">
                <label for="costPrice" class="form-label">Себестоимость единицы продукции</label>
                <input value="0" type="number" class="form-control" step="any" min="0" name="costPrice" id="costPrice">
            </div>
            <div class="col-md-3">
                <label for="timeToMarket" class="form-label">Число дней выпуска продукции</label>
                <input value="0" type="number" class="form-control" min="0" name="timeToMarket" id="timeToMarket">
            </div>
        </div>

        <#if errorMessage??>
            <div class="alert alert-danger" role="alert">
                ${errorMessage}
            </div>
        </#if>

        <div style="text-align: center" class="col-12">
            <button style="text-align: center" type="submit" class="btn btn-primary btn-lg">Отправить</button>
        </div>
    </form>
</div>
<footer class="footer d-flex flex-column flex-md-row text-center text-md-start justify-content-between py-4 px-4 px-xl-5 bg-primary">
    <div class="text-white mb-3">
        Copyright © 2023. All rights reserved.
    </div>
</footer>
<script type="text/javascript">


    var productCount = document.getElementById('productCount');
    productCount.addEventListener('input', calculate);
    productCount.addEventListener('input', calculateMaxOfAdvertisingDays);
    var qualityIndex = document.getElementById('qualityIndex');
    qualityIndex.addEventListener('input', calculate);
    var assortment = document.getElementById('assortment');
    assortment.addEventListener('input', calculate);
    var costPrice = document.getElementById('costPrice');
    costPrice.addEventListener('input', calculate);

    var investmentCreditAmount = document.getElementById('investmentCreditAmount');
    investmentCreditAmount.addEventListener('input', calculate);

    var productionCapacityPerDay = document.getElementById('productionCapacityPerDay');
    productionCapacityPerDay.addEventListener('input', calculate);
    productionCapacityPerDay.addEventListener('input', calculateMaxOfAdvertisingDays);
    var advertisingIntensityIndex = document.getElementById('advertisingIntensityIndex');
    advertisingIntensityIndex.addEventListener('input', calculate);
    var advertisingDays = document.getElementById('advertisingDays');
    advertisingDays.addEventListener('input', calculate);

    var M = 10;

    // количества дней рекламы циклом производства (time to market)
    function calculateMaxOfAdvertisingDays() {
        let timeToMarket = Math.ceil(Number(productCount.value) / Number(productionCapacityPerDay.value));
        document.getElementById('timeToMarket').value = timeToMarket.toString();
        advertisingDays.setAttribute("max", timeToMarket.toString())
    }

    var advertisingCost = document.getElementById('advertisingCost');
    advertisingCost.addEventListener('input', calculate);

    /**
     * Стоимость рекламы = базовая стоимость рекламы * индекс интенсивности рекламы(1-7) * кол-во дней рекламы
     */
    function calculate() {
        if (Number(assortment.value) > Number(productCount.value)) assortment.value = productCount.value;
        var n = Number(productCount.value) / Number(assortment.value)
        var k = 1
        if (n > 1000) {
            k = 0.7
        } else if (n > 100) {
            k = 0.9
        }
        var baseCostPrice = "${game.baseCostPrice}".replace(/\s/g, '').replace(/,/, '.');
        costPrice.value = (Number(k) * Number(baseCostPrice) * Number(qualityIndex.value)).toFixed(2);

        var productPower = "${game.productPower}".replace(/\s/g, '').replace(/,/, '.');
        var baseAdvertisementPrice = "${game.baseAdvertisementPrice}".replace(/\s/g, '').replace(/,/, '.');
        var productionCreation = (Number(productPower) * Math.ceil(Number(productionCapacityPerDay.value) / M)).toFixed(2);
        var advertisement = (Number(baseAdvertisementPrice) * Number(advertisingIntensityIndex.value) * Number(advertisingDays.value)).toFixed(2);
        advertisingCost.value = advertisement
        var initialProduction = (Number(productCount.value) * Number(costPrice.value)).toFixed(2)
        investmentCreditAmount.value = (Number(productionCreation) + Number(advertisement) + Number(initialProduction)).toFixed(2);
    }

</script>
</body>
</html>
