package kn.org.deliverybackend.dto.response.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchItem {
    private String id;
    private String type;
    private String title;
    private String subtitle;
    private String imageUrl;
    private Map<String, Object> extra;
}
