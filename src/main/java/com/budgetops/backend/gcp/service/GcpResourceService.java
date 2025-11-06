package com.budgetops.backend.gcp.service;

import com.budgetops.backend.gcp.entity.GcpResource;
import com.budgetops.backend.gcp.repository.GcpResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GcpResourceService {
    private final GcpResourceRepository resourceRepository;


}

