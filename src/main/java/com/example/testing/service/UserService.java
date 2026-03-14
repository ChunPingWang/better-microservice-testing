package com.example.testing.service;

import com.example.testing.dto.UserDto;
import com.example.testing.entity.User;
import com.example.testing.exception.UserNotFoundException;
import com.example.testing.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public User create(UserDto userDto) {
        User user = new User(userDto.getName(), userDto.getEmail());
        return userRepository.save(user);
    }

    public User update(Long id, UserDto userDto) {
        User user = findById(id);
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());
        return userRepository.save(user);
    }

    public List<User> searchByName(String name) {
        if (name == null || name.isBlank()) {
            return userRepository.findAll();
        }
        return userRepository.findByNameContainingIgnoreCase(name);
    }

    public void delete(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }
}
