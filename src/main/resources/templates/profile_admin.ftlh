<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0-alpha1/dist/css/bootstrap.min.css" rel="stylesheet"
          integrity="sha384-GLhlTQ8iRABdZLl6O3oVMWSktQOp6b7In1Zl3/Jr59b6EGGoI1aFkw7cmDA6j6gD" crossorigin="anonymous">
    <title>Профиль Админа</title>
    <style>
        .container {
            display: flex;
            flex-direction: column;
            align-items: center;
            /*min-height: 100vh;*/
            justify-content: center;
        }

        .profile {
            background-color: white;
            border-radius: 10px;
            box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
            padding: 30px;
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
<body onload="loadAllGames()">
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
                    <a class="nav-link" href="/logout">Выйти</a>
                </li>
            </ul>
        </div>
    </div>
</nav>

<div class="container-fluid" style="height: 528px; margin: 120px auto auto;">
    <div class="row justify-content-center" style="margin-left: 50px; margin-right: 50px">
        <div class="col-4">
            <div class="profile">
                <div style="text-align: center;">
                    <img class="photo" width="320" height="320" style="border-radius: 50%"
                         src="https://courseburg.ru/obuchenie_personala_v_organizacii/images/tild3032-3831-4363-a263-653865336130__clip-school-assignme.png"
                         alt="">
                    <p class="fw-bold h4 mt-3">Имя: ${account.fullName!}</p>
                    <p class="fw-bold h4 mt-3">Почта: ${account.email!}</p>
                    <div style="margin-top:20px">
                        <a class="btn btn-outline-primary" href="/profile/edit" role="button">Редактировать профиль</a>
                    </div>
                </div>
            </div>
        </div>
        <div class="col-8">
            <div class="profile">
                <h2>Мои игры</h2>
                <div class="table-responsive" style="height: 374px">
                    <table style="margin-top: 25px" id="dataTable" class="table table-striped">
                        <thead>
                        <tr>
                            <th>Название</th>
                            <th>Начало</th>
                            <th>Конец</th>
                            <th>Статус</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>

                        </tbody>
                    </table>
                </div>
                <div class="text-center" style="margin-top: 10px">
                    <a class="btn btn-primary" href="/game" role="button">Новая игра</a>
                </div>
            </div>
        </div>
    </div>
</div>

<footer class="footer d-flex flex-column flex-md-row text-center text-md-start justify-content-between py-4 px-4 px-xl-5 bg-primary">
    <div class="text-white mb-3">
        Copyright © 2023. All rights reserved.
    </div>
</footer>

<script>
    function loadAllGames() {
        fetch('/games')
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
                    const cell0 = row.insertCell(0);
                    cell0.innerHTML = item.name;
                    const cell1 = row.insertCell(1);
                    cell1.innerHTML = new Date(item.startDate).toLocaleString();
                    const cell2 = row.insertCell(2);
                    if (item.endDate != null) {
                        cell2.innerHTML = new Date(item.endDate).toLocaleString();
                    }
                    const cell3 = row.insertCell(3);
                    cell3.innerHTML = item.status;
                    const cell4 = row.insertCell(4)
                    const button = document.createElement('button');
                    button.innerText = 'Перейти';
                    button.classList = 'btn btn-outline-primary';
                    button.addEventListener('click', function () {
                        window.location.href = '/game/' + item.id;
                    })
                    cell4.appendChild(button);
                });
            });
    }
</script>
</body>
</html>
