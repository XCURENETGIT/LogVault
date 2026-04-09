//package com.xcurenet.logvault.privacy;
//
//import com.gliwka.hyperscan.wrapper.*;
//import com.gliwka.hyperscan.wrapper.Scanner;
//import com.xcurenet.common.regex.MatchResult;
//import com.xcurenet.common.utils.DateUtils;
//import com.xcurenet.logvault.module.util.PrivateType;
//import com.xcurenet.logvault.privacy.validator.PatternValidator;
//import com.xcurenet.logvault.privacy.validator.service.*;
//import jakarta.annotation.PostConstruct;
//import jakarta.annotation.PreDestroy;
//import lombok.extern.log4j.Log4j2;
//import org.apache.commons.io.IOUtils;
//import org.springframework.stereotype.Component;
//import org.springframework.util.StopWatch;
//
//import java.io.FileInputStream;
//import java.util.*;
//import java.util.concurrent.ConcurrentLinkedQueue;
//
//@Log4j2
//@Component
//public class PrivacyPattern {
//	private static final int MAX_MATCHES = 999;
//
//	private Database database;
//
//	private final ConcurrentLinkedQueue<Scanner> createdScanners = new ConcurrentLinkedQueue<>();
//
//	private ThreadLocal<Scanner> tlScanner;
//
//	private static final Map<Integer, PatternDefinition> PATTERN_DEFS = new LinkedHashMap<>();
//	private static int nextId = 0;
//
//	static {
//		registerPattern(PrivateType.SN.name(), "\\b[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[-!@#$%^*|/?~._\\s]*?[1-8][0-9]{6}\\b", new SNValidator()); // 주민등록번호 (내국인/외국인 포함)
//		registerPattern(PrivateType.DN.name(), "(((서울|부산|경기|강원|충북|충남|전북|전남|경북|경남|제주|대구|인천|광주|대전|울산)|[0-2][0-9])" + "([-!@#$%^*/?~._\\s]|())" + "(([06789][0-9])|[0-2][0-9])" + "[-!@#$%^*/?~._\\s](\\d{6}[-!@#$%^*/?~._\\s]\\d{2}))\\b", new DNValidator()); // 운전면허번호
//		registerPattern(PrivateType.PN.name(), "\\b([MSRGD][0-9]{8}|[MSRGD]{2}[0-9]{7}|[MSRGD][A-Za-z][0-9]{7}|" + "[MSRGD][0-9]{1}[A-Za-z][0-9]{6}|" + "[MSRGD][0-9]{2}[A-Za-z][0-9]{5}|" + "[MSRGD][0-9]{3}[A-Za-z][0-9]{4}|" + "[MSRGD][0-9]{4}[A-Za-z][0-9]{3}|" + "[MSRGD][0-9]{5}[A-Za-z][0-9]{2}|" + "[MSRGD][0-9]{6}[A-Za-z][0-9]{1}|" + "[MSRGD][0-9]{7}[A-Za-z])\\b", new PNValidator()); // 여권번호
//		registerPattern(PrivateType.CN.name(), "\\b[34569][0-9]{3}[-!@#$%^*|/?~._ ][0-9]{4}[-!@#$%^*|/?~._ ][0-9]{4}[-!@#$%^*|/?~._ ][0-9]{4}\\b", new CNValidator()); // 신용카드번호
//		registerPattern(PrivateType.MN.name(), "\\b(?:(010(-?|\\s*)\\d{4})|(01[1|6|7|8|9](-?|\\s*)\\d{3,4}))(-?|\\s*)(\\d{4})\\b", new MNValidator()); // 핸드폰 번호
//		registerPattern(PrivateType.BA.name(), "\\b[0-9]{3,4}[\\s-][0-9]{2,6}[\\s-][0-9]{2,6}(?:[\\s-][0-9]{1,3})?\\b", new BAValidator()); // 계좌번호
//		registerPattern(PrivateType.SSN.name(), "\\b\\d{3}[-!@#$%^*|/?~._\\s]\\d{2}[-!@#$%^*|/?~._\\s]\\d{4}\\b", new SSNValidator()); // 미국 사회보험번호
//		registerPattern(PrivateType.EML.name(), "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+(?:\\.[A-Za-z]{2,}|\\.(?:co\\.kr|or\\.kr|go\\.kr|ac\\.kr|ne\\.kr))\\b", new EmailValidator());
//		//registerPattern(PrivateType.FN.name(), "\\b[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[-!@#$%^*|/?~._\\s]*?[5-8][0-9]{6}\\b", new SNValidator()); // 외국인등록번호
//		//registerPattern(PrivateType.CRN.name(), "((서울|부산|경기|강원|충북|충남|전북|전남|경북|경남|제주|대구|인천|광주|대전|울산)|())" + "([-!@#$%^*/?~._\\s]|())" + "\\[가-힣]{2,3}([-!@#$%^*/?~._\\s]|())[가-힣]+([-!@#$%^*/?~._\\s]|())\\d{4}\\b", new CRNValidator()); // 자동차 등록번호
//		//registerPattern(PrivateType.AN.name(), "(?:[가-힣A-Za-z·\\d~\\-.]+(구|군|시)\\s+)+[가-힣A-Za-z·\\d~\\-.]*?(로|길)\\s*\\d{1,5}(?:-\\d{1,3})?(?:번지)?(?:\\s|\\(|\\)|,|$)" + "|" + "(?:[가-힣A-Za-z·\\d~\\-.]+(구|군|시)\\s+)+[가-힣A-Za-z·\\d~\\-.]+(동|읍|면|리)\\s*[가-힣A-Za-z·\\d~\\-.]*\\s*\\d{1,5}(?:번지)?(?:\\s|\\(|\\)|,|$)", new ANValidator()); // 주소
//		//registerPattern(PrivateType.IMEI.name(), "\\b\\d{15}\\b", new IMEIValidator()); // 휴대폰 단말기 식별번호
//		//registerPattern(PrivateType.BRN.name(), "\\b([0-9]{3}-[0-9]{2}-[0-9]{5})\\b", new BRNValidator()); // 사업자등록번호
//		//registerPattern(PrivateType.CPN.name(), "\\b([0-9]{6})\\-([0-9]{7})\\b", new CPNValidator()); // 법인번호
//		//registerPattern(PrivateType.MCN.name(), "\\b([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b|\\b([0-9A-Fa-f]{4}\\.){2}[0-9A-Fa-f]{4}\\b", new MNCValidator()); // MAC 주소
//	}
//
//	private static void registerPattern(String type, String regex, PatternValidator validator) {
//		Expression expression = new Expression(regex, EnumSet.of(ExpressionFlag.SOM_LEFTMOST, ExpressionFlag.CASELESS), nextId);
//		PATTERN_DEFS.put(nextId, new PatternDefinition(type, expression, Optional.ofNullable(validator)));
//		nextId++;
//	}
//
//	@PostConstruct
//	private void compile() throws CompileErrorException {
//		List<Expression> expressions = PATTERN_DEFS.values().stream().map(PatternDefinition::expression).toList();
//		database = Database.compile(expressions);
//		tlScanner = ThreadLocal.withInitial(() -> {
//			Scanner sc = new Scanner();
//			sc.allocScratch(database);
//			createdScanners.add(sc);
//			return sc;
//		});
//		PATTERN_DEFS.forEach((id, def) -> log.info("COMPILE | {} | {} | {}", def.type(), def.validator().toString(), def.expression()));
//	}
//
//	@PreDestroy
//	private void destroy() {
//		while (!createdScanners.isEmpty()) {
//			IOUtils.closeQuietly(createdScanners.poll());
//		}
//		IOUtils.closeQuietly(database);
//	}
//
//	public Map<String, List<MatchResult>> scan(String text) {
//		final Map<String, List<MatchResult>> result = new HashMap<>();
//		if (text == null || text.isEmpty()) {
//			return result;
//		}
//
//		Scanner scanner = tlScanner.get();
//		final Set<String> seenMatches = new HashSet<>();
//
//		// startPosition 기준 longest-only
//		List<Match> matches = scanner.scan(database, text);
//		Map<Long, Match> longestMatchByStart = new HashMap<>();
//		for (Match match : matches) {
//			long startPos = match.getStartPosition();
//			Match prev = longestMatchByStart.get(startPos);
//			if (prev == null || match.getEndPosition() > prev.getEndPosition()) {
//				longestMatchByStart.put(startPos, match);
//			}
//		}
//
//		// 정제된 결과만 처리
//		for (Match match : longestMatchByStart.values()) {
//			log.debug("match : {} {}", match.getMatchedExpression().getId(), match.getMatchedString());
//			PatternDefinition def = PATTERN_DEFS.get(match.getMatchedExpression().getId());
//			if (def == null) continue;
//
//			String matchedValue = match.getMatchedString().replaceAll("\n", "");
//			if (!seenMatches.add(matchedValue)) continue;
//			int start = (int) Math.max(0, match.getStartPosition() - 50);
//			int end = (int) Math.min(text.length(), match.getEndPosition() + 50);
//			String context = text.substring(start, end);
//
//			ValidationResult validation = def.validator().map(v -> v.validate(matchedValue, context)).orElse(ValidationResult.ok());
//			if (!validation.valid()) {
//				log.debug("{} > {}", def.type(), validation.message());
//				continue;
//			}
//
//			List<MatchResult> list = result.computeIfAbsent(def.type(), k -> new ArrayList<>());
//			if (list.size() < MAX_MATCHES) {
//				list.add(new MatchResult(match.getStartPosition(), match.getEndPosition(), matchedValue));
//			}
//		}
//		log.debug("Scan Result: {}", result);
//		return result;
//	}
//
//	private record PatternDefinition(String type, Expression expression, Optional<PatternValidator> validator) {
//	}
//}
