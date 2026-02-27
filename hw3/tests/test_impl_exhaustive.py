import pytest
from root.VendingMachine import VendingMachine

VM = VendingMachine

def test_initial_state_and_simple_getters():
    vm = VendingMachine()
    assert vm.get_number_of_product() == 0
    assert vm.get_current_balance() == 0
    assert vm.get_current_mode() == vm.Mode.OPERATION
    assert vm.get_price() == 5
    assert vm.get_current_sum() == 0
    assert vm.get_coins1() == 0
    assert vm.get_coins2() == 0

def test_fill_products_admin_only_and_success():
    vm = VendingMachine()
    assert vm.fill_products() == VM.Response.ILLEGAL_OPERATION
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.fill_products() == VM.Response.OK
    assert vm.get_number_of_product() == vm._max

def test_fill_coins_illegal_in_operation_mode_and_invalid_params_and_success_in_admin():
    vm = VendingMachine()
    assert vm.fill_coins(1, 1) == VM.Response.ILLEGAL_OPERATION

    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.fill_coins(0, 1) == VM.Response.INVALID_PARAM
    assert vm.fill_coins(1, vm._maxc2 + 1) == VM.Response.INVALID_PARAM
    assert vm.fill_coins(5, 6) == VM.Response.OK
    assert vm._coins1 == 5
    assert vm._coins2 == 6

def test_enter_admin_mode_when_balance_nonzero_returns_cannot_perform():
    vm = VendingMachine()
    assert vm.put_coin1() == VM.Response.OK
    assert vm.enter_admin_mode(vm._id) == VM.Response.CANNOT_PERFORM

def test_set_prices_now_works_in_admin():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.set_prices(0) == VM.Response.INVALID_PARAM
    assert vm.set_prices(10) == VM.Response.OK
    assert vm.get_price() == 10

def test_put_coin1_and_put_coin2_behavior_correct():
    vm = VendingMachine()
    assert vm.put_coin1() == VM.Response.OK
    assert vm._coins1 == 1
    assert vm._coins2 == 0
    assert vm.get_current_balance() == vm._coinval1

    assert vm.put_coin2() == VM.Response.OK
    assert vm._coins2 == 1
    assert vm._coins1 == 1
    assert vm.get_current_balance() == vm._coinval1 + vm._coinval2

def test_return_money_branches():
    vm = VendingMachine()
    assert vm.return_money() == VM.Response.OK

    vm._balance = 5
    vm._coins1 = 0
    vm._coins2 = 0
    assert vm.return_money() == VM.Response.TOO_BIG_CHANGE
    vm._balance = 0

    vm._coins1 = 5
    vm._coins2 = 1
    vm._balance = 4
    assert vm.return_money() == VM.Response.OK
    assert vm._coins1 == 3 and vm._coins2 == 0 and vm._balance == 0

    vm._coins1 = 10
    vm._coins2 = 3
    vm._balance = 4
    assert vm.return_money() == VM.Response.OK
    assert vm._coins2 == 1 and vm._balance == 0

    vm._coins1 = 0
    vm._coins2 = 2
    vm._balance = 3 
    assert vm.return_money() == VM.Response.UNSUITABLE_CHANGE
    assert vm._balance == 3

    vm._coins1 = 5
    vm._coins2 = 2
    vm._balance = 3
    assert vm.return_money() == VM.Response.OK
    assert vm._coins1 == 4 and vm._coins2 == 1 and vm._balance == 0

def test_give_product_param_and_business_branches():
    vm = VendingMachine()
    assert vm.give_product(0) == VM.Response.INVALID_PARAM
    assert vm.give_product(vm._max + 1) == VM.Response.INVALID_PARAM

    vm._num = 1
    assert vm.give_product(2) == VM.Response.INSUFFICIENT_PRODUCT

    vm._num = 10
    vm._balance = 0
    assert vm.give_product(1) == VM.Response.INSUFFICIENT_MONEY

    vm._balance = 20
    vm._coins1 = 0
    vm._coins2 = 0
    vm._price = 5
    assert vm.give_product(1) == VM.Response.TOO_BIG_CHANGE

    vm._coins1 = 0
    vm._coins2 = 2 
    vm._balance = 9
    vm._num = 5
    assert vm.give_product(1) == VM.Response.OK
    assert vm._num == 4
