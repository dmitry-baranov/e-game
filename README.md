# Economic Concurrent Game

## Запуск через Docker

Требуется только Docker с поддержкой Docker Compose. Java и Maven на компьютере устанавливать не нужно.

```bash
docker compose up --build
```

После запуска приложение доступно по адресам:

- регистрация: http://localhost:8080/sign-up
- вход: http://localhost:8080/sign-in

Для остановки нажмите `Ctrl+C`. Чтобы остановить контейнеры, запущенные в фоне, выполните:

```bash
docker compose down
```

Данные PostgreSQL сохраняются в Docker volume. Для удаления контейнеров вместе с данными:

```bash
docker compose down -v
```

## Maven Wrapper

При наличии Java 17 проект можно собирать без установленного Maven:

```bash
./mvnw clean package
```

При первом запуске wrapper автоматически скачает Maven 3.8.8.
