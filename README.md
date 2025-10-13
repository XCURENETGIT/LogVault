# LogVault

> **LogVault** 는 파일/메시지 인입 디렉터리를 지속 스캔하여 **메타데이터를 파싱 → 분석 → OpenSearch에 색인**하고, 본문/첨부 등 파일을 **로컬/MinIO**에 보관하는 **Spring Boot 기반 파이프라인**입니다.

---

## ✨ 주요 기능

- 📂 **디렉터리 스캐닝**: `DefaultConfig`의 설정에 따라 WMAIL 등 서비스별 인입 디렉터리를 주기적으로 스캔합니다.
- 🧩 **파싱(Parsing)**: `MSGParser`로 메시지 메타데이터(`.hdr`, 본문/첨부 경로 등)를 파싱합니다.
- 🔍 **분석(Analysis)**: 
  - GeoIP2로 **원격지/목적지 국가·좌표** 분석 (`resources/geo/GeoLite2-*`)
  - Lingua로 **본문 언어 감지**
  - UA Parser로 **User-Agent → OS/브라우저/디바이스** 추출
  - 첨부 파일에 대해 **암호화/압축/텍스트 추출** 및 **예상 확장자 추정**
- 🗃️ **저장(Storage)**: 
  - 본문/첨부 파일 → **로컬 파일시스템/MinIO** (선택)
  - 메타데이터 → **OpenSearch** 인덱스(`emass-*`)로 색인
- 🛠️ **유틸/부가 기능**:
  - **근무일/휴일/사용자 인사정보/예외 URL** 로더 제공 (`/actuator/insa/reload`)
  - **Actuator** 및 **metrics** 노출
  - 색인 템플릿/정책(**`emass-template.json`, `emass_policy.json`**) 자동 초기화

---

## 🏗️ 아키텍처 개요

```
[Scanner] -> [Worker(MSGWorker)] -> [Parser] -> [Analysis] -> [Index(OpenSearch)]
    |               |                   |            |
    +--> [FileSystem(Local/MinIO) Write]            +--> [Log/Clear]
```

- 스캐너가 인입 디렉터리를 우선순위 큐로 적재 → `MSGWorker`가 파일 존재/대기(최대 `file.wait.time.sec`) 확인 → 파싱/분석 → OpenSearch 색인 → 로그/정리(Clear).

---

## 📂 디렉터리 구조 (요약)

```
LogVault/
├─ src/
│  └─ main/
│     ├─ java/
│     │  └─ com/
│     │     └─ xcurenet/
│     └─ resources/
│        ├─ META-INF/
│        │  ├─ additional-spring-configuration-metadata.json
│        │  └─ spring.factories
│        ├─ dtd/
│        │  ├─ mybatis-3-config.dtd
│        │  └─ mybatis-3-mapper.dtd
│        ├─ geo/
│        │  ├─ GeoLite2-ASN.mmdb
│        │  ├─ GeoLite2-City.mmdb
│        │  └─ GeoLite2-Country.mmdb
│        ├─ mapper/
│        │  └─ info.xml
│        ├─ opensearch/
│        │  ├─ emass-template.json
│        │  └─ emass_policy.json
│        ├─ schema/
│        │  └─ insert_data.sql
│        ├─ application.properties
│        └─ banner.txt
└─ pom.xml
```

---

## 🧰 기술 스택

- Java 17, Spring Boot 3.x, Maven (패키징: **war**, 실행형)
- Spring Data OpenSearch 1.8.2, Rest High Level Client
- MariaDB + MyBatis (3.0.5)
- MinIO SDK, OkHttp, Zstd JNI, Commons-* (IO/Compress/Email/Lang3), Jackson
- GeoIP2 (MaxMind), Lingua(언어 감지), UA-Parser
- Spring Actuator, Spring Cloud (Config refresh 지원)

---

## ⚙️ 설정(Configurations)

### 1) 핵심 애플리케이션 프로퍼티 (`src/main/resources/application.properties`)

- **서버 포트**: `14541`
- **데이터베이스**:
  - `spring.datasource.url=jdbc:mariadb://<host>:3306/EMASSAI?...`
  - `spring.datasource.username=ENC(...)` (Jasypt 이용 가능)
  - `spring.datasource.password=******` _(민감정보는 환경변수/Secret Manager로 외부화 권장)_
- **OpenSearch**
- `spring.opensearch.rest.uris` = `https://10.100.20.208:9200`
- `spring.opensearch.rest.username` = `admin`
- `spring.opensearch.rest.password` = `NewPassword1e3!`
- `logging.level.org.opensearch.client.RestClient` = `ERROR`

- **Actuator 노출**: `refresh, env, loggers, info, health, metrics`

> 💡 **보안 주의**: 저장소에 평문 비밀번호/키를 커밋하지 마세요. Jasypt 또는 환경변수/Secret Manager를 사용하세요.

### 2) 프로그램 내 기본값 (`DefaultConfig`)

- `file.system.type=local`
- `edc.attach.path=/data01/attach/`
- `decoder.split.dir=100`
- `scan.dir.enable.wmail=true`
- `scan.dir.wmail=/users/las/msg/info/wmail`
- `edc.body.snippet.size=2000`
- `edc.decompress.depth=3`
- `edc.extract.text.timeout=5`
- `filter.http.response.content.type=text/css,application/javascript,text/javascript,font/woff2`
- `edc.ramdisk.path=/dev/shm/edc`, `edc.ramdisk.limit=104857600`

### 3) 파일시스템 선택

- `file.system.type=local` 또는 `minio`
- **MinIO 사용 시 (Config 게터 기준)**: `minioUrl`, `minioBucket`, `minioAccessKey`, `minioSecretKey`, `minioConnectTimeout`, `minioWriteTimeout`, `minioReadTimeout` 값을 설정해야 합니다.

### 4) OpenSearch 템플릿/정책

- 템플릿: `emass-template.json` (`index_patterns: emass-*`, alias: `emass`)
- ISM 정책: `emass_policy.json` (예: **hot 30d → warm**, **warm 90d → cold** 로 전이)

---

## 🚀 빌드 & 실행

### 요구사항
- **JDK 17+**, **Maven 3.9+**
- OpenSearch 2.x (보안 설정 시 `spring.opensearch.rest.*` 구성)
- MariaDB 10.x 이상

### 빌드
```bash
mvn -U -T 1C clean package
```

### 실행 (로컬)
```bash
java -jar target/logvault-0.0.1.war
# 또는 프로파일/외부설정
java -Dspring.profiles.active=prod -Dserver.port=14541 -jar target/logvault-0.0.1.war
```

### 시스템 서비스(예시)
`/etc/systemd/system/logvault.service`:
```ini
[Unit]
Description=LogVault
After=network.target

[Service]
User=svc-logvault
ExecStart=/usr/bin/java -Xms512m -Xmx2048m -jar /opt/logvault/logvault-0.0.1.war
Restart=always
Environment=SPRING_PROFILES_ACTIVE=prod

[Install]
WantedBy=multi-user.target
```

---

## 📡 REST/Actuator 엔드포인트

- `GET` `/actuator/insa/reload`  _(src: src/main/java/com/xcurenet/logvault/loader/InfoLoaderController.java)_
- `ANY` `/actuator/`  _(src: src/main/java/com/xcurenet/logvault/loader/InfoLoaderController.java)_

> **Actuator** 기본 경로: `/actuator/*`  
> 예: `GET /actuator/health`, `GET /actuator/info`, `POST /actuator/refresh`  
> **사용자/휴일/예외URL 재적재**: `GET /actuator/insa/reload`

---

## 🗃️ 색인 모델 (OpenSearch)

주요 필드 (총 60개): `attach`, `attach_count`, `attach_name`, `attach_total_size`, `body`, `client`, `client_version`, `count`, `ctime`, `day`, `dept_code`, `dept_name`, `device`, `dst_asn`, `dst_country`, `dst_ip`, `dst_location`, `dst_port`, `exist`, `has_name`, `hash`, `http`, `id`, `ip`, `is_ceo`, `jikgub_code`, `jikgub_name`, `keyword_info`, `keywords`, `language`, `ltime`, `msgid`, `name`, `network`, `os`, `os_version`, `privacy_info`, `privacy_total`, `protocol`, `raw`, `service`, `size`, `src_asn`, `src_country`, `src_ip`, `src_location`, `src_port`, `svc`, `svc1`, `svc12`, `svc2`, `svc3`, `svc4`, `text`, `type`, `url`, `user`, `user_agent`, `week`, `work`

> 실제 매핑은 `src/main/resources/opensearch/emass-template.json` 를 참고하세요.

---

## 📦 입력/출력

### 입력
- **인입 디렉터리**: `scan.dir.wmail=/users/las/msg/info/wmail` (서비스별 확장 가능)
- 메시지 구성 예시: `*.hdr`(헤더/메타), `*.html`(본문), 첨부 원본/텍스트 추출 파일

### 출력
- **파일 보관**: `edc.attach.path` (Local) 또는 MinIO 버킷 경로
- **색인**: OpenSearch `emass-*` (alias: `emass`)

---

## 🔐 보안 가이드

- 모든 **비밀번호/액세스 키는 환경변수/Jasypt로 암호화**하여 주입
- Jasypt 키는 코드에 **하드코딩하지 말고** 환경변수로 전달
- OpenSearch/DB 연결은 TLS 및 **최소 권한 계정** 사용
- 로그에 PII/비밀정보가 남지 않도록 레벨 및 마스킹 정책 점검

---

## 🧪 로컬 개발 팁

- OpenSearch가 없다면 임시로 **테스트 인덱서 비활성화** 또는 **개발용 클러스터** 기동
- GeoLite2 DB는 리소스에 포함되어 있으나 정기 업데이트 필요
- `management.endpoints.web.exposure.include` 를 개발/운영에서 구분

---

## 🐛 트러블슈팅

- **파일 누락으로 인한 대기**: `file.wait.time.sec` 안에 본문/헤더/첨부가 도착하지 않으면 스킵 로그 발생
- **색인 실패**: OpenSearch 인증/권한/템플릿 초기화 여부를 점검
- **MinIO 오류**: 자격증명/버킷/권한 확인, 연결 타임아웃(`minio*Timeout`) 조정
- **DB 조회 실패**: 인사/부서/사용자 IP 매핑 테이블 스키마 확인 (`mapper/info.xml`)

---

## 📜 라이선스/데이터

- MaxMind **GeoLite2** 데이터베이스 포함 (업데이트 정책 준수)
- 본 저장소 내 샘플/설정 파일의 크리덴셜은 **실서비스에 사용 금지**

---

## 📎 부록

- Spring Boot 메인 클래스: `com.xcurenet.logvault.LogVaultApplication`
- Maven Artifact: `com.xcurenet:LogValut:0.0.1` _(오타 주의: artifactId가 **LogValut**)_
- 서버 배너: `src/main/resources/banner.txt`




