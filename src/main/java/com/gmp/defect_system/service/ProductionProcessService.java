package com.gmp.defect_system.service;

import com.gmp.defect_system.entity.ProductionProcess;
import com.gmp.defect_system.repository.ProductionProcessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductionProcessService {
    private final ProductionProcessRepository repository;

    public ProductionProcessService(ProductionProcessRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductionProcess> listAll() {
        return repository.findAllByOrderByProcessNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ProductionProcess> listActive() {
        return repository.findByIsActiveTrueOrderByProcessNameAsc();
    }

    @Transactional
    public ProductionProcess add(String rawName) {
        String name = normalize(rawName);
        rejectDuplicate(name, null);
        ProductionProcess process = new ProductionProcess();
        process.setProcessName(name);
        process.setIsActive(true);
        return repository.save(process);
    }

    @Transactional
    public ProductionProcess rename(Long id, String rawName) {
        ProductionProcess process = get(id);
        String name = normalize(rawName);
        if (!process.getProcessName().equals(name)) {
            // Hindari merusak konsistensi transaksi lama yang masih menyimpan nama sebagai teks.
            if (!process.getProcessName().equalsIgnoreCase(name)
                    && repository.isNameReferenced(process.getProcessName())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Nama proses sudah dipakai dalam data produksi/defect. Nonaktifkan proses lama dan buat nama baru.");
            }
            rejectDuplicate(name, id);
            process.setProcessName(name);
        }
        return repository.save(process);
    }

    @Transactional
    public ProductionProcess setActive(Long id, boolean active) {
        ProductionProcess process = get(id);
        process.setIsActive(active);
        return repository.save(process);
    }

    @Transactional(readOnly = true)
    public void validateActiveName(String rawName) {
        String name = normalize(rawName);
        boolean valid = repository.findByProcessNameIgnoreCase(name)
                .map(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElse(false);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Proses tidak ditemukan atau tidak aktif: " + name);
        }
    }

    private ProductionProcess get(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Proses tidak ditemukan"));
    }

    private String normalize(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nama proses wajib diisi");
        }
        String name = rawName.trim();
        if (name.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nama proses maksimal 150 karakter");
        }
        return name;
    }

    private void rejectDuplicate(String name, Long exceptId) {
        repository.findByProcessNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(exceptId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Nama proses sudah tersedia");
            }
        });
    }
}
