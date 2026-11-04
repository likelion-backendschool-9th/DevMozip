package com.devmozip.backend.dto.profile;

import com.devmozip.backend.entity.Member;
import java.util.List;

public record ProfileTagsResponse(List<String> skills, List<String> interests) {
    public static ProfileTagsResponse from(Member member) {
        return new ProfileTagsResponse(member.getSkills() == null ? List.of() : List.copyOf(member.getSkills()),
                member.getInterests() == null ? List.of() : List.copyOf(member.getInterests()));
    }
}
