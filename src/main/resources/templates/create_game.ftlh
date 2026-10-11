<!DOCTYPE html>
<head xmlns="http://www.w3.org/1999/html">
    <title>Создание игры</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-GLhlTQ8iRABdZLl6O3oVMWSktQOp6b7In1Zl3/Jr59b6EGGoI1aFkw7cmDA6j6gD" crossorigin="anonymous">
    <style>
        .divider:after,
        .divider:before {
            content: "";
            flex: 1;
            height: 1px;
            background: #eee;
        }

        .h-custom {
            height: calc(100% - 73px);
        }

        @media (max-width: 450px) {
            .h-custom {
                height: 100%;
            }
        }
    </style>
</head>
<body>

<section class="vh-100">
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

    <div class="container-fluid h-custom" style="margin-top: 30px; margin-bottom:650px">
        <div class="row d-flex justify-content-center align-items-center h-100">
            <div class="col-md-8 col-lg-6 col-xl-4 offset-xl-1">
                <form action="/game" method="post">
                    <div class="d-flex flex-row align-items-center justify-content-center justify-content-lg-start mb-3">
                        <h2 class="fw-normal mb-0 me-3">Да начнется новая игра!</h2>
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="text" id="name" name="name"
                               placeholder="Наименование игры">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="1" min="1" id="timeUnit"
                               name="timeUnit"
                               placeholder="Модельное время в минутах(сутки = ? минут)">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any"
                               name="interestRateInvestmentCredit"
                               placeholder="Процентная ставка инвестиционного кредита">
                    </div>
                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any"
                               name="investmentCreditTermMonths"
                               placeholder="Срок инвестиционного кредита в месяцах">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any"
                               name="interestRateBusinessCredit" placeholder="Процентная ставка бизнес-кредита">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any" name="salesTax"
                               placeholder="Процент налога с продаж">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any" name="baseCostPrice"
                               placeholder="Себестоимость единицы продукции">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any" name="productPower"
                               placeholder="Цена создания единицы мощности производства">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any"
                               name="baseAdvertisementPrice"
                               placeholder="Цена рекламы">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" name="purchaseLimit"
                               placeholder="Величина насыщения покупателя">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" step="any" name="dailySpendingLimit"
                               placeholder="Максимальная ежедневная сумма средств покупателя">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" name="absoluteQualityProductLife"
                               placeholder="Срок эксплуатации абсолютно качественного товара в днях">
                    </div>

                    <div class="form-outline mb-4">
                        <input class="form-control form-control-lg" type="number" name="habitTrackingDays"
                               placeholder="Количество дней для расчета привыкания покупателя">
                    </div>

                    <h5>Выберите веса для модели покупателя:</h5>
                    <small style="margin-top: 10px">Чем выше вес, тем большее влияние будет оказывать соответствующий
                        параметр при выборе производителя.</small>
                    <div style="margin-top: 10px">
                        <div class="form-outline mb-4">
                            <input class="form-control form-control-lg validate-weights" required type="number" min="0"
                                   max="1" step="0.01"
                                   id="assortmentWeight" name="assortmentWeight"
                                   placeholder="Вес ассортимента">
                        </div>
                        <div class="form-outline mb-4">
                            <input class="form-control form-control-lg validate-weights" required type="number" min="0"
                                   max="1" step="0.01"
                                   id="qualityWeight" name="qualityWeight"
                                   placeholder="Вес качества">
                        </div>
                        <div class="form-outline mb-4">
                            <input class="form-control form-control-lg validate-weights" required type="number" min="0"
                                   max="1" step="0.01"
                                   id="advertisementWeight" name="advertisementWeight"
                                   placeholder="Вес рекламы">
                        </div>
                        <div class="form-outline mb-4">
                            <input class="form-control form-control-lg validate-weights" required type="number" min="0"
                                   max="1" step="0.01"
                                   id="habitWeight" name="habitWeight"
                                   placeholder="Вес привыкания покупателя к производителю">
                        </div>
                        <div class="error" style="color: red" id="error"></div>
                    </div>


                    <div class="form-outline mb-3" style="margin-top: 10px">
                        <h5>Выберите участников игры из списка:</h5>
                        <p class="text-muted">Аккаунты экспериментальных ботов создаются автоматически и здесь не отображаются.</p>
                        <select id="accounts" class="form-control form-control-lg" multiple required name="accountIds">
                            <#list accounts as account>
                                <option value="${account.id}">${account.fullName!} (${account.email!})</option>
                            </#list>
                        </select>
                    </div>

                    <#if errorMessage??>
                        <div class="alert alert-danger" role="alert">
                            ${errorMessage}
                        </div>
                    </#if>

                    <div class="text-center text-lg-start mt-10 pt-6">
                        <label>
                            <input class="btn btn-primary btn-lg"
                                   style="padding-left: 2.5rem; padding-right: 2.5rem;" type="submit" value="Создать">
                        </label>
                    </div>
                </form>
            </div>
            <div class="col-md-9 col-lg-6 col-xl-5">
                <img src="https://sun9-9.userapi.com/impg/M6YIz5fS_4YMoiHLVzrebf_YFdge0aNdmDF1vg/Vf1Cc5-ixmw.jpg?size=1087x840&quality=96&sign=d607b47217efe3197ce2dd2e9b8ca53e&type=album"
                     class="img-fluid" alt="Sample image">
            </div>
        </div>
    </div>
    <footer style="height: 60px"
            class="d-flex flex-column flex-md-row text-center text-md-start justify-content-between py-4 px-4 px-xl-5 bg-primary">
        <div class="text-white mb-3">
            Copyright © 2023. All rights reserved.
        </div>
    </footer>
</section>

<script>
    const assortmentWeightInput = document.getElementById('assortmentWeight');
    const qualityWeightInput = document.getElementById('qualityWeight');
    const advertisementWeightInput = document.getElementById('advertisementWeight');
    const habitWeightInput = document.getElementById('habitWeight');
    const error = document.getElementById('error');
    const errorElement = document.querySelector('.error');

    const validateInputs = () => {
        const assortmentWeight = +assortmentWeightInput.value || 0;
        const qualityWeight = +qualityWeightInput.value || 0;
        const advertisementWeight = +advertisementWeightInput.value || 0;
        const habitWeight = +habitWeightInput.value || 0;
        const sum = assortmentWeight + qualityWeight + advertisementWeight + habitWeight;

        if (sum !== 1) {
            error.innerHTML = 'Сумма весов должна быть равна 1';
            error.classList.add('text-danger');
            return false;
        } else {
            error.innerHTML = '';
            error.classList.remove('text-danger');
            return true;
        }
    }

    assortmentWeightInput.addEventListener('input', validateInputs);
    qualityWeightInput.addEventListener('input', validateInputs);
    advertisementWeightInput.addEventListener('input', validateInputs);
    habitWeightInput.addEventListener('input', validateInputs);
</script>

</body>
</html>
