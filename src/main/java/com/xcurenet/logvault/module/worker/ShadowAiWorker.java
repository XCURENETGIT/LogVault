package com.xcurenet.logvault.module.worker;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.xcurenet.common.types.IP;
import com.xcurenet.common.utils.Common;
import com.xcurenet.common.utils.DateUtils;
import com.xcurenet.logvault.conf.Config;
import com.xcurenet.logvault.exception.IndexerException;
import com.xcurenet.logvault.loader.AiServiceLoader;
import com.xcurenet.logvault.loader.type.AiServiceVO;
import com.xcurenet.logvault.loader.type.UserInfo;
import com.xcurenet.logvault.module.util.IdentificationMode;
import com.xcurenet.logvault.module.util.InsaManager;
import com.xcurenet.logvault.opensearch.EmassDoc;
import com.xcurenet.logvault.opensearch.IndexService;
import com.xcurenet.logvault.opensearch.ShadowAiDoc;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

@Log4j2
public class ShadowAiWorker implements Runnable {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DIR_DATE = DateTimeFormatter.ofPattern("yyyy_MM_dd");
    private static final DateTimeFormatter CTIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> CLAIMED_FILES = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean inprogress = new AtomicBoolean(false);
    private final AtomicBoolean run;
    private final Config conf;
    private final InsaManager insaManager;
    private final AiServiceLoader aiServiceLoader;
    private final IndexService indexService;

    public ShadowAiWorker(final ApplicationContext context, final AtomicBoolean run) {
        this.run = run;
        this.conf = context.getBean(Config.class);
        this.insaManager = context.getBean(InsaManager.class);
        this.aiServiceLoader = context.getBean(AiServiceLoader.class);
        this.indexService = context.getBean(IndexService.class);
    }

    @Override
    public void run() {
        MDC.put("worker", Thread.currentThread().getName());
        while (run.get()) {
            try {
                scanOnce();
            } catch (Exception e) {
                log.warn("SHADOW_AI_SCAN_FAIL | {}", e.getMessage(), e);
            } finally {
                Common.sleep(60 * 1000);
            }
        }
        MDC.remove("worker");
    }

    public boolean getProgress() {
        return inprogress.get();
    }

    private void scanOnce() throws Exception {
        Path base = Paths.get(conf.getDirShadowAi());
        Path parent = base.getParent();
        if (parent == null || Files.notExists(parent)) return;

        String dirPrefix = base.getFileName().toString() + "_";
        try (Stream<Path> dateDirs = Files.list(parent)) {
            dateDirs.filter(Files::isDirectory)
                    .filter(path -> isShadowDateDir(path, dirPrefix))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(this::scanDateDir);
        }
    }

    private boolean isShadowDateDir(Path path, String dirPrefix) {
        String dirName = path.getFileName().toString();
        if (!dirName.startsWith(dirPrefix)) return false;
        return isValidDateDir(dirName.substring(dirPrefix.length()));
    }

    private void scanDateDir(Path dateDir) {
        try (Stream<Path> paths = Files.walk(dateDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isTargetLogFile)
                    .filter(this::isReadyLogFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(this::claimAndProcess);
        } catch (Exception e) {
            log.warn("SHADOW_AI_DIR_FAIL | {} | {}", dateDir, e.getMessage(), e);
        } finally {
            cleanupOldEmptyDirs(dateDir);
        }
    }

    private boolean isValidDateDir(String dateText) {
        try {
            LocalDate.parse(dateText, DIR_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTargetLogFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".log") || fileName.endsWith(".gz");
    }

    private boolean isGzipFile(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz");
    }

    private boolean isReadyLogFile(Path file) {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(2));
            return !Files.getLastModifiedTime(file).toInstant().isAfter(cutoff);
        } catch (Exception e) {
            log.warn("SHADOW_AI_FILE_TIME_FAIL | {} | {}", file, e.getMessage(), e);
            return false;
        }
    }

    private void cleanupOldEmptyDirs(Path dateDir) {
        LocalDate date = parseDateDir(dateDir);
        if (date == null) return;

        try (Stream<Path> paths = Files.walk(dateDir)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> !dateDir.equals(path))
                    .filter(path -> isOldDirectory(dateDir, date, path))
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(this::deleteEmptyDir);
        } catch (Exception e) {
            log.warn("SHADOW_AI_DIR_CLEAN_FAIL | {} | {}", dateDir, e.getMessage(), e);
        }

        if (date.isBefore(LocalDate.now(ZONE))) {
            deleteEmptyDir(dateDir);
        }
    }

    private LocalDate parseDateDir(Path dateDir) {
        Path base = Paths.get(conf.getDirShadowAi());
        String dirPrefix = base.getFileName().toString() + "_";
        String dirName = dateDir.getFileName().toString();
        if (!dirName.startsWith(dirPrefix)) return null;

        try {
            return LocalDate.parse(dirName.substring(dirPrefix.length()), DIR_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isOldDirectory(Path dateDir, LocalDate date, Path directory) {
        LocalDate today = LocalDate.now(ZONE);
        if (date.isBefore(today)) return true;
        if (date.isAfter(today)) return false;

        Path relative = dateDir.relativize(directory);
        if (relative.getNameCount() == 0) return false;

        String hourText = relative.getName(0).toString();
        if (!Common.isNumeric(hourText)) return false;

        int hour = Common.nvz(hourText);
        if (hour < 0 || hour > 23) return false;

        return hour < LocalDateTime.now(ZONE).getHour();
    }

    private void deleteEmptyDir(Path directory) {
        try (Stream<Path> children = Files.list(directory)) {
            if (children.findAny().isPresent()) return;
        } catch (Exception e) {
            log.warn("SHADOW_AI_DIR_CHECK_FAIL | {} | {}", directory, e.getMessage(), e);
            return;
        }

        try {
            Files.deleteIfExists(directory);
            log.info("SHADOW_AI_DIR_DELETE | {}", directory);
        } catch (Exception e) {
            log.warn("SHADOW_AI_DIR_DELETE_FAIL | {} | {}", directory, e.getMessage(), e);
        }
    }

    private void claimAndProcess(Path file) {
        String key = file.toAbsolutePath().normalize().toString();
        if (!CLAIMED_FILES.add(key)) return;

        try {
            inprogress.set(true);
            processFile(file);
        } finally {
            inprogress.set(false);
            CLAIMED_FILES.remove(key);
        }
    }

    private void processFile(Path file) {
        StopWatch sw = DateUtils.start();
        int total = 0;
        int indexed = 0;
        int skipped = 0;
        try {
            if (Files.size(file) == 0L) {
                deleteFile(file, total, indexed, skipped, sw);
                return;
            }

            try (BufferedReader reader = newLogReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    total++;
                    if (Common.isEmpty(line)) {
                        skipped++;
                        continue;
                    }

                    try {
                        ShadowAiDoc doc = toDoc(file, total, line);
                        if (doc == null) {
                            skipped++;
                            continue;
                        }
                        indexService.index(doc);
                        indexed++;
                    } catch (IndexerException e) {
                        throw e;
                    } catch (Exception e) {
                        skipped++;
                        log.warn("SHADOW_AI_LINE_SKIP | {}:{} | {}", file, total, e.getMessage());
                    }
                }
            }

            deleteFile(file, total, indexed, skipped, sw);
        } catch (Exception e) {
            log.warn("SHADOW_AI_FILE_FAIL | {} | total={} indexed={} skipped={} | {}", file, total, indexed, skipped, e.getMessage(), e);
        }
    }

    private BufferedReader newLogReader(Path file) throws Exception {
        if (isGzipFile(file)) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
    }

    private ShadowAiDoc toDoc(Path file, int lineNo, String line) throws Exception {
        JSONObject json = JSON.parseObject(line);
        if (json == null) return null;

        String timeIso8601 = json.getString("time_iso8601");
        String clientIp = json.getString("client_ip");
        String clientPort = json.getString("client_port");
        String host = json.getString("host");
        if (!"0".equals(json.getString("ssl_decrypted"))) return null;
        if (Common.isEmpty(timeIso8601) || Common.isEmpty(clientIp) || Common.isEmpty(host)) return null;

        OffsetDateTime time = OffsetDateTime.parse(timeIso8601);
        IP ip = IP.create(clientIp);
        String ctime = time.format(CTIME);

        ShadowAiDoc doc = new ShadowAiDoc();
        doc.setId(makeId(file, lineNo, ctime));
        doc.setTimestamp(Date.from(time.toInstant()));
        doc.setCtime(ctime);
        doc.setService(toService(host));
        doc.setUser(toUser(ip, clientPort));
        return doc;
    }

    private String makeId(Path file, int lineNo, String ctime) {
        return ctime + "." + Common.toHexString(Common.md5(file.toAbsolutePath().normalize() + ":" + lineNo));
    }

    private ShadowAiDoc.Service toService(String host) {
        ShadowAiDoc.Service service = new ShadowAiDoc.Service();
        service.setHost(host);

        AiServiceVO aiService = aiServiceLoader.get(host);
        if (aiService != null) {
            service.setCategoryGroupSeq(aiService.getCategoryGroupCd());
            service.setCategoryCd(aiService.getCategoryCd());
        }
        return service;
    }

    private EmassDoc.User toUser(IP ip, String clientPort) {
        EmassDoc.User user = new EmassDoc.User();
        user.setIp(ip.toCanonicalAddr());

        UserInfo info = null;
        IdentificationMode mode = conf.getUserIdentificationMode();
        if (mode == IdentificationMode.PORT && Common.isNumeric(clientPort)) {
            user.setProxyPort(Common.nvz(clientPort));
            info = insaManager.getUserInfoByPort(clientPort);
        } else if (mode == IdentificationMode.IP) {
            info = insaManager.getUserInfoByIp(ip);
        }

        if (info != null) {
            user.setId(info.getUserId());
            user.setName(info.getName());
            user.setCeo(Common.isEquals(info.getCeo(), "Y"));
            user.setDeptCode(info.getDeptCd());
            user.setDeptName(info.getDeptNm());
            user.setJikgubCode(info.getJikgubCd());
            user.setJikgubName(info.getJikgubNm());
        }
        return user;
    }

    private void deleteFile(Path file, int total, int indexed, int skipped, StopWatch sw) {
        try {
            Files.deleteIfExists(file);
            log.info("SHADOW_AI_DONE | {} | total={} indexed={} skipped={} | {}", file, total, indexed, skipped, DateUtils.stop(sw));
        } catch (Exception e) {
            log.warn("SHADOW_AI_DELETE_FAIL | {} | {}", file, e.getMessage(), e);
        }
    }

}
