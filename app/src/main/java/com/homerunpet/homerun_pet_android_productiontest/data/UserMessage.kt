package com.homerunpet.homerun_pet_android_productiontest.data

// 你的原始写法有错误，且role字段应使用枚举类型来表达角色。
// 下面演示了如何用enum class来实现角色role字段，并在UserMessage数据类中使用：

// 定义角色枚举（注意可以加注释说明每个枚举值代表什么）
enum class UserRole(val value: Int) {
    ProductionTest(0),    // 产测
    Admin(1),             // 管理员
    AfterSales(2);        // 售后

    companion object {
        fun fromInt(value: Int): UserRole? = UserRole.values().find { it.value == value }
    }
}

// UserMessage数据类，role字段类型为UserRole
data class UserMessage(
    val phone: String,
    val role: UserRole,     // 使用枚举类型，不再直接用Int或String
    val user_name: String
)

// 使用方式举例：
// val user = UserMessage(phone = "13760748522", role = UserRole.Admin, user_name = "张三")
// 可以通过user.role.value拿到枚举对应的Int
// 如果API返回的是Int，可用UserRole.fromInt(0)转换为枚举
