package com.homerunpet.homerun_pet_android_productiontest.common.ext

import java.lang.reflect.ParameterizedType


/**
 * 获取当前类绑定的泛型ViewModel-clazz
 */
@Suppress("UNCHECKED_CAST")
fun <VM> getVmClazz(obj: Any): Class<VM> {
    var clazz: Class<*>? = obj.javaClass
    while (clazz != null) {
        val type = clazz.genericSuperclass
        if (type is ParameterizedType) {
            val args = type.actualTypeArguments
            if (args.isNotEmpty()) {
                return args[0] as Class<VM>
            }
        }
        clazz = clazz.superclass
    }
    throw IllegalArgumentException("Generic type VM not found in ${obj.javaClass.simpleName}")
}

/**
 * 获取当前类绑定的泛型DataBinding-clazz
 */
@Suppress("UNCHECKED_CAST")
fun <DB> getDbClazz(obj: Any): Class<DB> {
    var clazz: Class<*>? = obj.javaClass
    while (clazz != null) {
        val type = clazz.genericSuperclass
        if (type is ParameterizedType) {
            val args = type.actualTypeArguments
            if (args.size > 1) {
                return args[1] as Class<DB>
            }
        }
        clazz = clazz.superclass
    }
    throw IllegalArgumentException("Generic type DB not found in ${obj.javaClass.simpleName}")
}







