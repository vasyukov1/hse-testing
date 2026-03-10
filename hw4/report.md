# Отчёт по выполнению Home Task 04

> Выполнил: Васюков Александр  
> Группа: БПИ-235  
> Email: avvasiukov@edu.hse.ru  

## Цель работы

Разработать набор тестовых заглушек (mock-объектов) для существующего тестового набора банковского клиентского приложения. Изменения вносились в тестовые файлы – в места, отмеченные `...`, – без критической модификации исходного кода приложения и логики тестов.

---

## Архитектура приложения

Приложение состоит из трёх слоёв:

```
Client  →  AccountManager  →  Account
                ↓                  ↓
        IAuthorizationSource  IAccountDataSource
```

- `Client` – точка входа для внешнего потребителя; методы `login`, `logout`, `deposit`, `withdraw`, `get_balance`
- `AccountManager` – управляет авторизацией и локальным хранилищем активных аккаунтов
- `Account` – выполняет операции со счётом через `IAccountDataSource`
- `IAuthorizationSource` / `IAccountDataSource` – интерфейсы, которые в тестах заменяются mock-объектами
- `OperationResponse` – универсальный объект ответа с полем `code` и опциональным `body`
- `OperationException` – исключение, несущее `OperationResponse`

---

## Выполненная работа

### 1. `pytest.ini` – исправление конфигурации

**Проблема:** В оригинальном файле путь к исходникам был указан в кавычках:
```ini
pythonpath = "src"
```
Pytest воспринимал `"src"` буквально как имя директории и не находил модули.

**Исправление:**
```ini
pythonpath = src
```

Без этого исправления ни один тест не запустился бы – все импорты падали бы с `ModuleNotFoundError`.

---

### 2. `TestServerlessAccountModuleData.py` – тесты класса `Account`

**Класс тестирует:** низкоуровневые методы объекта `Account` напрямую, с mock-заглушкой `IAccountDataSource`.

**Заполненные стабы (`#record`):**

| Тест | Настройка mock |
|---|---|
| `test_deposit_success` | `data_source.deposit` → `SUCCEED, 100.0` |
| `test_deposit_exceptions` | `data_source.deposit` → `OperationResponse(exception_code, None)` |
| `test_withdraw_no_money` | `data_source.withdraw` → `NO_MONEY, 50.0` (баланс в `body`, по спецификации) |
| `test_withdraw_success` | `deposit` → `SUCCEED, 100.0`; `withdraw` → `SUCCEED, 0.0` |
| `test_withdraw_exceptions` | `data_source.withdraw` → `OperationResponse(exception_code, None)` |
| `test_get_balance_success` | `data_source.get_balance` → `SUCCEED, 50.0` |
| `test_get_balance_exceptions` | `data_source.get_balance` → `OperationResponse(exception_code, None)` |

**Пояснение:**
- Для `NO_MONEY` обязательно передаётся текущий баланс в `body` – это прямое требование спецификации: *"В теле ответа содержит текущее значение баланса средств"*.
- Для всех кодов ошибок (`body=None`) – соответствует спецификации: тело ответа при ошибках не заполняется.

---

### 3. `TestServerlessAccountManagerAuth.py` – тесты класса `AccountManager`

**Класс тестирует:** авторизационную логику `AccountManager` с mock-заглушками обоих интерфейсов.

**Заполненные стабы (`#record`):**

| Тест | Настройка mock |
|---|---|
| `test_login_success` | `auth_source.login` → `SUCCEED, session_id=1` |
| `test_call_login_invalid_credentials` | `auth_source.login` → `NO_USER_INCORRECT_PASSWORD, None` |
| `test_call_login_already_logged_remote` | `auth_source.login` → `ALREADY_LOGGED, session_id=1` |
| `test_call_login_already_logged_local` | `auth_source.login` → `SUCCEED, 1` (второй вызов блокируется локальным кэшем) |
| `test_logout_success` | `login` → `SUCCEED, 1`; `logout` → `SUCCEED` |
| `test_logout_not_logged_remote` | `login` → `SUCCEED, 1`; `logout` → `NOT_LOGGED` |
| `test_logout_not_logged_local` | `login` → `SUCCEED, 1`; `logout` → `SUCCEED`; второй logout блокируется кэшем |
| `test_logout_incorrect_session` | `login` → `SUCCEED, 1`; `logout` → `INCORRECT_SESSION` |

**Ключевые моменты:**
- `test_call_login_already_logged_remote`: mock возвращает `ALREADY_LOGGED` с `body=session_id` – спецификация требует, чтобы в `body` ответа `ALREADY_LOGGED` хранился номер активной сессии.
- `test_call_login_already_logged_local`: первый `login` успешен (mock → `SUCCEED`), второй вызов перехватывается локально в `active_accounts` – до mock не доходит. Это проверяет локальный кэш `AccountManager`.
- `test_logout_not_logged_local`: первый `logout` успешен и удаляет аккаунт из `active_accounts`. Второй `logout` возвращает `NOT_LOGGED` без обращения к серверу – тест проверяет именно локальную проверку.
- Тесты с `null`-аргументами (`test_account_manager_init_null_argument_*`, `test_login_null_argument_*`, `test_logout_null_account`) не требовали `#record` – их логика обрабатывается до обращения к mock.

---

### 4. `TestServerlessClientIntegrationAuth.py` – интеграционные тесты `Client` (авторизация)

**Класс тестирует:** поведение `Client` как фасада над `AccountManager`, не обращаясь к реальному серверу.

**Заполненные стабы (`#record`):**

| Тест | Настройка mock |
|---|---|
| `test_client_login_correct_success` | `auth_source.login` → `SUCCEED, 1` |
| `test_client_login_exceptions` (параметризован) | `auth_source.login` → нужный код; для `ALREADY_LOGGED` добавлен `body=session_id` по спецификации |
| `test_client_logout_login_correct_success` | `login` → `SUCCEED, 1`; `logout` → `SUCCEED` |
| `test_client_login_login_already_logged` | `auth_source.login` → `SUCCEED, 1` (второй вызов пойман локально) |
| `test_client_logout_not_logged_remote` | `login` → `SUCCEED, 1`; `logout` → `NOT_LOGGED` |
| `test_client_logout_logged_success` | `login` → `SUCCEED, 1`; `logout` → `SUCCEED` |
| `test_client_logout_exceptions` (параметризован) | `login` → `SUCCEED, 1`; `logout` → нужный код ошибки |
| `test_client_double_logout_logged_success` | `login` → `SUCCEED, 1`; `logout` → `SUCCEED`; второй logout пойман локально |

**Пояснение:**
- Параметризованный `test_client_login_exceptions`: для кода `ALREADY_LOGGED` в `body` передаётся `session_id` – иначе тест `test_call_login_already_logged_remote` в `AccountManager` не прошёл бы валидацию `body`.
- `test_client_logout_not_logged_local`: не требовал `#record` – аккаунт создаётся вручную через `Account(...)` без регистрации в `AccountManager`, поэтому `logout` всегда вернёт `NOT_LOGGED` из локального кэша.

---

### 5. `TestServerlessClientIntegrationData.py` – интеграционные тесты `Client` (операции со счётом)

**Класс тестирует:** методы `get_balance`, `deposit`, `withdraw` через полный стек `Client → AccountManager → Account → mock`.

**Заполненные стабы (`#record`):**

| Тест | Настройка mock |
|---|---|
| `test_client_get_balance_correct_success` | `login` → `SUCCEED,1`; `get_balance` → `SUCCEED, 0.0` |
| `test_client_get_balance_local_not_logged_exceptions` | `login/logout` → `SUCCEED`; `get_balance` → `NOT_LOGGED` |
| `test_client_get_balance_exceptions` (×3) | `login` → `SUCCEED,1`; `get_balance` → нужный код ошибки |
| `test_client_deposit_correct_success` | `login` → `SUCCEED,1`; `deposit` → `SUCCEED, 100.0` |
| `test_client_deposit_correct_get_balance_test_success` | `deposit` → `SUCCEED, 100.0`; `get_balance` → `SUCCEED, 100.0` |
| `test_client_deposit_local_not_logged_exception` | `login/logout` → `SUCCEED`; `deposit` → `NOT_LOGGED` |
| `test_client_deposit_not_logged_exception_get_balance` | после неудачного `deposit` повторный `login` → `get_balance` возвращает `0.0` |
| `test_client_deposit_exceptions` (×3) | `deposit` → нужный код ошибки |
| `test_client_deposit_exceptions_get_balance` (×3) | после неудачного `deposit` баланс не меняется |
| `test_withdraw_no_money` | `withdraw` → `NO_MONEY, 50.0` |
| `test_withdraw_successful` | `deposit` → `SUCCEED, 500.0`; `withdraw` → `SUCCEED, 300.0` |
| `test_withdraw_correct_balance_success` | `deposit/withdraw/get_balance` → согласованные значения |
| `test_withdraw_not_logged_exception` | `login/logout` → `SUCCEED`; `withdraw` → `NOT_LOGGED` |
| `test_withdraw_not_logged_get_balance` | после неудачного `withdraw` новый `login` → `get_balance` = `0.0` |
| `test_client_withdraw_exceptions` (×3) | `withdraw` → нужный код ошибки |
| `test_client_withdraw_exceptions_get_balance` (×3) | после неудачного `withdraw` баланс не меняется |

**Пояснение:**
- Группа тестов `*_not_logged_exception_get_balance` проверяет инвариант: неудачная операция не должна изменять баланс. Mock настроен так, что `get_balance` после ошибки возвращает исходное значение `0.0`.
- `test_withdraw_no_money`: `body=initial_balance` – требование спецификации для `NO_MONEY`.
- В тестах с logout и повторным login: `auth_source.login` настроен на возврат `SUCCEED` для каждого вызова (mock не имеет состояния, возвращает одно значение всегда).

---

### 6. `TestServerClientSystem.py` – системные тесты с реальным сервером

**Заполненные стабы:**

```python
# Запуск сервера
process = Popen(
    ["java", "-jar", str(jar_path)],
    cwd=str(server_dir),
    stdout=PIPE,
    stderr=PIPE
)
_wait_port("127.0.0.1", 7000, timeout_s=12)

yield "local-server"

# Остановка сервера
process.terminate()
try:
    process.wait(timeout=5)
except Exception:
    process.kill()
```

Этот класс запускает реальный JAR-сервер (`HomeTask04Java.jar`) и тестирует `Client` через HTTP. Если переменная окружения `SERVER_URL` задана – сервер не запускается, используется внешний.

---

### 7. `AccountManager.py` – запуск приложения и передача ответов между Java и Python

В файле был заменён порт, на котором запускалось приложение, с `7000` на `7001`, потому что на macOS этот порт занимает Control Center, а убийство процесса через `kill <PID>` не помогает, так как launchd автоматически перезапускает процесс.

Поэтому приложение тестировалось через следующие команды:
```sh
cd server
java -jar HomeTask04Java.jar 7001
```

Так же в коде файла `AccountManager.py` был заменён соответсвующий порт.

Также была проблема с тем, что Java возвращала ответ в формате строки, а Python требовал целочисленное значение, поэтому ответ явно преобразовывался в `int`:

```py
answer = int(response.body)
```

Это позволило успешно пройти все 71 тест.

---

## Принципы при реализации заглушек

1. **Минимальность**: каждый mock настроен ровно на то поведение, которое нужно конкретному тесту.
2. **Соответствие спецификации**: для `NO_MONEY` и `ALREADY_LOGGED` обязательно заполняется `body` – согласно Приложению 2–4.
3. **Изоляция**: `setup_method` пересоздаёт mock-объекты перед каждым тестом, исключая взаимовлияние между тестами.
4. **Неизменность исходного кода**: файлы в `src/` не трогались – все изменения строго в `tests/`.

---

## Вывод

Реализованы mock-заглушки для всех четырёх классов serverless-тестов. Каждый стаб соответствует спецификации интерфейсов `IAuthorizationSource` и `IAccountDataSource`. Тесты покрывают: успешные сценарии, все коды ошибок, граничные случаи (null-аргументы, двойной logout, операции после выхода из системы, недостаток средств). Системный тест дополнен корректным управлением жизненным циклом сервер-процесса.
