import math


class CalcException(Exception):
    def __init__(self, message):
        super().__init__(message)


def opn(s_in):
    sb_stack, sb_out = [], []
    for c_in in s_in:
        if is_op(c_in):
            while sb_stack:
                c_tmp = sb_stack[-1]
                if is_op(c_tmp) and op_prior(c_in) <= op_prior(c_tmp):
                    sb_out.append(" ")
                    sb_out.append(c_tmp)
                    sb_out.append(" ")
                    del sb_stack[-1]
                else:
                    sb_out.append(" ")
                    break
            sb_out.append(" ")
            sb_stack.append(c_in)
        elif c_in == '(':
            sb_stack.append(c_in)
        elif c_in == ')':
            if not sb_stack:
                raise CalcException("Ошибка разбора скобок. Проверьте правильность выражения.")

            c_tmp = sb_stack[-1]
            while c_tmp != "(":
                sb_out.append(" ")
                sb_out.append(c_tmp)
                del sb_stack[-1]
                if not sb_stack:
                    raise CalcException("Ошибка разбора скобок. Проверьте правильность выражения.")
                c_tmp = sb_stack[-1]
            del sb_stack[-1]
        else:
            sb_out.append(c_in)

    while sb_stack:
        if sb_stack[-1] in "()":
            raise CalcException("Ошибка разбора скобок. Проверьте правильность выражения.")
        sb_out.append(" ")
        sb_out.append(sb_stack[-1])
        del sb_stack[-1]

    return "".join(sb_out)


def is_op(c):
    return c in "-+*/^!%"


def op_prior(op):
    priorities = {"^": 3, "*": 2, "/": 2, "%": 2}
    return priorities.get(op, 1)


def apply_factorial(value):
    if not math.isfinite(value):
        raise CalcException("Факториал определён только для конечных чисел")
    if value < 0 or not value.is_integer():
        raise CalcException("Факториал определён только для неотрицательных целых чисел")
    return float(math.factorial(int(value)))


def calculate(s_in):
    stack = []

    for token in s_in.split():
        s_tmp = token.strip()
        if not s_tmp:
            continue

        if len(s_tmp) == 1 and is_op(s_tmp):
            if s_tmp == "!":
                if not stack:
                    raise CalcException("Неверное количество данных в стеке для операции !")
                stack.append(apply_factorial(stack.pop()))
                continue

            if len(stack) < 2:
                raise CalcException("Неверное количество данных в стеке для операции " + token)

            b, a = stack.pop(), stack.pop()
            match s_tmp:
                case "+":
                    a += b
                case "-":
                    a -= b
                case "*":
                    a *= b
                case "/":
                    if b == 0:
                        return float("inf")
                    a /= b
                case "%":
                    if b == 0:
                        raise CalcException("Остаток от деления на ноль недопустим")
                    a %= b
                case "^":
                    a **= b
                case _:
                    raise CalcException("Недопустимая операция " + s_tmp)
            stack.append(a)
            continue

        try:
            stack.append(float(s_tmp))
        except ValueError as exc:
            raise CalcException("Недопустимый символ в выражении") from exc

    if not stack:
        raise CalcException("Пустое выражение")
    if len(stack) != 1:
        raise CalcException("Количество операторов не соответствует количеству операндов")

    return stack.pop()


if __name__ == "__main__":
    print(
        "Введите выражение для расчета. Поддерживаются цифры, операции +,-,*,/,^,% и приоритеты в виде скобок ( и ):"
    )
    s_in = input()
    s_in = opn(s_in)
    print(calculate(s_in))
