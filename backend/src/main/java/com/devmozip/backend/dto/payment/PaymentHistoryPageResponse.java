package com.devmozip.backend.dto.payment;

import java.util.List;

/** 13.5 결제 내역 페이지. 화면은 hasNext가 true일 때 다음 페이지를 이어 붙인다 */
public record PaymentHistoryPageResponse(List<PaymentHistoryItemResponse> items, int page, int size, boolean hasNext) {
}
