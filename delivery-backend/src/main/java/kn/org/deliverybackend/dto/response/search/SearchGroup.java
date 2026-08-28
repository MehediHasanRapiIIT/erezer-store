package kn.org.deliverybackend.dto.response.search;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SearchGroup {
    private List<SearchItem> items;
    private long total;

    public static SearchGroup empty() {
        return SearchGroup.builder().items(List.of()).total(0L).build();
    }
}
