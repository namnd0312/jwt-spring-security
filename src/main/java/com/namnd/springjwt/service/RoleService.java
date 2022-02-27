package com.namnd.springjwt.service;

import com.namnd.springjwt.model.Role;
import com.namnd.springjwt.model.User;

import java.util.Optional;

public interface RoleService {

    void save(Role role);

    Role findByName(String name);

    //Đẩy thay đổi vào DB để query lại
    void flush();
}
