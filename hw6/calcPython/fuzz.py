from hypothesis import HealthCheck, Verbosity, given, settings, strategies as st

from calc import CalcException, calculate, opn

CALC_INPUT_ALPHABET = "0123456789+-*/^!%()., abcdefXYZ\t\n"


@given(st.text(alphabet=CALC_INPUT_ALPHABET, min_size=0, max_size=200))
@settings(
    verbosity=Verbosity.verbose,
    max_examples=5_000,
    derandomize=True,
    deadline=None,
    suppress_health_check=[HealthCheck.too_slow],
)
def test_calculate_with_processed_input(input_string):
    try:
        processed_input = opn(input_string)
    except Exception:
        return

    try:
        calculate(processed_input)
    except (CalcException, OverflowError, ValueError):
        return


if __name__ == "__main__":
    test_calculate_with_processed_input()
