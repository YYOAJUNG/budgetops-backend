package com.budgetops.backend.gcp.controller;

import com.budgetops.backend.gcp.service.GcpResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gcp/resources")
@RequiredArgsConstructor
public class GcpResourceController {

    private final GcpResourceService service;

}

