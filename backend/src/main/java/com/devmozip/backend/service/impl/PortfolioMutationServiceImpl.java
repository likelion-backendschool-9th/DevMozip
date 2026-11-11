package com.devmozip.backend.service.impl;

import com.devmozip.backend.dto.portfolio.*;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PortfolioMutationRepository;
import com.devmozip.backend.service.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor @Transactional
public class PortfolioMutationServiceImpl implements PortfolioMutationService {
    private final MemberRepository members;
    private final PortfolioMutationRepository repository;
    private final PortfolioQueryService queries;
    private final PortfolioDocumentPolicy policy;
    private final ObjectMapper mapper;
    private final com.devmozip.backend.storage.LocalPortfolioFileStorage files;
    @Override public PortfolioDetailResponse create(String email,PortfolioWriteRequest input){
        var member=viewer(email);var value=stored(input,member.getId());
        try{return queries.detail(email,repository.create(member.getId(),value,now()));}
        catch(DataAccessException e){throw unavailable();}
    }
    @Override public PortfolioDetailResponse update(String email,long id,PortfolioWriteRequest input){
        var member=viewer(email);
        try{
            var old=owned(id,member);
            if(!Objects.equals(input.expectedUpdatedAt(),old.updatedAt()))throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 곳에서 변경되었습니다. 최신 내용을 다시 불러와 주세요.");
            var value=stored(input,member.getId());
            if(!old.visibility().equals(value.visibility())&&repository.referenced(id)&&!repository.submittedCopiesReady(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"제출본이 보관되지 않은 포트폴리오는 공개 범위를 변경할 수 없습니다.");
            var time=now();if(old.updatedAt()!=null&&!time.isAfter(old.updatedAt()))time=old.updatedAt().plusNanos(1000);
            repository.update(id,member.getId(),value,time);return queries.detail(email,id);
        }catch(DataAccessException e){throw unavailable();}
    }
    @Override public void delete(String email,long id){
        var member=viewer(email);
        try{
            owned(id,member);
            if(!repository.referenceConstraintReady())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"지원서 참조 제약이 준비되지 않아 삭제할 수 없습니다.");
            if(repository.referenced(id)&&!repository.submittedCopiesReady(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"제출본이 보관되지 않은 포트폴리오는 삭제할 수 없습니다.");
            repository.delete(id,member.getId());
        }catch(org.springframework.dao.DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 기능에서 참조 중인 포트폴리오는 삭제할 수 없습니다.");}
        catch(DataAccessException e){throw unavailable();}
    }
    @Override public PortfolioDetailResponse changeVisibility(String email,long id,PortfolioVisibilityRequest input){
        var member=viewer(email);
        try{
            var old=owned(id,member);
            if(!Objects.equals(input.expectedUpdatedAt(),old.updatedAt()))throw new ResponseStatusException(HttpStatus.CONFLICT,"다른 곳에서 변경되었습니다. 최신 내용을 다시 불러와 주세요.");
            String visibility=input.visibility().name();
            if(old.visibility().equals(visibility))return queries.detail(email,id);
            // 제출본 보관이 확인되면 원본 범위 변경을 허용한다. 지원서/보관본은 변경하지 않는다.
            if(repository.referenced(id)&&!repository.submittedCopiesReady(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"제출본이 보관되지 않은 포트폴리오는 공개 범위를 변경할 수 없습니다.");
            var time=now();if(old.updatedAt()!=null&&!time.isAfter(old.updatedAt()))time=old.updatedAt().plusNanos(1000);
            repository.changeVisibility(id,member.getId(),visibility,time);
            return queries.detail(email,id);
        }catch(DataAccessException e){throw unavailable();}
    }
    private PortfolioMutationRepository.Locked owned(long id,Member member){
        if(id<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ID를 확인해 주세요.");
        var old=repository.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"포트폴리오를 찾을 수 없습니다."));
        if(old.userId()!=member.getId()||!List.of("PUBLIC","PRIVATE").contains(Objects.toString(old.visibility(),"")))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"포트폴리오를 찾을 수 없습니다.");
        return old;
    }
    private Member viewer(String email){
        if(email==null||email.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"로그인이 필요합니다.");
        var m=members.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"회원 정보를 찾을 수 없습니다."));
        if(m.getStatus()!=Member.MemberStatus.ACTIVE&&m.getStatus()!=Member.MemberStatus.WARNED)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"이 계정은 수정할 수 없습니다.");
        return m;
    }
    private PortfolioMutationRepository.Stored stored(PortfolioWriteRequest p,long ownerId){
        var doc=policy.clean(p.document());
        var stack=policy.labels(p.techStack(),30,100);
        var attachments=p.attachments()==null?List.<PortfolioWriteRequest.PortfolioAttachmentRequest>of():p.attachments().stream()
                .map(a->new PortfolioWriteRequest.PortfolioAttachmentRequest(a.name(),policy.url(a.url(),false))).toList();
        verifyFile(p.thumbnailUrl(),ownerId,true);
        for(var a:attachments)verifyFile(a.url(),ownerId,false);
        if(doc!=null)for(var b:doc.blocks()){
            for(var image:b.images())verifyFile(image.src(),ownerId,true);
            for(var link:b.links())verifyFile(link.url(),ownerId,false);
        }
        if(doc==null&&p.description()!=null&&p.description().stripLeading().startsWith("{")&&p.description().contains("devmozip.portfolio."))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"구조화 문서는 document 필드로 전달해 주세요.");
        String description=doc==null?p.description():policy.encode(doc);
        if(description!=null&&description.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"문서 크기는 2MB 이내여야 합니다.");
        String topic=doc!=null&&!doc.fields().isEmpty()?doc.fields().get(0):p.topic();
        return new PortfolioMutationRepository.Stored(p.title().strip(),policy.url(p.thumbnailUrl(),true),topic,p.summary().strip(),description,
                p.role().strip(),mapper.writeValueAsString(stack),mapper.writeValueAsString(attachments),p.visibility()==null?"PRIVATE":p.visibility());
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.MICROS);}
    private void verifyFile(String url,long ownerId,boolean image){
        if(url==null||!url.startsWith("/api/files/"))return;
        var file=files.metadata(url.substring("/api/files/".length()));
        if(file.ownerId()!=ownerId)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"본인이 업로드한 파일만 연결할 수 있습니다.");
        if(image&&!file.contentType().startsWith("image/"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"이미지에는 이미지 파일만 연결할 수 있습니다.");
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오 저장소를 사용할 수 없습니다.");}
}
