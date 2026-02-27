# Исправленные проблемы

## Проблема 1 — `fill_products` работает в OPERATION (должен только в ADMINISTERING)

### Код до исправления:
```python
def fill_products(self):
    self._num = self._max
    return VendingMachine.Response.OK
```

### Данные, на которых наблюдается некорректное поведение:

Начальное состояние автомата: режим `OPERATION`, `_num = 0`

### Полученное значение, ожидаемое:

Получено: `Response.OK` и `_num == _max`.

Ожидалось: `Response.ILLEGAL_OPERATION` (т.к. метод должен работать только в режиме `ADMINISTERING`).

### Код после исправления:

```python
def fill_products(self):
    if self._mode == VendingMachine.Mode.OPERATION:
        return VendingMachine.Response.ILLEGAL_OPERATION
    self._num = self._max
    return VendingMachine.Response.OK
```

## Проблема 2 — get_coins2 в OPERATION возвращает _coins1 (ошибка переменной)

### Код до исправления:

```python
def get_coins2(self):
    if self._mode == VendingMachine.Mode.OPERATION:
        return self._coins1
    return self._coins2
```

### Данные, на которых наблюдается некорректное поведение:

После заполнения монет в режиме `ADMINISTERING` (например _coins1 = 5, _coins2 = 6) и выхода в OPERATION.

### Полученное значение, ожидаемое:

Получено: `get_coins2() == 5` (т.к. возвращается _coins1).

Ожидалось: `get_coins2() == 0` (в OPERATION метод должен возвращать 0).

### Код после исправления:

```python
def get_coins2(self):
    if self._mode == VendingMachine.Mode.OPERATION:
        return 0
    return self._coins2
```

## Проблема 3 — fill_coins некорректные проверки параметров (опечатки и дубли)

### Код до исправления:

```python
def fill_coins(self, c1: int, c2: int):
    if self._mode == VendingMachine.Mode.OPERATION:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if c1 <= 0 or c2 > self._maxc1:
        return VendingMachine.Response.INVALID_PARAM
    if c1 <= 0 or c2 > self._maxc2:
        return VendingMachine.Response.INVALID_PARAM
    self._coins1 = c1
    self._coins2 = c2
    return VendingMachine.Response.OK
```

### Данные, на которых наблюдается некорректное поведение:

Вызов `fill_coins(1, 51)` — ожидаемо `c2 > maxc2`, но в первой проверке используется `_maxc1` (ошибка).

### Полученное значение, ожидаемое:

Получено: `INVALID_PARAM` (правильно обнаруживается, но по неправильной причине / с ошибочной логикой).

Ожидалось: корректная проверка границ: `c1 <= 0 or c1 > _maxc1` и `c2 <= 0 or c2 > _maxc2`.

### Код после исправления:

```python
def fill_coins(self, c1: int, c2: int):
    if self._mode == VendingMachine.Mode.OPERATION:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if c1 <= 0 or c1 > self._maxc1:
        return VendingMachine.Response.INVALID_PARAM
    if c2 <= 0 or c2 > self._maxc2:
        return VendingMachine.Response.INVALID_PARAM
    self._coins1 = c1
    self._coins2 = c2
    return VendingMachine.Response.OK
```

## Проблема 4 — enter_admin_mode возвращает UNSUITABLE_CHANGE вместо CANNOT_PERFORM при ненулевом балансе

### Код до исправления:

```python
def enter_admin_mode(self, code: int):
    if self._balance != 0:
        return VendingMachine.Response.UNSUITABLE_CHANGE
    ...
```

### Данные, на которых наблюдается некорректное поведение:

Баланс пользователя ненулевой (`_balance = 1`) и вызов `enter_admin_mode(_id)`.

### Полученное значение, ожидаемое:

Получено: `UNSUITABLE_CHANGE`

Ожидалось: `CANNOT_PERFORM` по спецификации.

### Код после исправления:

```python
def enter_admin_mode(self, code: int):
    if self._balance != 0:
        return VendingMachine.Response.CANNOT_PERFORM
    ...
```

## Проблема 5 — set_prices использует _price вместо параметра p в проверке (NameError)

### Код до исправления:

```python
def set_prices(self, p: int):
    if self._mode == VendingMachine.Mode.OPERATION:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if _price <= 0:
        return VendingMachine.Response.INVALID_PARAM
    self._price = p
    return VendingMachine.Response.OK
```

### Данные, на которых наблюдается некорректное поведение:

Вызов `set_prices(10)` в админ-режиме.

### Полученное значение, ожидаемое:

Получено: `NameError: name '_price' is not defined`

Ожидалось: проверка `p <= 0` и при корректном p — `Response.OK`.

### Код после исправления:

```python
def set_prices(self, p: int):
    if self._mode == VendingMachine.Mode.OPERATION:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if p <= 0:
        return VendingMachine.Response.INVALID_PARAM
    self._price = p
    return VendingMachine.Response.OK
```

## Проблема 6 — put_coin1 / put_coin2 перепутаны (ошибка логики и проверок)

### Код до исправления:

```python
def put_coin1(self):
    if self._mode == VendingMachine.Mode.ADMINISTERING:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if self._coins2 == self._maxc2:
        return VendingMachine.Response.CANNOT_PERFORM
    self._balance += self._coinval2
    self._coins2 += 1
    return VendingMachine.Response.OK

def put_coin2(self):
    if self._mode == VendingMachine.Mode.ADMINISTERING:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if self._coins1 == self._maxc1:
        return VendingMachine.Response.CANNOT_PERFORM
    self._balance += self._coinval1
    self._coins1 += 1
    return VendingMachine.Response.OK
```

### Данные, на которых наблюдается некорректное поведение:

Вызов `put_coin1()` — по спецификации должен увеличить `_coins1` и баланс на 1, но текущая реализация меняет `_coins2` и баланс на 2.

### Полученное значение, ожидаемое:

Получено: при `put_coin1()` — `_coins2` увеличивается, баланс возрастает на 2.

Ожидалось: `_coins1` увеличивается, баланс += 1.

### Код после исправления:

```python
def put_coin1(self):
    if self._mode == VendingMachine.Mode.ADMINISTERING:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if self._coins1 == self._maxc1:
        return VendingMachine.Response.CANNOT_PERFORM
    self._balance += self._coinval1
    self._coins1 += 1
    return VendingMachine.Response.OK

def put_coin2(self):
    if self._mode == VendingMachine.Mode.ADMINISTERING:
        return VendingMachine.Response.ILLEGAL_OPERATION
    if self._coins2 == self._maxc2:
        return VendingMachine.Response.CANNOT_PERFORM
    self._balance += self._coinval2
    self._coins2 += 1
    return VendingMachine.Response.OK
```

## Проблема 7 — give_product возвращает INSUFFICIENT_MONEY при недостатке сдачи (по спецификации должен TOO_BIG_CHANGE)

### Код до исправления:

```python
res = self._balance - number * self._price
if res < 0:
    return VendingMachine.Response.INSUFFICIENT_MONEY
if res > self._coins1 * self._coinval1 + self._coins2 * self._coinval2:
    return VendingMachine.Response.INSUFFICIENT_MONEY
```

### Данные, на которых наблюдается некорректное поведение:

`_balance` достаточно для покупки, но сумма сдачи res больше имеющейся сдачи в автомате.

### Полученное значение, ожидаемое:

Получено: `INSUFFICIENT_MONEY`

Ожидалось: `TOO_BIG_CHANGE` (по описанию задания).

### Код после исправления:

```python
if res < 0:
    return VendingMachine.Response.INSUFFICIENT_MONEY
if res > self._coins1 * self._coinval1 + self._coins2 * self._coinval2:
    return VendingMachine.Response.TOO_BIG_CHANGE
```
