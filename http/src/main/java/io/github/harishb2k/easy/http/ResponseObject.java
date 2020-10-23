package io.github.harishb2k.easy.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.gitbub.harishb2k.easy.helper.json.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseObject {
    private byte[] body;

    public Map<String, Object> convertAsMap() {
        if (body == null) return null;
        return JsonUtils.convertAsMap(new String(body));
    }
}
