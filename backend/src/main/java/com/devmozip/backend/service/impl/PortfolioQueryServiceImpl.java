package com.devmozip.backend.service.impl;

import com.devmozip.backend.service.PortfolioQueryService;
import com.devmozip.backend.service.PortfolioDocumentPolicy;

import com.devmozip.backend.dto.portfolio.*;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PortfolioQueryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class PortfolioQueryServiceImpl implements PortfolioQueryService {
    private final MemberRepository members;
    private final PortfolioQueryRepository portfolios;
    private final ObjectMapper mapper;
    private final PortfolioDocumentPolicy documents;

    @Override
    public PortfolioPageResponse own(String email,int page,int size) {
        validatePage(page,size);
        return available(()->list(viewer(email).getId(),true,page,size));
    }
    @Override
    public PortfolioPageResponse publicList(String slug,int page,int size) {
        validatePage(page,size);
        if(slug==null||!slug.matches("[A-Za-z0-9_-]{1,100}")||slug.equalsIgnoreCase("me")) throw missing();
        return available(()->{
            var owner=members.findByTagAndStatus(slug,Member.MemberStatus.ACTIVE).orElseThrow(PortfolioQueryServiceImpl::missing);
            return list(owner.getId(),false,page,size);
        });
    }
    @Override
    public PortfolioDetailResponse detail(String email,long id) {
        if(id<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"포트폴리오 ID를 확인해 주세요.");
        return available(()->{
            var viewer=viewer(email);
            var access=portfolios.access(id).orElseThrow(PortfolioQueryServiceImpl::missing);
            boolean own=access.userId()==viewer.getId();
            boolean known=List.of("PUBLIC","PRIVATE").contains(access.visibility()==null?"":access.visibility());
            if(!known||(!own&&(!"PUBLIC".equals(access.visibility())||!"ACTIVE".equals(access.ownerStatus())))) throw missing();
            // 클라이언트 데모 구독이나 오래된 JWT 권한이 아니라 현재 users.role로 검사한다.
            if(!own&&viewer.getRole()!=Member.MemberRole.PREMIUM&&viewer.getRole()!=Member.MemberRole.ADMIN)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"다른 회원의 상세 포트폴리오는 프리미엄 권한이 필요합니다.");
            var row=portfolios.detail(id).orElseThrow(PortfolioQueryServiceImpl::missing);
            var document=documents.decode(row.description());
            return new PortfolioDetailResponse(summary(row.summary()),new PortfolioDetailResponse.Author(access.userId(),
                    row.nickname(),row.tag(),row.profileImage()),document==null?row.description():documents.plain(document),attachments(row.attachments()),document,own);
        });
    }
    private Member viewer(String email) {
        if(email==null||email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
        var member=members.findByEmail(email).orElseThrow(PortfolioQueryServiceImpl::missing);
        if(member.getStatus()!=Member.MemberStatus.ACTIVE&&member.getStatus()!=Member.MemberStatus.WARNED)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"이 계정으로 조회할 수 없습니다.");
        return member;
    }
    private PortfolioPageResponse list(long userId,boolean own,int page,int size) {
        var result=portfolios.list(userId,own,page,size);
        return new PortfolioPageResponse(result.rows().stream().map(this::summary).toList(),page,size,result.total(),
                result.total()/size+(result.total()%size==0?0:1));
    }
    private PortfolioSummaryResponse summary(PortfolioQueryRepository.Summary row) {
        return new PortfolioSummaryResponse(row.id(),row.userId(),row.title(),row.thumbnailUrl(),row.topic(),row.summary(),
                row.role(),stack(row.techStack()),row.visibility(),row.createdAt(),row.updatedAt());
    }
    private List<String> stack(String raw) {
        if(raw==null) return List.of();
        var values=mapper.readTree(raw);
        if(!values.isArray()||values.size()>100) throw invalidData();
        var result=new ArrayList<String>();
        for(var value:values){
            if(!value.isString()||value.asString().isBlank()||value.asString().length()>100) throw invalidData();
            if(!result.contains(value.asString()))result.add(value.asString());
        }
        return List.copyOf(result);
    }
    private List<PortfolioDetailResponse.Attachment> attachments(String raw) {
        if(raw==null) return List.of();
        var values=mapper.readTree(raw);
        if(!values.isArray()||values.size()>100) throw invalidData();
        var result=new ArrayList<PortfolioDetailResponse.Attachment>();
        for(var value:values){
            if(!value.isObject()||!value.path("url").isString()) throw invalidData();
            var name=value.path("name");
            if(!name.isMissingNode()&&!name.isNull()&&!name.isString()) throw invalidData();
            result.add(new PortfolioDetailResponse.Attachment(name.isString()?name.asString():null,value.path("url").asString()));
        }
        return List.copyOf(result);
    }
    private void validatePage(int page,int size){
        if(page<0||page>1000000||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"페이지와 조회 개수를 확인해 주세요.");
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"포트폴리오 정보를 찾을 수 없습니다.");}
    private static ResponseStatusException invalidData(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오 데이터를 확인할 수 없습니다.");}
    private <T>T available(Supplier<T> operation){
        try{return operation.get();}
        catch(DataAccessException|tools.jackson.core.JacksonException exception){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오를 조회할 수 없습니다.");
        }
    }
}
