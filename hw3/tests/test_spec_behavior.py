import pytest
from root.VendingMachine import VendingMachine

VM = VendingMachine

def test_fill_products_should_be_admin_only():
    vm = VendingMachine()
    assert vm.fill_products() == VM.Response.ILLEGAL_OPERATION

def test_get_coins2_in_operation_should_be_zero():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.fill_coins(5, 5) == VM.Response.OK
    vm.exit_admin_mode()
    assert vm.get_coins2() == 0

def test_enter_admin_mode_balance_nonzero_should_return_cannot_perform():
    vm = VendingMachine()
    vm.put_coin1()
    assert vm.enter_admin_mode(vm._id) == VM.Response.CANNOT_PERFORM

def test_set_prices_invalid_and_ok():
    vm = VendingMachine()
    assert vm.enter_admin_mode(vm._id) == VM.Response.OK
    assert vm.set_prices(0) == VM.Response.INVALID_PARAM
    assert vm.set_prices(7) == VM.Response.OK
    assert vm.get_price() == 7

def test_put_coin_correct_behavior_and_capacity():
    vm = VendingMachine()
    assert vm.put_coin1() == VM.Response.OK
    assert vm._coins1 == 1
    assert vm.get_current_balance() == 1
