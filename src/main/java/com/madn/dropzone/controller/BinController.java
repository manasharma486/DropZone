package com.madn.dropzone.controller;

import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.service.BinService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bin")
public class BinController {
    private BinService binService;

    public BinController(BinService binService) {
        this.binService = binService;
    }

    @GetMapping
    public ResponseEntity<List<StoredFileDto>> getFilesInBin(
            @RequestParam("userid") UUID userid,
            @RequestParam(value = "type", required = false, defaultValue = "") String type) {

        List<StoredFileDto> storedFileDtoList =
                binService.getFilesInBin(userid, type);
        return ResponseEntity.ok(storedFileDtoList);
    }

    @PostMapping("/restore")
    public ResponseEntity<String> restoreFiles(@RequestParam("userid") UUID userid ,
                                               @RequestBody BinFileDto binFileDto) {
        String s = binService.restoreFiles(userid , binFileDto);
        return ResponseEntity.ok(s);
    }

    @PostMapping("/restore-all")
    public ResponseEntity<String> restoreAllFiles(@RequestParam("userid") UUID userid) {
        String s = binService.restoreAllFiles(userid);
        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/empty")
    public ResponseEntity<String> deleteFiles(@RequestParam("userid") UUID userid ,
                                              @RequestBody BinFileDto binFileDto) {
        String s = binService.deleteFiles(userid, binFileDto);
        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/empty-all")
    public ResponseEntity<String> deleteAllFiles(@RequestParam("userid") UUID userid) {
        String s = binService.deleteAllFiles(userid);
        return ResponseEntity.ok(s);
    }

}
