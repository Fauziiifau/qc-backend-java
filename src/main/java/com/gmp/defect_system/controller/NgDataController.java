package com.gmp.defect_system.controller;

import com.gmp.defect_system.dto.NgDataRequest;
import com.gmp.defect_system.entity.NgData;
import com.gmp.defect_system.service.NgDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ng-data")
@CrossOrigin(origins = "*")
public class NgDataController {

    private final NgDataService ngDataService;

    public NgDataController(NgDataService ngDataService) {
        this.ngDataService = ngDataService;
    }

    @GetMapping
    public ResponseEntity<List<NgData>> getAll() {
        return ResponseEntity.ok(ngDataService.getAllNgData());
    }

    @PostMapping
    public ResponseEntity<NgData> create(@RequestBody NgDataRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "System";

        return new ResponseEntity<>(ngDataService.saveNewDefect(request, username), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NgData> update(@PathVariable Long id, @RequestBody NgData ngData) {
        return ResponseEntity.ok(ngDataService.updateNgData(id, ngData));
    }
}