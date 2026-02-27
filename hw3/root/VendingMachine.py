class VendingMachine:
    class Mode:
        OPERATION = 1
        ADMINISTERING = 2

    class Response:
        OK = 1
        ILLEGAL_OPERATION = 2
        INVALID_PARAM = 3
        CANNOT_PERFORM = 4
        TOO_BIG_CHANGE = 5
        UNSUITABLE_CHANGE = 6
        INSUFFICIENT_PRODUCT = 7
        INSUFFICIENT_MONEY = 8

    _coinval1 = 1
    _coinval2 = 2

    def __init__(self):
        self._id = 117345294655382
        self._mode = VendingMachine.Mode.OPERATION
        # max amount of product
        self._max = 40
        # current amount of product
        self._num = 0
        # price of product 
        self._price = 5
        # coins storage capacity for coins 1 and 2
        self._maxc1 = 50
        self._maxc2 = 50
        # current amount of coins 1 and 2
        self._coins1 = 0
        self._coins2 = 0
        self._balance = 0

    # getters
    def get_number_of_product(self):
        return self._num

    def get_current_balance(self):
        return self._balance

    def get_current_mode(self):
        return self._mode

    def get_current_sum(self):
        if self._mode == VendingMachine.Mode.OPERATION:
            return 0
        return self._coins1 * self._coinval1 + self._coins2 * self._coinval2

    def get_coins1(self):
        if self._mode == VendingMachine.Mode.OPERATION:
            return 0
        return self._coins1

    def get_coins2(self):
        if self._mode == VendingMachine.Mode.OPERATION:
            return 0
        return self._coins2

    def get_price(self):
        return self._price

    # admin functions
    def fill_products(self):
        if self._mode == VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        self._num = self._max
        return VendingMachine.Response.OK

    def fill_coins(self, c1: int, c2: int):
        if self._mode == VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        # validate params
        if c1 <= 0 or c1 > self._maxc1:
            return VendingMachine.Response.INVALID_PARAM
        if c2 <= 0 or c2 > self._maxc2:
            return VendingMachine.Response.INVALID_PARAM
        self._coins1 = c1
        self._coins2 = c2
        return VendingMachine.Response.OK

    def enter_admin_mode(self, code: int):
        # can't enter admin if user has money inserted
        if self._balance != 0:
            return VendingMachine.Response.CANNOT_PERFORM
        if code != self._id:
            return VendingMachine.Response.INVALID_PARAM
        self._mode = VendingMachine.Mode.ADMINISTERING
        return VendingMachine.Response.OK

    def exit_admin_mode(self):
        if self._mode == VendingMachine.Mode.ADMINISTERING:
            self._mode = VendingMachine.Mode.OPERATION

    def set_prices(self, p: int):
        if self._mode == VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        if p <= 0:
            return VendingMachine.Response.INVALID_PARAM
        self._price = p
        return VendingMachine.Response.OK

    # operation functions
    def put_coin1(self):
        if self._mode != VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        if self._coins1 == self._maxc1:
            return VendingMachine.Response.CANNOT_PERFORM
        self._coins1 += 1
        self._balance += self._coinval1
        return VendingMachine.Response.OK

    def put_coin2(self):
        if self._mode != VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        if self._coins2 == self._maxc2:
            return VendingMachine.Response.CANNOT_PERFORM
        self._coins2 += 1
        self._balance += self._coinval2
        return VendingMachine.Response.OK

    def return_money(self):
        if self._mode != VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        if self._balance == 0:
            return VendingMachine.Response.OK

        total_value = self._coins1 * self._coinval1 + self._coins2 * self._coinval2
        if self._balance > total_value:
            return VendingMachine.Response.TOO_BIG_CHANGE

        # if need more than all coin2 can provide -> use all coin2 and rest by coin1
        if self._balance > self._coins2 * self._coinval2:
            needed_from_c1 = self._balance - self._coins2 * self._coinval2
            # since total_value >= balance, coins1 must be sufficient
            self._coins1 -= needed_from_c1
            self._coins2 = 0
            self._balance = 0
            return VendingMachine.Response.OK

        # if balance divisible by coinval2 -> give only coin2
        if self._balance % self._coinval2 == 0:
            coins2_to_give = self._balance // self._coinval2
            self._coins2 -= coins2_to_give
            self._balance = 0
            return VendingMachine.Response.OK

        # balance is odd relative to coinval2 (1) and coins1 needed
        if self._coins1 == 0:
            return VendingMachine.Response.UNSUITABLE_CHANGE

        # give (balance // 2) coins of type2 and 1 coin of type1
        coins2_to_give = self._balance // self._coinval2
        self._coins2 -= coins2_to_give
        self._coins1 -= 1
        self._balance = 0
        return VendingMachine.Response.OK

    def give_product(self, number: int):
        if self._mode != VendingMachine.Mode.OPERATION:
            return VendingMachine.Response.ILLEGAL_OPERATION
        if number <= 0 or number > self._max:
            return VendingMachine.Response.INVALID_PARAM
        if number > self._num:
            return VendingMachine.Response.INSUFFICIENT_PRODUCT

        res = self._balance - number * self._price
        if res < 0:
            return VendingMachine.Response.INSUFFICIENT_MONEY

        total_value = self._coins1 * self._coinval1 + self._coins2 * self._coinval2
        if res > total_value:
            return VendingMachine.Response.TOO_BIG_CHANGE

        # if need more than coin2 can give -> give all coin2 and rest by coin1
        if res > self._coins2 * self._coinval2:
            needed_from_c1 = res - self._coins2 * self._coinval2
            # since total_value >= res => coins1 sufficient
            self._coins1 -= needed_from_c1
            self._coins2 = 0
            self._balance = 0
            self._num -= number
            return VendingMachine.Response.OK

        # res can be paid fully by coin2 if divisible
        if res % self._coinval2 == 0:
            coins2_to_give = res // self._coinval2
            self._coins2 -= coins2_to_give
            self._balance = 0
            self._num -= number
            return VendingMachine.Response.OK

        # res is odd and needs at least one coin1
        if self._coins1 == 0:
            return VendingMachine.Response.UNSUITABLE_CHANGE

        coins2_to_give = res // self._coinval2
        self._coins2 -= coins2_to_give
        self._coins1 -= 1
        self._balance = 0
        self._num -= number
        return VendingMachine.Response.OK
