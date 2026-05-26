package com.yuno.provider.api;

import com.yuno.common.dto.ProviderProcessRequest;
import com.yuno.common.dto.ProviderProcessResponse;
import com.yuno.common.response.ApiResponse;
import com.yuno.provider.router.ProviderRouter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderRouter providerRouter;

    @PostMapping("/process")
    public ResponseEntity<ProviderProcessResponse> process(
            @Valid @RequestBody ProviderProcessRequest request) {
        log.info("Provider routing request. paymentId={}, method={}",
                request.getPaymentId(), request.getPaymentMethod());
        ProviderProcessResponse response = providerRouter.route(request);
        return ResponseEntity.ok(response);
    }
}
