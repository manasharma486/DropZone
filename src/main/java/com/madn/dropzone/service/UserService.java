package com.madn.dropzone.service;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.dto.UserDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.User;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import static com.madn.dropzone.mapper.FileMapper.fileToFileDto;

@Service
public class UserService {
    private UserRepository userRepository;
    private FileRepository fileRepository;

    public UserService(UserRepository userRepository , FileRepository fileRepository) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
    }


    public String createUser(UserDto userDto) {
        User user = new User();
        StorageAccount account = new StorageAccount();

        user.setAccount(account);
        user.setName(userDto.getName());
        user.setAge(userDto.getAge());
        user.setEmail(userDto.getEmail());
        user.setPasswordHash(userDto.getPassword());

        userRepository.save(user);
        return user.getId().toString();
    }
}
