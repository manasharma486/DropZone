package com.madn.dropzone.controller;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.dto.UserDto;
import com.madn.dropzone.entity.User;
import com.madn.dropzone.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private UserService service;
    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> createUser(@RequestBody UserDto user) {
        String response = service.createUser(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/bin")
    public ResponseEntity<List<StoredFileDto>> getFilesInBin(@RequestParam("userid") UUID userid) {
        List<StoredFileDto> storedFileDtoList = service.getFilesInBin(userid);

        return ResponseEntity.ok(storedFileDtoList);
    }

    @DeleteMapping("/bin/empty")
    public ResponseEntity<String> emptyBin(@RequestParam("userid") UUID userid) {
        String s = service.emptyBin(userid);

        return ResponseEntity.ok(s);
    }

    @GetMapping("/drive")
    public ResponseEntity<List<StoredFileDto>> getFiles(@RequestParam("userid") UUID userid) {
        List<StoredFileDto> storedFileDtoList = service.getFilesInDrive(userid);

        return ResponseEntity.ok(storedFileDtoList);
    }
}
