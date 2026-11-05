package com.devmozip.backend.dto.profile;

import com.devmozip.backend.entity.Member;
import java.util.List;

public record ProfileOptionsResponse(List<String> skills, List<String> interests) {
    public static ProfileOptionsResponse from(Member member) {
        return new ProfileOptionsResponse(member.getSkills() == null ? List.of() : List.copyOf(member.getSkills()),
                member.getInterests() == null ? List.of() : List.copyOf(member.getInterests()));
    }
}
