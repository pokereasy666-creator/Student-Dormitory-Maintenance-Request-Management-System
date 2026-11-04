package com.dorm.repair.mapper;

import com.dorm.repair.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户表 sys_user 的数据访问接口。
 * <p>
 * SQL 直接写在注解里；application.yml 中开启了 map-underscore-to-camel-case，
 * 所以 real_name 列会自动映射到 realName 属性。
 */
public interface UserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    User findByUsername(String username);

    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User findById(Long id);

    /** 新增用户；useGeneratedKeys 让 MyBatis 把自增主键回填到 user.id */
    @Insert("INSERT INTO sys_user (username, password, real_name, role, phone, building, room_no, created_at, updated_at) "
            + "VALUES (#{username}, #{password}, #{realName}, #{role}, #{phone}, #{building}, #{roomNo}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 修改个人信息（不包含账号、密码、角色） */
    @Update("UPDATE sys_user SET real_name = #{realName}, phone = #{phone}, building = #{building}, "
            + "room_no = #{roomNo}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateProfile(User user);

    @Update("UPDATE sys_user SET password = #{password}, updated_at = #{now} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password, @Param("now") LocalDateTime now);
}
