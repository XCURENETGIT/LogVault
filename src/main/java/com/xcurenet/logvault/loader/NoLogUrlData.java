package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.FastWildcardMatch;
import lombok.Data;
import lombok.ToString;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.stereotype.Component;

@Data
@ToString
@Component
public class NoLogUrlData {

	public record NoLogUrlInfo(@Field("URL") String url) {
	}

	private FastWildcardMatch data = new FastWildcardMatch(false);
}
