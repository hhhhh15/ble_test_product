package com.homerunpet.homerun_pet_android_productiontest.base.net

object HmApi {
    fun getBaseUrl(): String {
        // return BuildConfig.HM_API_BASE_URL
        return "http://ptest-app.homerunsmartapi.com"
    }

    /**
     * ************************************************************************** 通用接口
     * **********************************
     */




    // 获取地区列表
    const val COMMON_REGION = "/v4/common/region"

    // 获取语言列表
    const val COMMON_LANG = "/v4/common/lang"

    // 获取系统配置
    const val COMMON_CONFIG = "/v4/common/config"

    // 获取APP版本信息
    const val COMMON_APP_VERSION = "/v4/common/app-version"

    /**
     * ************************************************************************** 登录相关接口
     * **********************************
     */
    // 手机号密码登录
    const val LOGIN_PHONE = "/v4/auth/login/phone"

    // 邮箱密码登录
    const val LOGIN_EMAIL = "/v4/auth/login/email"

    // 发送短信验证码
    const val SMS_CODE = "/v4/auth/sms-code"
    fun getSmsCode(topic: String) = "$SMS_CODE/$topic"

    // 发送邮箱验证码
    const val EMAIL_CODE = "/v4/auth/email-code"
    fun getEmailCode(topic: String) = "$EMAIL_CODE/$topic"

    // 手机号验证码登录
    const val LOGIN_PHONE_SMS = "/v4/auth/login/phone-sms"

    // 邮箱验证码登录
    const val LOGIN_EMAIL_SMS = "/v4/auth/login/email-sms"

    // 智能验证码配置信息
    const val CAPTCHA_CONF = "/v4/auth/captcha-conf"

    // 忘记密码，用户使用邮箱验证码修改密码
    const val PASSWORD_BY_EMAIL = "/v4/auth/password/by-email"

    // 忘记密码，用户使用手机验证码修改密码
    const val PASSWORD_BY_PHONE = "/v4/auth/password/by-phone"

    // 运营商一键登录
    const val LOGIN_QUICK = "/v4/auth/login/quick"

    /**
     * ************************************************************************** 我的相关接口
     * **********************************
     */
    // 用户注销账号
    const val USERS = "/v4/users"

    // 解绑邮箱 更换邮箱
    const val USERS_EMAIL = "/v4/users/email"

    // 解绑手机号 更换手机号
    const val USERS_PHONE = "/v4/users/phone"

    // 用户初始化密码设置
    const val USERS_PASSWORD = "/v4/users/password"

    // 用户使用原密码修改密码
    const val USERS_PASSWORD_BY_OLD = "/v4/users/password/by-old"

    // 切换语言
    const val USERS_LANGUAGE = "/v4/users/language"

    // 获取用户信息 / 修改用户信息
    const val USERS_INFO = "/v4/users/info"

    // 获取OSS上传Token
    const val USERS_OSS_TOKEN = "/v4/users/oss-token"

    // 获取用户配置 /修改用户配置
    const val USERS_CONFIG = "/v4/users/config"

    // 用户退出登录
    const val USERS_LOGOUT = "/v4/users/logout"

    // 更新用户设备信息
    const val USERS_DEVICE = "/v4/users/device"

    /**
     * ************************************************************************** 家庭相关接口
     * **********************************
     */
    // 获取家庭列表
    const val FAMILIES = "/v4/families"

    // 家庭成员列表
    fun getFamilyMembers(familyId: String) = "/v4/families/$familyId/members"

    // 获取该用户的家庭成员类型
    fun getFamilyMembersType(familyId: String) = "/v4/families/$familyId/members/type"

    // 被邀请的记录
    const val FAMILIES_MEMBERS_INVITATION = "/v4/families/members/invitation"

    // 邀请成员
    fun getInviteMember(familyId: String) = "/v4/families/$familyId/members/invitation"

    // 搜索成员
    fun getSearchMember(familyId: String) = "/v4/families/$familyId/members/search"

    // 删除成员 or 修改成员类型
    fun getDeleteMember(familyId: String, memberId: String) =
        "/v4/families/$familyId/members/${memberId}"

    // 撤回邀请
    fun getCancelInvite(familyId: String, memberId: String) =
        "/v4/families/$familyId/members/$memberId/cancel"

    // 邀请决策
    fun getDecisionFamilies(familyId: String, memberId: String) =
        "/v4/families/$familyId/members/$memberId/decision"

    // 房间列表 or 单个创建房间
    fun getRoomList(familyId: String) = "/v4/families/$familyId/rooms"

    // 房间类型列表
    const val FAMILIES_ROOM_TYPES = "/v4/families/rooms/types"

    // 修改房间 or 删除房间
    fun getUpdateRoom(familyId: String, roomId: String) = "/v4/families/$familyId/rooms/$roomId"

    // 初始化多个房间
    fun getInitRooms(familyId: String) = "/v4/families/$familyId/rooms/init"

    /**
     * ************************************************************************** 问题相关接口
     * **********************************
     */
    // 问题指南列表 or 问题提交
    const val ISSUES = "/v4/issues"

    // 问题指南类型
    const val ISSUES_TYPES = "/v4/issues/types"

    // 产品建议
    const val ISSUES_SUGGESTIONS = "/v4/issues/suggestions"

    /**
     * ************************************************************************** 宠物相关接口
     * **********************************
     */
    // 宠物列表 or 自己的宠物列表 or 添加宠物
    const val PETS = "/v4/pets"

    // 宠物品种列表
    const val PETS_VARIETY = "/v4/pets/variety"

    // 宠物详情
    fun getPetsDetail(pet_id: Int) = "/v4/pets/$pet_id/detail"

    // 删除宠物 or 修改宠物
    fun getDeleteOrUpdatePets(pet_id: Int) = "/v4/pets/$pet_id"

    // 添加宠物体重 or 宠物体重数据
    fun getPetsWeight(pet_id: Int) = "/v4/pets/$pet_id/weight"

    // 宠物如厕数据
    fun getPetsToilet(pet_id: Int) = "/v4/pets/$pet_id/toilet"

    // 上传宠物图片（AI）
    const val PETS_UPLOAD_PICTURE = "/v4/pets/upload-picture"

    // 宠物动态记录
    fun getPetsDynamic(pet_id: Int) = "/v4/pets/$pet_id/dynamic"

    /**
     * ************************************************************************** 设备相关
     * **********************************
     */

    /*
      * 产测app的接口
      * */
    //登录post固定账号密码
    const val PRODUCT_TEST_LOGIN= "/v1/auth/login/phone"

    //弄一个自动刷新token的
    const val PRODUCT_TEST_TOKEN="/v1/users/refresh-token"

    //用户登录后的信息，获取到登录角色role
    const val PRODUCT_USER_MESSAGE="/v1/users"

    //用户退出登录
    const val PRODUCT_USER_LOGOUT="/v1/users/logout"

 /*
 *      配网接口
 * */
    // 获取品类产品
    const val CATEGORY_PRODUCTS = "/v1/devices/category-products"

    // 获取产品详情
    fun getProductsByKey(product_key: String) = "/v1/products/$product_key"

    // 获取设备配网状态(自研设备)
    fun getHrDeviceProvisionStatus(user_id: String) = "/v1/users/${user_id}/provision-status"
  /*
  *     物模型接口
  * */
  // 获取产品物模型
  fun getProductsThingModel(product_key: String) = "/v1/products/$product_key/thing-model"

    //下发设备服务
    fun issueDeviceService(device_name: String,identifier:String)="/v1/devices/$device_name/service/$identifier"

    // 查询设备属性
    fun getDeviceProperty(device_name: String) = "/v1/devices/$device_name/properties"

    // 更新设备属性
    fun getUpdateDeviceProperty(device_name: String) = "/v1/devices/$device_name/properties"

    //重置设备
    fun resetDevcie(device_name: String)="/v1/devices/$device_name/reset"

    //设备事件
    fun getDeviceEvent(device_name: String)="/v1/devices/$device_name/events"

    // 获取设备故障信息
    fun getDeviceFaultInfo(device_name: String) = "/v1/devices/$device_name/faults"

    // 删除设备
    fun getDeleteDevice(device_name: String) = "/v1/devices/$device_name"


    // 绑定设备/更新设备别名
    fun postAddDevice(device_name: String) = "/v1/devices/$device_name"

    // 绑定设备/更新设备别名
    fun getDevice(device_name: String) = "/v1/devices/$device_name"

    //产测报告上传
    fun postTestReport(device_name:String)="/v1/devices/$device_name/production-testing-logs"

    //获取产测报告，我靠，这个接口用的地方不知道啊，因为这个产测报告又产测结果，产测报告上传有用到
    fun getTestReport()="/v1/devices/production-testing-logs"

    // 获取设备固件信息
    fun getDeviceFirmwareInfo(deviceName: String) = "/v1/devices/$deviceName/firmwares"

    // (获取/创建/修改/删除)定时任务
    const val DEVICES_TASK = "/v1/tasks"

    //这个接口文档没有说明
    fun getUploadFile(user_id: String) = "/v1/users/$user_id/provision-logs"



    /**
     * ************************************************************************** 广告
     * **********************************
     */

    // 开屏广告
    const val ADS_SPLASH = "/v1/ads/splash"

    // 首页轮播广告
    const val ADS_BANNER = "/v1/ads/banner"

}
