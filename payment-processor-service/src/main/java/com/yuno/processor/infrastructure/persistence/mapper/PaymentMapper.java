package com.yuno.processor.infrastructure.persistence.mapper;

import com.yuno.common.dto.CreatePaymentRequest;
import com.yuno.common.dto.CreatePaymentResponse;
import com.yuno.common.dto.PaymentDetailsResponse;
import com.yuno.common.dto.PaymentEventDto;
import com.yuno.processor.infrastructure.persistence.entity.PaymentEntity;
import com.yuno.processor.infrastructure.persistence.entity.PaymentEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "providerTransactionId", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "retryCount", ignore = true)
    @Mapping(target = "correlationId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "events", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    PaymentEntity toEntity(CreatePaymentRequest request);

    @Mapping(target = "paymentId", expression = "java(entity.getId().toString())")
    @Mapping(target = "idempotencyHit", constant = "false")
    CreatePaymentResponse toCreateResponse(PaymentEntity entity);

    @Mapping(target = "paymentId", expression = "java(entity.getId().toString())")
    @Mapping(target = "events", source = "events")
    PaymentDetailsResponse toDetailsResponse(PaymentEntity entity);

    @Mapping(target = "eventId", expression = "java(event.getId().toString())")
    PaymentEventDto toEventDto(PaymentEventEntity event);

    List<PaymentEventDto> toEventDtoList(List<PaymentEventEntity> events);
}
