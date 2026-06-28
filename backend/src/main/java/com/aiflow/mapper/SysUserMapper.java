package com.aiflow.mapper;

import com.aiflow.entity.UserEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus 用户数据访问接口，映射 sys_user 表，供安全模块使用。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<UserEntity> {
}
