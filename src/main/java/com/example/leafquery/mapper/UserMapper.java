package com.example.leafquery.mapper;

import com.example.leafquery.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {

    int insertUser(User user);

    User selectByUsername(String username);

    User selectByUserId(Long userId);

    List<User> selectAll();

    int updateUser(User user);

    int updatePasswordIfUnchanged(@Param("userId") Long userId,
                                  @Param("expectedPassword") String expectedPassword,
                                  @Param("password") String password);

    int deleteIfPasswordUnchanged(@Param("userId") Long userId,
                                 @Param("expectedPassword") String expectedPassword);

    int deleteByUserId(Long userId);

}
