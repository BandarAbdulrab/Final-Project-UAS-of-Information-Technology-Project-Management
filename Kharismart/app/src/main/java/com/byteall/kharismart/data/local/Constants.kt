package com.byteall.kharismart.data.local

object Role {
    const val ADMIN = "admin"
    const val CASHIER = "cashier"
}

object PaymentMethod {
    const val CASH = "cash"
    const val QRIS = "qris"
    const val CREDIT = "credit"
}

object MovementType {
    const val INITIAL = "initial"
    const val RESTOCK = "restock"
    const val SALE = "sale"
    const val ADJUSTMENT = "adjustment"
}

object DebtStatus {
    const val UNPAID = "unpaid"
    const val PAID = "paid"
}

object FinanceType {
    const val INCOME = "income"
    const val EXPENSE = "expense"
}
