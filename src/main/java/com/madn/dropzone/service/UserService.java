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

@Service
public class UserService {
    private UserRepository userRepository;
    private FileRepository fileRepository;
    private StorageAccountRepository storageAccountRepository;
    private StorageService storageService;

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

    public List<StoredFileDto> getFilesInBin(UUID userid) {
        boolean check  = userRepository.existsById(userid);
        if(check) {
            List<StoredFileDto> storedFileDtoList = fileRepository.getStoredFileFromStatus(userid , FileStatus.DELETED).
                    stream().map((file)-> fileToFileDto(file)).toList();

            return storedFileDtoList;
        }

        else {
            throw new RuntimeException("User Does not Exists !!");
        }
    }

    public List<StoredFileDto> getFilesInDrive(UUID userid) {
        boolean check  = userRepository.existsById(userid);
        if(check) {
            List<StoredFileDto> storedFileDtoList = fileRepository.getStoredFileFromStatus(userid , FileStatus.ACTIVE).
                    stream().map((file)-> fileToFileDto(file)).toList();

            return storedFileDtoList;
        }

        else {
            throw new RuntimeException("User Does not Exists !!");
        }
    }


    private StoredFileDto fileToFileDto(StoredFile file) {
        StoredFileDto fileDto = new StoredFileDto();

        fileDto.setFileName(file.getFileName());
        fileDto.setFileSize(file.getFileSize());
        fileDto.setStatus(file.getStatus());
        fileDto.setContentType(file.getContentType());
        fileDto.setUploaded_at(file.getUploadedAt());

        return fileDto;

    }

    public String emptyBin(UUID userid) {
        StorageAccount account = storageAccountRepository.
                findByUserId(userid).orElseThrow(()-> new RuntimeException("User Does Not Exists"));

        List<StoredFile> fileList = fileRepository.getStoredFileFromStatus(userid, FileStatus.DELETED);


        for (StoredFile file : fileList) {
            account.setUsedBytes(account.getUsedBytes() - file.getFileSize());
            storageService.deleteFromStorage(file.getStorageKey());
        }

        storageAccountRepository.save(account);
        fileRepository.removeAllByStatus_Deleted();

        return "Deletion Successfull !!";
    }
}
