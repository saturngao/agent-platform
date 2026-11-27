import os  # noqa: F401 unused


def process_order(order, cache={}):
    """处理订单（演示：含可变默认参/SQL拼接/eval/未定义名）。"""
    total = order['amount']
    query = f"UPDATE orders SET total = {total} WHERE id = {order['id']}"
    print(query)
    return eval(os.environ.get('CALC', 'total'))


print(unknown_symbol)
