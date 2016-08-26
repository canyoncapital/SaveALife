package com.savelife.mvc.service.user;

import com.savelife.mvc.model.user.UserEntity;
import com.savelife.mvc.model.user.UserRoleEntity;
import com.savelife.mvc.repository.UserRepository;
import com.savelife.mvc.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by anton on 16.08.16.
 */
@Service("userService")
public class UserServiceImpl implements UserService {

    @Resource
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Override
    public UserEntity findUserByToken(String token) {
        return userRepository.findByToken(token);
    }

    @Override
    public UserEntity findUserById(long idUser) {
        return userRepository.findOne(idUser);
    }

    @Override
    public List<UserEntity> findAllUsers() {
        return (List)userRepository.findAll();
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<UserEntity> findAllByRole(String role) {
        UserRoleEntity userRole = userRoleRepository.findByUserRole(role);
        List<UserEntity> list = new ArrayList<>();
//        dao.findAllUsers().forEach((k) -> {
//            if (k.getUser_role().getUser_role().equals(role)) {
//                list.add(k);
//            }
//        });
        return userRepository.findAllByUserRole(userRole);
    }

    @Override
    public boolean exist(String token) {
        return userRepository.findByToken(token) != null;
    }

    @Override
    public void save(UserEntity userEntity) {
        userRepository.save(userEntity);
    }

    @Override
    public void delete(UserEntity entity) {
        userRepository.delete(entity);
    }

    @Override
    public void deleteByToken(String token) {
        userRepository.deleteByToken(token);
    }

    /*
     * Since the method is running with Transaction, No need to call hibernate update explicitly.
     * Just fetch the entity from db and update it with proper values within transaction.
     * It will be updated in db once transaction ends.
     * */
    @Override
    public void update(UserEntity entity) {
        userRepository.update(entity.getToken(), entity.getUserRole().getId(),
                entity.getCurrentLatitude(), entity.getCurrentLongitude(), entity.isEnable(), entity.getIdUser());

    }


}
