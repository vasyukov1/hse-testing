import yaml
from hypothesis import HealthCheck, Verbosity, given, settings, strategies as st

YAML_TEXT_ALPHABET = (
    " \t\n-:{}[],&*!#|>%?'\"@`"
    "0123456789"
    "abcdefghijklmnopqrstuvwxyz"
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
)
YAML_KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-"

YAML_SCALARS = st.one_of(
    st.none(),
    st.booleans(),
    st.integers(),
    st.floats(allow_nan=False, allow_infinity=False),
    st.text(alphabet=YAML_TEXT_ALPHABET, max_size=40),
)
YAML_KEYS = st.text(alphabet=YAML_KEY_ALPHABET, min_size=1, max_size=20)
YAML_DATA = st.recursive(
    YAML_SCALARS,
    lambda children: st.one_of(
        st.lists(children, max_size=5),
        st.dictionaries(YAML_KEYS, children, max_size=5),
    ),
    max_leaves=20,
)

COMMON_SETTINGS = settings(
    verbosity=Verbosity.normal,
    max_examples=3_000,
    derandomize=True,
    deadline=None,
    suppress_health_check=[HealthCheck.too_slow],
)


@given(st.text(alphabet=YAML_TEXT_ALPHABET, min_size=0, max_size=400))
@COMMON_SETTINGS
def test_yaml_safe_load_fuzz(payload):
    try:
        yaml.safe_load(payload)
    except (yaml.YAMLError, UnicodeError, ValueError, RecursionError):
        return


@given(st.text(alphabet=YAML_TEXT_ALPHABET, min_size=0, max_size=400))
@COMMON_SETTINGS
def test_yaml_compose_fuzz(payload):
    try:
        yaml.compose(payload)
    except (yaml.YAMLError, UnicodeError, ValueError, RecursionError):
        return


@given(YAML_DATA)
@COMMON_SETTINGS
def test_yaml_safe_dump_fuzz(payload):
    yaml.safe_dump(payload, sort_keys=True, allow_unicode=False)
