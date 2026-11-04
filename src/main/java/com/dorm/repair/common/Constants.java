package com.dorm.repair.common;

import java.util.List;

/**
 * 全局常量。
 */
public final class Constants {

    private Constants() {
    }

    /** 登录成功后，当前用户信息（LoginUser）在 HttpSession 中的属性名 */
    public static final String SESSION_USER = "loginUser";

    /** 楼栋列表（写死在代码中）。注册、修改资料、提交报修时只能从这里选择 */
    public static final List<String> BUILDINGS = List.of(
            "1号楼", "2号楼", "3号楼", "4号楼", "5号楼", "6号楼", "7号楼", "8号楼");
}
