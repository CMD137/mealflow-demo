"""Tool specifications shared with the LLM (OpenAI function-calling schema).

Must stay in sync with the Java ToolRegistryService. Tool names express
customer-service semantics only.
"""

TOOL_SPECS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "query_order_status",
            "description": "按订单号查询订单状态（离线演示数据，非实时）",
            "parameters": {
                "type": "object",
                "properties": {"orderNo": {"type": "string", "description": "订单号"}},
                "required": ["orderNo"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_user_spending",
            "description": "查询用户在某时间段内的消费统计（离线演示数据，非实时）",
            "parameters": {
                "type": "object",
                "properties": {"period": {"type": "string", "description": "时间段，如 last-7-days"}},
                "required": ["period"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_order_detail",
            "description": "按 orderId 查询当前用户订单详情（含状态、金额、商品明细）",
            "parameters": {
                "type": "object",
                "properties": {"orderId": {"type": "integer", "description": "订单 ID"}},
                "required": ["orderId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_user_orders",
            "description": "查询当前用户全部订单列表",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_queue_status",
            "description": "查询当前用户排队状态（排第几、预计等待时长、排队票据状态）",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_voucher_wallet",
            "description": "查询当前用户优惠券包（可用/已锁定/已核销）",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_voucher_claim_status",
            "description": "按 voucherId 查询当前用户的秒杀券领取状态",
            "parameters": {
                "type": "object",
                "properties": {"voucherId": {"type": "integer", "description": "优惠券 ID"}},
                "required": ["voucherId"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_my_notifications",
            "description": "查询当前用户收到的站内通知消息",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_merchant_menu",
            "description": "按 merchantId 查询商家信息与菜品/SKU 列表（公开数据）",
            "parameters": {
                "type": "object",
                "properties": {"merchantId": {"type": "integer", "description": "商家 ID"}},
                "required": ["merchantId"],
            },
        },
    },
]
