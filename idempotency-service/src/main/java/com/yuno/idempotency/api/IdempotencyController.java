package com.yuno.idempotency.api;

import com.yuno.common.dto.IdempotencyCheckRequest;
import com.yuno.common.dto.IdempotencyCheckResponse;
import com.yuno.common.dto.IdempotencyStoreRequest;
import com.yuno.idempotency.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/idempotency")
@RequiredArgsConstructor
public class IdempotencyController {

    private final IdempotencyService idempotencyService;

    @PostMapping("/check")
    public ResponseEntity<IdempotencyCheckResponse> check(
            @RequestBody IdempotencyCheckRequest request) {
        IdempotencyCheckResponse response = idempotencyService.check(request.getIdempotencyKey());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/store")
    public ResponseEntity<Void> store(@RequestBody IdempotencyStoreRequest request) {
        idempotencyService.store(request.getIdempotencyKey(), request.getResponse());
        return ResponseEntity.ok().build();
    }
}
