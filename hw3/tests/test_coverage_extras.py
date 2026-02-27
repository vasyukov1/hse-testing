import pytest
from root.VendingMachine import VendingMachine

VM = VendingMachine

def test_admin_getters_and_exit_admin_noop():
    vm = VendingMachine()
    vm.exit_admin_mode()
    assert vm.get_current_mode() == VM.Mode.OPERATION

    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.fill_coins(7, 8) == VM.Response.OK
    assert vm.get_current_sum() == 7 * vm._coinval1 + 8 * vm._coinval2
    assert vm.get_coins1() == 7
    assert vm.get_coins2() == 8

    vm.exit_admin_mode()
    assert vm.get_current_mode() == VM.Mode.OPERATION
    assert vm.get_current_sum() == 0
    assert vm.get_coins1() == 0
    assert vm.get_coins2() == 0

def test_fill_coins_more_invalid_combinations():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    # c1 <= 0
    assert vm.fill_coins(0, 1) == VM.Response.INVALID_PARAM
    # c2 <= 0
    assert vm.fill_coins(1, 0) == VM.Response.INVALID_PARAM
    # c1 > max
    assert vm.fill_coins(vm._maxc1 + 1, 1) == VM.Response.INVALID_PARAM
    # c2 > max
    assert vm.fill_coins(1, vm._maxc2 + 1) == VM.Response.INVALID_PARAM

def test_enter_admin_mode_wrong_code_and_set_prices_in_operation():
    vm = VendingMachine()
    assert vm.enter_admin_mode(0) == VM.Response.INVALID_PARAM
    assert vm.set_prices(3) == VM.Response.ILLEGAL_OPERATION

def test_put_coin_in_admin_and_capacity_limits():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.put_coin1() == VM.Response.ILLEGAL_OPERATION
    assert vm.put_coin2() == VM.Response.ILLEGAL_OPERATION
    vm.exit_admin_mode()

    # capacity limit for coin1
    vm._coins1 = vm._maxc1
    assert vm.put_coin1() == VM.Response.CANNOT_PERFORM
    vm._coins1 = 0

    # capacity limit for coin2
    vm._coins2 = vm._maxc2
    assert vm.put_coin2() == VM.Response.CANNOT_PERFORM
    vm._coins2 = 0

def test_return_money_edge_cases_more():
    vm = VendingMachine()
    vm._coins1 = 0
    vm._coins2 = 0
    vm._balance = 10
    assert vm.return_money() == VM.Response.TOO_BIG_CHANGE

    vm._coins1 = 0
    vm._coins2 = 5
    vm._balance = 6
    assert vm.return_money() == VM.Response.OK
    assert vm._coins2 == 2 and vm._balance == 0

    vm._coins1 = 10
    vm._coins2 = 1
    vm._balance = 6
    assert vm.return_money() == VM.Response.OK
    assert vm._coins1 == 6 and vm._coins2 == 0 and vm._balance == 0

def test_give_product_admin_illegal_and_exact_payment_and_unsuitable_change():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.give_product(1) == VM.Response.ILLEGAL_OPERATION
    vm.exit_admin_mode()

    vm._num = 3
    vm._price = 5
    vm._coins1 = 0
    vm._coins2 = 0
    vm._balance = 5 
    assert vm.give_product(1) == VM.Response.OK
    assert vm._num == 2 and vm._balance == 0

    vm._num = 5
    vm._price = 5
    vm._coins1 = 0
    vm._coins2 = 0
    vm._balance = 20 
    assert vm.give_product(1) == VM.Response.TOO_BIG_CHANGE

    vm._coins1 = 0
    vm._coins2 = 2
    vm._price = 5
    vm._balance = 8
    vm._num = 5
    assert vm.give_product(1) == VM.Response.UNSUITABLE_CHANGE

def test_return_money_illegal_in_admin_mode():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.return_money() == VM.Response.ILLEGAL_OPERATION
    vm.exit_admin_mode()
    assert vm.return_money() == VM.Response.OK

def test_give_product_illegal_in_admin_mode():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.give_product(1) == VM.Response.ILLEGAL_OPERATION
    vm.exit_admin_mode()
    vm._num = 1
    vm._price = 5
    vm._balance = 5
    assert vm.give_product(1) == VM.Response.OK

def test_give_product_branch_res_more_than_coin2_capacity():
    vm = VendingMachine()

    vm._num = 5
    vm._price = 5
    vm._balance = 11
    vm._coins2 = 2
    vm._coins1 = 10  
    
    assert vm.give_product(1) == VM.Response.OK
    assert vm._coins2 == 0
    assert vm._coins1 == 8
    assert vm._balance == 0
    assert vm._num == 4


def test_give_product_branch_odd_change_needs_coin1():
    vm = VendingMachine()

    vm._num = 5
    vm._price = 5
    vm._balance = 8
    vm._coins2 = 2
    vm._coins1 = 5

    assert vm.give_product(1) == VM.Response.OK
    assert vm._coins2 == 1
    assert vm._coins1 == 4
    assert vm._balance == 0
    assert vm._num == 4
