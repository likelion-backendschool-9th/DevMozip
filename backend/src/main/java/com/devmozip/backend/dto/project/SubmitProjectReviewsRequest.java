package com.devmozip.backend.dto.project;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record SubmitProjectReviewsRequest(@NotBlank @Size(max=128) String token,@NotEmpty List<@NotNull @Valid Entry> entries){
 @Override public String toString(){return "SubmitProjectReviewsRequest[REDACTED]";}
 public record Entry(@Positive long targetMemberId,@NotNull @Min(1) @Max(5) Integer rating,@NotNull @Size(max=5) List<@NotBlank @Size(max=100) String> keywords){}
}
