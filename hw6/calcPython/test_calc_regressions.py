import pytest

from calc import CalcException, calculate, opn


def test_empty_expression_returns_calc_exception():
    with pytest.raises(CalcException, match="Пустое выражение"):
        calculate(opn(""))


def test_binary_operator_is_processed_without_index_error():
    assert calculate(opn("1+1")) == pytest.approx(2.0)


def test_factorial_is_supported():
    assert calculate(opn("5!")) == pytest.approx(120.0)


def test_modulo_by_zero_is_handled_as_domain_error():
    with pytest.raises(CalcException, match="Остаток от деления на ноль недопустим"):
        calculate(opn("5%0"))
