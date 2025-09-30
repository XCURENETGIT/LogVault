package com.xcurenet.logvault.loader;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.common.utils.FastWildcardMatch;
import com.xcurenet.logvault.database.DBCommon;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
@Service
@RequiredArgsConstructor
public class NoLogUrlLoader {

	private final DBCommon dbCommon;
	private final AtomicReference<NoLogUrlData> nologUrlData = new AtomicReference<>();

	/**
	 * 필터링 URL Load
	 * FastWildcardMatch 방식으로 * ? 를 지원하고, 대소문자 구분도 지원함. (기본적으로 대소문자는 구분하지 않음)
	 * 등록된 필터 URL 과 완벽히 일치하는 내용을 찾는다. (강제로 *를 붙여서 www.sw.or.kr
	 * 등록 : www.sw.or.kr  로깅 : www.sw.or.kr/xcn.jsp > 이럴경우 필터링이 되지 않는다.
	 * 그래서 등록할때 *가 없는 경우 강제로 마지막에 붙여준다.
	 */
	public void load() {
		NoLogUrlData newData = new NoLogUrlData();
		final FastWildcardMatch urls = new FastWildcardMatch(false);
		List<NoLogUrlData.NoLogUrlInfo> list = dbCommon.getInfoToCollection("INFO_NOLOG_URL", NoLogUrlData.NoLogUrlInfo.class);
		for (NoLogUrlData.NoLogUrlInfo info : list) {
			if (CommonUtil.isNotEmpty(info.url())) {
				String url = info.url();
				if (!info.url().contains("*")) url = info.url() + "*";

				urls.addPattern(url);

				log.debug("[NOLOG_URL] {}", url);
			}
		}
		log.info("[INFO_LOAD] NOLOG_URL Size: {}", list.size());
		newData.setData(urls);
		nologUrlData.set(newData);
	}

	public boolean check(String url) {
		NoLogUrlData data = nologUrlData.get();
		if (data != null) {
			final FastWildcardMatch ids = data.getData();
			return ids.isMatch(url);
		}
		return false;
	}

	public static void main(String[] args) {
		final FastWildcardMatch urls = new FastWildcardMatch(false);
		urls.addPattern("1.225.49.114:5500/css/xcn-page.min.cs*");
		System.out.println(urls.isMatch("1.225.49.114:5500/css/xcn-page.min.css"));
	}
}
