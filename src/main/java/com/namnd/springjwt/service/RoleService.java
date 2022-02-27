package com.namnd.springjwt.service;

import com.namnd.springjwt.model.Role;

public interface RoleService {

    void save(Role role);

    Role findByName(String name);

    //Đẩy thay đổi vào DB để query lại
    void flush();
}
