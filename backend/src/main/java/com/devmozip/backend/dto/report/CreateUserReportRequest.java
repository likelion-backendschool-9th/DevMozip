package com.devmozip.backend.dto.report;

import com.devmozip.backend.utils.StrictStringDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateUserReportRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String reason) {
}
