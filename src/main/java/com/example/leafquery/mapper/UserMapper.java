package com.example.leafquery.mapper;

import com.example.leafquery.entity.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface UserMapper {

    int insertUser(User user);

    User selectByUsername(String username);

    User selectByUserId(Long userId);

    List<User> selectAll();

    int updateUser(User user);

    int deleteByUserId(Long userId);

}
