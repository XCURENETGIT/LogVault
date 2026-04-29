# MSG 처리 모듈 기능 목록

| No | 기능 분류 | 기능 이름 | 기능 설명 | 입력 | 출력/결과 | 관련 코드 |
|---:|---|---|---|---|---|---|
| 1 | 파일 스캔 | 수집 디렉터리 탐색 | 설정된 수집 디렉터리를 주기적으로 탐색하여 처리 대상 파일을 찾는다. 최대 탐색 깊이는 2로 제한된다. | 수집 디렉터리 경로 | 후보 파일 목록 | `FileScanner` |
| 2 | 파일 스캔 | MSG 확장자 선별 | 파일명 확장자가 `.msg`인 파일만 처리 대상으로 선택한다. 대소문자는 구분하지 않는다. | 디렉터리 내 파일 | `.msg` 후보 파일 | `FileScanner.scan` |
| 3 | 파일 스캔 | 파일 기본 상태 검증 | 정규 파일 여부, 파일 크기, 숨김 파일 여부, 파일 권한을 확인한다. 조건을 만족하지 않으면 처리하지 않는다. | 파일 경로 | 처리 가능 여부 | `FileScanner.isValidCandidate` |
| 4 | 파일 스캔 | 중복 처리 방지 | 이미 큐에 들어갔거나 워커가 처리 중인 파일을 다시 큐에 넣지 않도록 관리한다. | 파일 절대 경로 | 중복 처리 방지 | `FileScanner.PROCESSING_SET` |
| 5 | 파일명 검증 | WMAIL 파일명 규칙 검증 | 파일명이 `WMAILyyyyMMddHHmmss-srcIpHex-dstIpHex-srcPort-dstPort-...` 구조를 따르는지 검사한다. | `.MSG` 파일명 | 정상/오류 판단 | `FileScanner.validateDetail` |
| 6 | 파일명 검증 | 시간 파트 검증 | 파일명 첫 번째 파트가 `WMAIL`과 14자리 숫자 시간값으로 구성되어 있는지 확인한다. | 파일명 | 시간 파트 정상 여부 | `FileScanner.validateDetail` |
| 7 | 파일명 검증 | IP Hex 검증 | 파일명 내 source IP와 destination IP가 hex 문자열인지 확인한다. | 파일명 IP 파트 | IP 형식 정상 여부 | `FileScanner.validateDetail` |
| 8 | 파일명 검증 | 포트 범위 검증 | 파일명 내 source port와 destination port가 숫자이며 `0`부터 `65535` 범위인지 확인한다. | 파일명 포트 파트 | 포트 정상 여부 | `FileScanner.validateDetail` |
| 9 | 파일명 검증 | 시퀀스 검증 | 파일명 내 sequence 값이 숫자 형식인지 확인한다. | 파일명 sequence 파트 | sequence 정상 여부 | `FileScanner.validateDetail` |
| 10 | 파일명 파싱 | 파일명 메타정보 추출 | 파일명에서 prefix, ctime, source IP, destination IP, source port, destination port, seq, cid, deviceName, decodeHost, suffix를 추출한다. | `.MSG` 파일명 | `FileNameInfo` | `FileNameInfo.getInfo` |
| 11 | 참조 파일 확인 | 참조 파일 키 탐색 | `.MSG` 파일 내용에서 `HDRFILE`, `MSGFILE`, `APPFILE` 라인을 찾아 참조 파일명을 추출한다. | `.MSG` 텍스트 | 참조 파일명 | `FileScanner.isFileValid` |
| 12 | 참조 파일 확인 | 참조 파일 도착 대기 | 본문/헤더/첨부 참조 파일이 아직 없고 대기 시간 이내이면 이번 스캔에서 제외하고 다음 주기에 다시 확인한다. | 참조 파일명, 수정시각 | 대기 또는 처리 진행 | `FileScanner.isFileValid` |
| 13 | 큐 처리 | ScanData 생성 | 파일 경로, 파일명, 마지막 수정시각, 파일 크기, 파일명 메타정보를 포함한 작업 단위를 만든다. | `.MSG` 파일 경로 | `ScanData` | `ScanData` |
| 14 | 큐 처리 | 작업 큐 적재 | 검증을 통과한 `ScanData`를 워커가 소비할 큐에 넣는다. 큐 용량이 꽉 차면 대기한다. | `ScanData` | Worker queue 적재 | `FileScanner.addQueue` |
| 15 | 워커 공통 | 작업 폴링 | 워커가 큐에서 `ScanData`를 꺼내 처리한다. | Worker queue | 처리 대상 `ScanData` | `AbstractWorker.poll` |
| 16 | 워커 공통 | 파일 존재 확인 | 큐에서 꺼낸 `.MSG` 파일이 실제로 존재하는지 다시 확인한다. | `ScanData.filePath` | 처리 진행 또는 skip | `AbstractWorker.run` |
| 17 | MSG 읽기 | MSG 파일 안전 읽기 | `.MSG` 파일을 UTF-8로 읽고, 깨진 문자는 replacement 처리한다. | `.MSG` 파일 경로 | 전체 텍스트 | `Common.readFileSafe` |
| 18 | MSG 파싱 | 섹션 파싱 | `[WMAIL]` 같은 섹션 라인을 `SVCKIND` 값으로 저장한다. | `.MSG` 텍스트 라인 | `SVCKIND` | `MSGParser.parseInfoText` |
| 19 | MSG 파싱 | Key/Value 파싱 | `KEY : VALUE` 라인을 첫 번째 `:` 기준으로 key와 value로 분리한다. | `.MSG` 텍스트 라인 | key/value map | `MSGParser.parseInfoText` |
| 20 | MSG 파싱 | 배열 필드 파싱 | `PCFILE[0]`, `APPFILE[1]` 같은 배열 키를 리스트 값으로 변환한다. | `KEY[n] : VALUE` 라인 | `List` 값 | `MSGParser.parseInfoText` |
| 21 | MSG 파싱 | FieldKey 매핑 | `MSGData` 필드의 `@FieldKey` 어노테이션을 기준으로 key/value map을 객체 필드에 매핑한다. | key/value map | `MSGData` | `MSGParser.convertData` |
| 22 | MSG 파싱 | 타입 변환 | 문자열 값을 `DateTime`, `IP`, `EMail`, `AttachExtension`, `List` 등 필드 타입에 맞게 변환한다. | 문자열 값 | 타입 변환된 필드 값 | `MSGParser.parseValue` |
| 23 | MSG 검증 | 필수 필드 검증 | `CTIME`, `SOURCEIP`, `SOURCEPORT`, `DESTINATIONIP`, `HOST`, `URL`, `STYPE` 필수 여부를 검사한다. | `MSGData` | 정상 또는 파싱 예외 | `MSGParser.checkField` |
| 24 | MSG 검증 | Query 길이 검증 | `URL_PARAMS` 값이 존재하고 길이가 10000자를 초과하면 오류로 처리한다. | `MSGData.query` | 정상 또는 파싱 예외 | `MSGParser.checkField` |
| 25 | MSG 보정 | MSG ID 생성 | `CTIME`과 `.MSG` 파일 경로를 기반으로 고유 `msgid`를 생성한다. | `CTIME`, 파일 경로 | `MSGData.msgid` | `MSGParser.parse` |
| 26 | MSG 보정 | 기본 Action 설정 | `ACTION` 필드가 없으면 기본값을 `ALLOW`로 설정한다. | `MSGData.action` | `ALLOW` 기본값 | `MSGParser.parse` |
| 27 | MSG 보정 | 차단 서비스 타입 보정 | `ACTION`이 `ALLOW`가 아니고 `STYPE`이 3자리이면 마지막에 `S`를 붙여 송신 서비스로 보정한다. | `ACTION`, `STYPE` | 보정된 `STYPE` | `MSGParser.parse` |
| 28 | 참조 파일 확인 | 본문 파일 존재 확인 | `MSGFILE`이 있으면 실제 본문 파일 경로를 계산하고 존재 여부를 확인한다. | `MSGData.msgFile` | 존재 확인 또는 대기 | `AbstractWorker.checkAttachments` |
| 29 | 참조 파일 확인 | 첨부 파일 존재 확인 | `APPFILE[n]` 목록의 실제 첨부 파일 경로를 계산하고 존재 여부를 확인한다. | `MSGData.appFile` | 존재 확인 또는 대기 | `AbstractWorker.checkAttachments` |
| 30 | 문서 변환 | EmassDoc 생성 | 파싱된 `MSGData`를 OpenSearch 색인용 `EmassDoc`으로 변환한다. | `MSGData` | `EmassDoc` | `MSGWorker.parse` |
| 31 | 문서 변환 | Action 변환 | `MSGData.action` 문자열을 `ActionType` enum으로 변환한다. | `ACTION` | `EmassDoc.action` | `MSGWorker.parse` |
| 32 | 문서 변환 | 정책명 설정 | `RULE_SEQ`가 있으면 로드된 정책 목록에서 정책명을 찾아 문서에 설정한다. | `RULE_SEQ`, rule list | `ruleName` | `MSGWorker.parse` |
| 33 | 문서 변환 | 시간 필드 설정 | `CTIME`을 timestamp와 `yyyyMMddHHmmss` 문자열로 변환하고, 처리 시작 시간을 `ltime`으로 저장한다. | `CTIME`, 처리 시작 시간 | `timestamp`, `ctime`, `ltime` | `MSGWorker.parse` |
| 34 | 서비스 처리 | STYPE 길이 검증 | `STYPE`이 null이 아니고 정확히 4자리인지 검증한다. | `STYPE` | 정상 또는 파싱 예외 | `MSGWorker.parse` |
| 35 | 서비스 처리 | 서비스 코드 분해 | 4자리 `STYPE`을 `svc1`, `svc2`, `svc3`, `svc12`로 나누어 저장한다. | `STYPE` | `EmassDoc.Service` | `MSGWorker.setService` |
| 36 | 네트워크 처리 | 네트워크 정보 구성 | 파일명에서 추출한 source/destination IP와 port, 프로토콜을 `EmassDoc.Network`에 저장한다. | `FileNameInfo`, `PROTOCOL` | `EmassDoc.Network` | `MSGWorker.setNetwork` |
| 37 | HTTP 처리 | HTTP URL 구성 | `HOST`, 파일명 destination port, `URL`, `URL_PARAMS`를 조합해 최종 URL을 만든다. | `HOST`, dst port, `URL`, `URL_PARAMS` | `EmassDoc.Http.url` | `MSGWorker.setHttp` |
| 38 | 본문 처리 | 본문 텍스트 추출 | `MSGFILE`이 가리키는 본문 파일을 읽어 텍스트를 추출한다. | 본문 파일 | 본문 텍스트 | `MSGWorker.setBody` |
| 39 | 본문 처리 | 본문 길이 제한 | 설정된 글자 길이와 토큰 제한에 맞게 본문 텍스트를 줄인다. | 본문 텍스트 | 제한된 텍스트 | `MSGWorker.setBody` |
| 40 | 본문 처리 | Body 문서 설정 | 본문 확장자, 저장 대상 경로, 파일 크기, 텍스트를 `EmassDoc.Body`에 저장한다. | 본문 파일, 텍스트 | `EmassDoc.Body` | `MSGWorker.setBody` |
| 41 | 첨부 처리 | 첨부 목록 구성 | `PCFILE[n]`, `APPFILE[n]`, `EXTENSION[n]` 값을 인덱스 기준으로 묶어 첨부 목록을 만든다. | 첨부 필드 목록 | `EmassDoc.Attach` 목록 | `MSGWorker.setAttach` |
| 42 | 첨부 처리 | 첨부 표시 이름 설정 | `PCFILE[n]`을 우선 표시 이름으로 사용하고 없으면 `APPFILE[n]`을 사용한다. | `PCFILE[n]`, `APPFILE[n]` | 첨부 이름 | `MSGWorker.setAttach` |
| 43 | 첨부 처리 | 첨부 이름 정리 | 첨부 이름에 `{`, `}`, `:`, `"` 문자가 있으면 제거한다. | 첨부 이름 | 정리된 첨부 이름 | `MSGWorker.setAttach` |
| 44 | 첨부 처리 | 첨부 실제 경로 설정 | `APPFILE[n]` 값을 설정 기반 실제 경로로 변환하고 절대 경로를 저장한다. | `APPFILE[n]` | `srcPath` | `MSGWorker.setAttach` |
| 45 | 첨부 처리 | 첨부 존재 여부 확인 | 실제 첨부 파일이 존재하는지 확인한다. | 첨부 실제 경로 | `exist` | `MSGWorker.setAttach` |
| 46 | 첨부 처리 | 첨부 크기 계산 | 첨부 파일이 존재하면 파일 크기를 계산한다. | 첨부 파일 | `size` | `MSGWorker.setAttach` |
| 47 | 첨부 처리 | 첨부 Hash 계산 | 첨부 파일이 존재하면 SHA-256 hash를 계산한다. | 첨부 파일 | `hash` | `MSGWorker.setAttach` |
| 48 | 첨부 처리 | 첨부 저장 경로 계산 | 첨부 파일이 전송될 목적지 경로를 계산한다. | `CTIME`, `msgid`, 첨부 파일명 | 저장 대상 경로 | `MSGWorker.setAttach` |
| 49 | 첨부 처리 | 첨부 확장자 설정 | 첨부 표시 이름에서 확장자를 추출해 소문자로 저장한다. | 첨부 이름 | 확장자 | `MSGWorker.setAttach` |
| 50 | 첨부 처리 | 첨부 ID 설정 | `APPFILE[n]`을 ID로 사용하고, 없으면 원본 파일명 MD5 hex 값을 ID로 사용한다. | `APPFILE[n]`, `PCFILE[n]` | 첨부 ID | `MSGWorker.setAttach` |
| 51 | 첨부 처리 | 첨부 통계 설정 | 첨부 개수, 실제 존재 개수, 첨부 총 크기를 계산해 문서에 저장한다. | 첨부 목록 | count, existCount, totalSize | `MSGWorker.setAttach` |
| 52 | 크기 계산 | 이벤트 전체 크기 계산 | 본문 크기와 첨부 총 크기를 합산해 이벤트 전체 크기를 계산한다. | body size, attach total size | `EmassDoc.size` | `MSGWorker.setSize` |
| 53 | 후처리 상태 | OCR/ML 기본 상태 설정 | 기본 OCR 상태와 ML 처리 후보 여부를 `processStatus`에 저장한다. | action, 설정, 서비스 코드 | `ProcessStatus` | `MSGWorker.parse` |
| 54 | 정렬 보정 | 수신 서비스 timestamp 보정 | 서비스 방향이 수신(`R`)이면 정렬을 위해 timestamp에 1ms를 추가한다. | service.svc3 | 보정된 timestamp | `MSGWorker.parse` |
| 55 | 필터링 | 이벤트 필터링 | 생성된 문서를 기준으로 필터 조건을 적용한다. 필터링되면 분석/색인 흐름을 타지 않는다. | `ScanData`, `EmassDoc` | 필터링 여부 | `FilterService.filter` |
| 56 | 분석 | 이벤트 분석 | 필터링되지 않은 이벤트에 대해 키워드, 개인정보, 첨부, 언어, GuardRail, 이상점수 등 분석을 수행한다. | `ScanData`, `EmassDoc` | 분석 결과 반영 | `AnalysisService.analyse` |
| 57 | 인사 매핑 | 사용자 정보 매핑 | source IP 또는 설정된 식별 모드를 기준으로 사용자/부서/직급 정보를 찾아 문서에 설정한다. | `ScanData`, source IP | `EmassDoc.User` | `MSGWorker.insaMapping` |
| 58 | 인사 매핑 | Proxy Port 설정 | 사용자 식별 모드가 `PORT`이고 파일명의 deviceName이 숫자이면 proxy port로 설정한다. | 설정, `FileNameInfo.deviceName` | `user.proxyPort` | `MSGWorker.insaMapping` |
| 59 | Room ID | 생성형 AI Room ID 생성 | 서비스 코드 첫 글자가 `I`이면 `svc12`와 사용자 ID 또는 source IP를 조합해 Base64 room ID를 만든다. | service, user ID, source IP | `roomId` | `MSGWorker.roomId` |
| 60 | 파일 전송 | INFO 파일 전송 | 원본 `.MSG` 메타파일을 목적지 저장소로 전송한다. | `.MSG` 파일 | 저장소 파일 | `AbstractWorker.transToInfo` |
| 61 | 파일 전송 | 본문 파일 전송 | `MSGFILE` 본문 파일을 목적지 저장소로 전송한다. | 본문 파일 | 저장소 파일 | `AbstractWorker.transToBody` |
| 62 | 파일 전송 | 첨부 파일 전송 | `APPFILE[n]` 첨부 파일을 목적지 저장소로 전송한다. | 첨부 파일 | 저장소 파일 | `AbstractWorker.transToAttach` |
| 63 | 파일 전송 | 첨부 내부 객체 전송 | 첨부 분석 과정에서 추출된 내부 객체 파일을 목적지 저장소로 전송한다. | embedded file | 저장소 파일 | `AbstractWorker.transToAttachEmbedded` |
| 64 | 색인 | OpenSearch 색인 | 최종 `EmassDoc`을 OpenSearch에 색인한다. | `EmassDoc` | 색인 문서 | `MSGWorker.index` |
| 65 | 통계 | 처리 통계 생성 | 정상 처리된 이벤트를 기준으로 통계 데이터를 생성한다. | `ScanData` | 통계 반영 | `StatService.processEvent` |
| 66 | 후처리 | OCR/ML 파이프라인 전달 | 후처리 대상 이벤트를 OCR/ML 파이프라인으로 전달한다. | `ScanData` | 후처리 태스크 | `PipelineManager.send` |
| 67 | 알림 | 즉시 알림 전송 | 후처리 대상이 아닌 이벤트는 즉시 알림 서비스로 전달한다. | `ScanData` | 알림 발송 | `MSGWorker.alert` |
| 68 | 정리 | 원본 파일 정리 | 처리가 끝난 `.MSG`, 본문, 첨부 등 관련 원본 파일을 정리한다. | `ScanData` | 파일 정리 | `ClearService.clear` |
| 69 | 로그 | 처리 로그 기록 | 처리 결과와 주요 이벤트 정보를 로그로 남긴다. | `ScanData` | 로그 기록 | `LogService.log` |
| 70 | 오류 처리 | 파싱 오류 NOK 이동 | `.MSG` 읽기, 필드 검증, 타입 변환, 문서 변환 중 오류가 나면 관련 파일을 NOK 디렉터리로 이동한다. | 오류 발생 파일 | NOK 이동 | `AbstractWorker.run` |
| 71 | 오류 처리 | 전송/색인 실패 재시도 | 파일 전송, 색인, 인사 매핑 실패 시 최대 3회 재시도한다. | 실패 작업 | 재시도 또는 실패 처리 | `AbstractWorker.run` |
| 72 | 오류 처리 | 재시도 실패 NOK 이동 | 3회 재시도 후에도 실패하면 `.MSG` 파일과 관련 파일을 NOK 디렉터리로 이동한다. | 실패 파일 | NOK 이동 | `AbstractWorker.run` |
| 73 | 운영 관리 | 큐 처리 상태 해제 | 작업이 끝나면 성공/실패와 관계없이 중복 처리 방지 캐시에서 파일 경로를 제거한다. | 파일 절대 경로 | 재처리 가능 상태 | `FileScanner.removeFromQueue` |
| 74 | 운영 관리 | 처리량 카운터 증가 | 정상 처리된 이벤트에 대해 초/분 단위 처리량 카운터를 증가시킨다. | 처리 완료 이벤트 | 처리량 통계 | `AbstractWorker.run` |

