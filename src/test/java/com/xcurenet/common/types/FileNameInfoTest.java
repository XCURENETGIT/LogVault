package com.xcurenet.common.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FileNameInfo - MSG 파일명 파싱")
class FileNameInfoTest {

    private static final String VALID = "WMAIL20251104151028-01e13165-d8ef2415-57793-443-00-462358-DEBDA8FBC3951135-VI01.http-2.hdr";

    @Nested
    @DisplayName("정상 파싱")
    class ValidParsing {

        @Test
        @DisplayName("prefix = WMAIL")
        void prefix() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals("WMAIL", info.getPrefix());
        }

        @Test
        @DisplayName("ctime 파싱")
        void ctime() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals("20251104151028", info.getCtime());
        }

        @Test
        @DisplayName("srcIP 파싱 (hex → IP)")
        void srcIp() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertNotNull(info.getSrcIP());
        }

        @Test
        @DisplayName("dstIP 파싱 (hex → IP)")
        void dstIp() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertNotNull(info.getDstIP());
        }

        @Test
        @DisplayName("srcPort = 57793")
        void srcPort() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals(57793, info.getSrcPort());
        }

        @Test
        @DisplayName("dstPort = 443")
        void dstPort() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals(443, info.getDstPort());
        }

        @Test
        @DisplayName("seq = 0")
        void seq() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals(0, info.getSeq());
        }

        @Test
        @DisplayName("cid = 462358")
        void cid() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals("462358", info.getCid());
        }

        @Test
        @DisplayName("deviceName 파싱")
        void deviceName() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals("DEBDA8FBC3951135", info.getDeviceName());
        }

        @Test
        @DisplayName("decodeHost 파싱")
        void decodeHost() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertEquals("VI01", info.getDecodeHost());
        }

        @Test
        @DisplayName("suffix에 확장자 포함")
        void suffix() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            assertTrue(info.getSuffix().contains(".hdr"));
        }
    }

    @Nested
    @DisplayName("getName() 재구성")
    class GetName {
        @Test
        @DisplayName("파싱 후 getName()이 원본과 동일한 구조")
        void reconstructedName_shouldMatchStructure() throws Exception {
            FileNameInfo info = FileNameInfo.getInfo(VALID);
            String name = info.getName();
            assertNotNull(name);
            assertTrue(name.startsWith("WMAIL"));
            assertTrue(name.contains("-"));
        }
    }

    @Nested
    @DisplayName("파트 수 변동 대응")
    class PartVariation {
        @Test
        @DisplayName("최소 파트 (6개) - seq까지만 있는 파일")
        void minimalParts() throws Exception {
            String minimal = "WMAIL20251104151028-01e13165-d8ef2415-443-80-10.msg";
            FileNameInfo info = FileNameInfo.getInfo(minimal);
            assertNotNull(info);
            assertEquals(443, info.getSrcPort());
            assertEquals(80, info.getDstPort());
            assertEquals(10, info.getSeq());
        }

        @Test
        @DisplayName("cid 포함 (7개 파트)")
        void withCid() throws Exception {
            String withCid = "WMAIL20251104151028-01e13165-d8ef2415-443-80-10-99999.msg";
            FileNameInfo info = FileNameInfo.getInfo(withCid);
            assertEquals("99999", info.getCid());
        }
    }
}
