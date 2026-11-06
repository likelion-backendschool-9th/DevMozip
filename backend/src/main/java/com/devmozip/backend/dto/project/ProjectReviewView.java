package com.devmozip.backend.dto.project;
import java.time.LocalDateTime;
import java.util.List;
public record ProjectReviewView(long projectId,String title,LocalDateTime completedAt,LocalDateTime expiresAt,boolean submitted,boolean expired,List<String> keywords,List<Target> targets){
 public record Target(long memberId,long userId,String nickname,String tag,String status,Integer rating,List<String> keywords){}
 public record Link(String href,LocalDateTime expiresAt){@Override public String toString(){return "ReviewLink[REDACTED]";}}
 public record RequestStatus(long userId,String nickname,int total,int submitted,LocalDateTime expiresAt){}
 public record Summary(long userId,long count,double average,List<Long> distribution,List<Keyword> keywords,List<Received> reviews){ }
 public record Keyword(String name,long count){}
 public record Received(long id,long projectId,String projectTitle,long authorUserId,String authorNickname,String authorTag,int rating,List<String> keywords,LocalDateTime submittedAt){}
}
