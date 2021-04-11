package com.grouptuity.grouptuity.data


fun getDinerSubtotal(diner: Diner, itemMap: Map<Long, Item>): Double = diner.items.sumByDouble { itemId -> itemMap[itemId]?.let { it.price / it.diners.size }  ?: 0.0 }