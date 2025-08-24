package com.ruchira.murex.controller;

import com.ruchira.murex.dto.InstructionRequestDto;
import com.ruchira.murex.service.InboundInstructionProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Slf4j
public class InstructionProcessingController {

    private final InboundInstructionProcessingService inboundInstructionProcessingService;

    @GetMapping("/process-instruction")
    public ResponseEntity<Object> fetchData(InstructionRequestDto instructionRequestDto) {

        try {

           inboundInstructionProcessingService.processInstruction(instructionRequestDto);
            return ResponseEntity.ok("SUCCESS");
        } catch (Exception e) {
            // Log the error and return appropriate response
            log.error("Error fetching data: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }


}