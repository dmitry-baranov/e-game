<!DOCTYPE html>
<head xmlns="http://www.w3.org/1999/html">
    <title>Авторизация</title>
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
<body>
<section class="vh-100">
    <nav class="navbar navbar-expand-lg navbar-dark bg-primary">
        <div class="container-fluid">
            <a class="navbar-brand" href="/">Economic Concurrent Game</a>
            <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav"
                    aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
        </div>
    </nav>

    <div class="container-fluid h-custom">
        <div class="row d-flex justify-content-center align-items-center h-100">
            <div class="col-md-9 col-lg-6 col-xl-5">
                <img src="https://uploads-ssl.webflow.com/5d5b41242fb63c7dd72ca703/5d7093ed6d5a38317cdb06f3_undraw_unlock_24mb.svg"
                     class="img-fluid" alt="Sample image">
            </div>
            <div class="col-md-8 col-lg-6 col-xl-4 offset-xl-1">
                <form action="/sign-in" method="post">
                    <div class="d-flex flex-row align-items-center justify-content-center justify-content-lg-start mb-3">
                        <h2 class="fw-normal mb-0 me-3">Вход в систему</h2>
                    </div>

                    <!-- Email input -->
                    <div class="form-outline mb-4">
                        <input type="email" name="email" id="form3Example3" class="form-control form-control-lg"
                               placeholder="Введите адресс электронной почты"/>
                        <#--                        <label class="form-label" for="form3Example3">Адресс электронной почты</label>-->
                    </div>

                    <!-- Password input -->
                    <div class="form-outline mb-3">
                        <input type="password" name="password" id="form3Example4" class="form-control form-control-lg"
                               placeholder="Введите пароль"/>
                        <#--                        <label class="form-label" for="form3Example4">Пароль</label>-->
                    </div>

                    <div class="d-flex justify-content-between align-items-center">
                        <!-- Checkbox -->
                        <div class="form-check mb-0">
                            <input class="form-check-input me-2" type="checkbox" name="rememberMe"
                                   id="form2Example3"/>
                            <label class="form-check-label" for="form2Example3">
                                Запомни меня
                            </label>
                        </div>
                    </div>

                    <div class="text-center text-lg-start mt-10 pt-4">
                        <label>
                            <input class="btn btn-primary btn-lg"
                                   style="padding-left: 2.5rem; padding-right: 2.5rem;" type="submit" value="Войти">
                        </label>
                        <p class="small fw-bold mt-2 pt-1 mb-0">Еще не зарегистрированы? <a
                                    href="/sign-up"
                                    class="link-danger">Регистрация</a></p>
                    </div>

                </form>
            </div>
        </div>
    </div>

    <footer class="footer d-flex flex-column flex-md-row text-center text-md-start justify-content-between py-4 px-4 px-xl-5 bg-primary">
        <div class="text-white mb-3">
            Copyright © 2023. All rights reserved.
        </div>
    </footer>
</section>
</body>
</html>
