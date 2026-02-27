## Инструкция для запуска

1. Создайте виртуальное окружение и установите зависимости:
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

2. Запустите тесты и сгенерируйте HTML-отчёт о покрытии:

```bash
pytest -q --cov=root --cov-report=html:coverage
```
