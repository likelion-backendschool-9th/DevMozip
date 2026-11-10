package com.devmozip.backend.dto.portfolio;

import java.util.List;
/** description TEXT 안에 저장하는 버전 있는 편집 문서. DB 컬럼을 추가하지 않는다. */
public record PortfolioDocument(String schema,String start,String end,String teamSize,List<String> fields,List<Block> blocks) {
    public record Block(String id,String kind,String title,String content,String format,List<Image> images,List<Link> links,String source) {}
    public record Image(String src,String caption) {}
    public record Link(String label,String url) {}
}
