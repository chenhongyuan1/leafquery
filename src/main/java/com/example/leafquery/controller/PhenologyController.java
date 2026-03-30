package com.example.leafquery.controller;

import com.example.leafquery.dto.PhenologyEstimateRequest;
import com.example.leafquery.dto.PhenologyEstimateResponse;
import com.example.leafquery.service.PhenologyEstimateService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/farm/phenology")
@CrossOrigin(origins = "*")
public class PhenologyController {

    private final PhenologyEstimateService phenologyEstimateService;

    public PhenologyController(PhenologyEstimateService phenologyEstimateService) {
        this.phenologyEstimateService = phenologyEstimateService;
    }

    @PostMapping("/estimate")
    public PhenologyEstimateResponse estimate(@RequestBody PhenologyEstimateRequest request) {
        return phenologyEstimateService.estimate(request, LocalDate.now());
    }
}
