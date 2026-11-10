package com.devmozip.backend.service;

import com.devmozip.backend.dto.portfolio.PortfolioDocument;
import java.net.URI;
import java.time.YearMonth;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.owasp.html.HtmlPolicyBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class PortfolioDocumentPolicy {
    public static final String SCHEMA="devmozip.portfolio.v1";
    private final ObjectMapper mapper;
    private final ProfileIntroductionPolicy html;
    public PortfolioDocument clean(PortfolioDocument d) {
        if(d==null)return null;
        if(!SCHEMA.equals(d.schema()))throw bad("지원하지 않는 포트폴리오 문서 버전입니다.");
        try{if(YearMonth.parse(d.end()).isBefore(YearMonth.parse(d.start())))throw bad("진행 기간을 확인해 주세요.");}
        catch(java.time.DateTimeException|NullPointerException e){throw bad("시작·종료 월을 확인해 주세요.");}
        if(d.teamSize()!=null&&d.teamSize().length()>100)throw bad("팀 구성은 100자 이내입니다.");
        var fields=labels(d.fields(),30,40);
        if(d.blocks()==null||d.blocks().isEmpty()||d.blocks().size()>20)throw bad("섹션은 1~20개입니다.");
        var ids=new HashSet<String>();var sources=new HashSet<String>();var blocks=new ArrayList<PortfolioDocument.Block>();int total=0;
        for(var b:d.blocks()) {
            if(b==null||b.id()==null||!b.id().matches("[a-zA-Z0-9_-]{1,80}")||!ids.add(b.id())||b.title()==null||b.title().isBlank()||b.title().length()>80
                ||b.content()==null||b.content().length()>20000||!List.of("text","gallery","links").contains(Objects.toString(b.kind(),""))
                ||!List.of("text","markdown","html").contains(Objects.toString(b.format(),"")))throw bad("섹션 형식을 확인해 주세요.");
            total+=b.content().length();if(total>150000)throw bad("문서 본문은 합계 150,000자 이내입니다.");
            if(b.source()!=null&&(!List.of("description","contribution","features","challenge","outcome").contains(b.source())||!sources.add(b.source())))throw bad("섹션 연결 정보가 올바르지 않습니다.");
            var images=new ArrayList<PortfolioDocument.Image>();
            if(b.images()!=null){if(b.images().size()>6)throw bad("이미지는 섹션당 6개까지입니다.");for(var image:b.images()){
                if(image==null||image.caption()==null||image.caption().length()>150)throw bad("이미지 설명을 확인해 주세요.");
                images.add(new PortfolioDocument.Image(url(image.src(),false),image.caption()));}}
            var links=new ArrayList<PortfolioDocument.Link>();
            if(b.links()!=null){if(b.links().size()>6)throw bad("링크는 섹션당 6개까지입니다.");for(var link:b.links()){
                if(link==null||link.label()==null||link.label().isBlank()||link.label().length()>40)throw bad("링크 이름을 확인해 주세요.");
                links.add(new PortfolioDocument.Link(link.label().strip(),url(link.url(),false)));}}
            String content="html".equals(b.format())?Objects.toString(html.clean(b.content(),"html"),""):b.content();
            blocks.add(new PortfolioDocument.Block(b.id(),b.kind(),b.title().strip(),content,b.format(),List.copyOf(images),List.copyOf(links),b.source()));
        }
        return new PortfolioDocument(SCHEMA,d.start(),d.end(),d.teamSize(),fields,List.copyOf(blocks));
    }
    public String encode(PortfolioDocument d){return mapper.writeValueAsString(clean(d));}
    public PortfolioDocument decode(String stored) {
        if(stored==null||!stored.stripLeading().startsWith("{")||!stored.contains("devmozip.portfolio."))return null;
        try{
            var value=mapper.readTree(stored);
            if(!value.isObject()||!value.path("schema").isString()||!value.path("schema").asString().startsWith("devmozip.portfolio."))return null;
            return clean(mapper.treeToValue(value,PortfolioDocument.class));
        }catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"저장된 포트폴리오 문서 형식을 확인해 주세요.");}
    }
    public String plain(PortfolioDocument d){
        var strip=new HtmlPolicyBuilder().toFactory();
        return String.join("\n\n",d.blocks().stream().filter(b->"text".equals(b.kind())).map(b->b.title()+"\n"+("html".equals(b.format())?strip.sanitize(b.content()):b.content())).toList());
    }
    public List<String> labels(List<String> source,int max,int length){
        if(source==null||source.size()>max)throw bad("선택 항목 수를 확인해 주세요.");
        var result=new ArrayList<String>();var keys=new HashSet<String>();
        for(var v:source){if(v==null||v.isBlank()||v.strip().length()>length||!keys.add(v.strip().toLowerCase(Locale.ROOT)))throw bad("선택 항목의 길이·중복을 확인해 주세요.");result.add(v.strip());}
        return List.copyOf(result);
    }
    public String url(String value,boolean optional){
        if(optional&&(value==null||value.isBlank()))return null;
        try{
            if(value==null||value.length()>2048||value.chars().anyMatch(c->c<32||c==127)||value.contains("\\"))throw new IllegalArgumentException();
            var uri=URI.create(value);
            if(value.startsWith("/")&&!value.startsWith("//"))return value;
            if(!List.of("http","https").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getRawUserInfo()!=null)throw new IllegalArgumentException();
            return uri.toString();
        }catch(IllegalArgumentException e){throw bad("이미지·첨부 링크는 올바른 http(s) URL 또는 동일 출처 경로여야 합니다. 파일 업로드는 후속 기능입니다.");}
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
