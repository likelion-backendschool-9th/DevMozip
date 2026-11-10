package com.devmozip.backend.dto.ai;

import com.devmozip.backend.entity.AiUsage;

public record AiUsageResponse(String usageMonth, int usedCount, int limit, int remainingCount, int pendingCount, int availableCount) {
	public static AiUsageResponse from(AiUsage usage, int limit) {
		return new AiUsageResponse(usage.getUsageMonth(), usage.getUsedCount(), limit,
			Math.max(0, limit - usage.getUsedCount()), usage.getPendingCount(),
			Math.max(0, limit - usage.getUsedCount() - usage.getPendingCount()));
	}
}
