<!DOCTYPE html>
<#import "spring.ftl" as spring/>
<head>
    <meta charset="UTF-8">
    <title>Регистрация</title>
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

        .error {
            color: red;
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
                <img src="https://courseburg.ru/obuchenie_personala_v_organizacii/images/tild3937-3165-4638-b563-346662343136__clip-online-business.png"
                     class="img-fluid" alt="Sample image">
            </div>
            <div class="col-md-8 col-lg-6 col-xl-4 offset-xl-1">
                <form action="/sign-up" method="post">
                    <@spring.bind path="signUpForm"/>
                    <p class="error">${spring.status.errorMessage}</p>

                    <div class="d-flex flex-row align-items-center justify-content-center justify-content-lg-start mb-3">
                        <h2 class="fw-normal mb-0 me-3">Регистрация</h2>
                    </div>

                    <div class="form-outline mb-4">
                        <@spring.formInput "signUpForm.fullName" "class='form-control form-control-lg' id='form3Example3' placeholder='Введите ФИО'"/>
                        <@spring.showErrors "<br>" "error"/>
                    </div>

                    <div class="form-outline mb-4">
                        <@spring.formInput "signUpForm.email" "class='form-control form-control-lg' id='form3Example3' placeholder='Введите адресс электронной почты'"/>
                        <@spring.showErrors "<br>" "error"/>
                    </div>

                    <div class="form-outline mb-3">
                        <@spring.formPasswordInput "signUpForm.password" "class='form-control form-control-lg' id='form3Example4' placeholder='Введите пароль'"/>
                        <@spring.showErrors "<br>" "error"/>
                    </div>

                    <div class="text-center text-lg-start mt-10 pt-4">
                        <label>
                            <input class="btn btn-primary btn-lg"
                                   style="padding-left: 2.5rem; padding-right: 2.5rem;" type="submit"
                                   value="Зарегистрироваться">
                        </label>
                        <p class="small fw-bold mt-2 pt-1 mb-0">Уже есть аккаунт? <a
                                    href="/sign-in"
                                    class="link-danger">Войти</a></p>
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
