package com.xcurenet.common.thumbnail;

import com.xcurenet.common.utils.Common;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

@Log4j2
public class TextThumbnail {
	private static final String FONT_RESOURCE_PATH = "/font/NotoSansKR-Regular.ttf";
	private static final float FONT_SIZE = 8f;
	private static final float PADDING = 2f;

	/**
	 * 주어진 텍스트를 이미지로 변환 후 Base64(JPEG)로 반환
	 *
	 * @param text   입력 텍스트
	 * @param width  썸네일 가로
	 * @param height 썸네일 세로
	 */
	public String execute(final String text, final int width, final int height) {
		final String target = Common.getSummaryText(Common.nvl(text), 1000);

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();

		// 안전 초기화
		g.setComposite(AlphaComposite.SrcOver);
		g.setTransform(new AffineTransform());

		// 배경
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);

		// 렌더링 힌트
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// 리소스 폰트 로드
		Font baseFont = loadResourceFont();
		g.setFont(baseFont);
		g.setColor(Color.BLACK);

		// 텍스트 출력
		drawWrappedText(g, target, width, height);

		g.dispose();
		return Common.toBase64(toJpegBytes(img));
	}

	/**
	 * 리소스에 포함된 NotoSansKR-Regular.otf 폰트 로드
	 */
	private Font loadResourceFont() {
		try (InputStream in = new ClassPathResource(FONT_RESOURCE_PATH).getInputStream()) {
			Font f = Font.createFont(Font.TRUETYPE_FONT, in);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
			log.debug("[FONT] Loaded resource font: {}", FONT_RESOURCE_PATH);
			return f.deriveFont(Font.PLAIN, FONT_SIZE);
		} catch (Exception e) {
			log.error("[FONT] Failed to load resource font: {}", e.getMessage());
			return new Font("Dialog", Font.PLAIN, (int) FONT_SIZE);
		}
	}

	/**
	 * 줄바꿈 처리하며 텍스트 출력
	 */
	private void drawWrappedText(Graphics2D g, String text, int width, int height) {
		float drawWidth = Math.max(1, width - TextThumbnail.PADDING * 2);
		FontRenderContext frc = g.getFontRenderContext();
		float y = TextThumbnail.PADDING;
		String[] paragraphs = text.replace("\r\n", "\n").split("\n", -1);
		for (String p : paragraphs) {
			if (p.isEmpty()) {
				y += g.getFontMetrics().getHeight();
				if (y > height - TextThumbnail.PADDING) break;
				continue;
			}
			AttributedString as = new AttributedString(p);
			as.addAttribute(TextAttribute.FONT, g.getFont());
			AttributedCharacterIterator it = as.getIterator();
			LineBreakMeasurer lbm = new LineBreakMeasurer(it, frc);

			while (lbm.getPosition() < it.getEndIndex()) {
				TextLayout layout = lbm.nextLayout(drawWidth);
				if (layout == null) break;

				y += layout.getAscent();
				if (y > height - TextThumbnail.PADDING) return;

				layout.draw(g, TextThumbnail.PADDING, y);
				y += layout.getDescent() + layout.getLeading();
			}
		}
	}

	/**
	 * JPEG로 변환 후 byte[] 반환
	 */
	private byte[] toJpegBytes(BufferedImage img) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(img, "jpg", out);
			return out.toByteArray();
		} catch (IOException e) {
			log.warn("[THUMNAIL] ImageIO write failed: {}", e.getMessage());
			return null;
		}
	}

	// 테스트용 main
	public static void main(String[] args) {
		String text = """
				/data01/attach/20251016/13/54/20251016135437.GCFTMEDJWI7MWJNBA2R6MXTQ5EV2Y2EY/20251016135437-01e13165-a04f680a-54207-443-00-64560-DEBDA8FBC3951135ED28B45CFD0FAB8B-VI01.http-1.txt",
				            "text": ""\"13:50:47.405 | INFO  | [    MSGWorker-1] | stractLogVaultWorker. 172 | [MG_START] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | /users/las/msg/info/wmail/54/WMAIL20251016135045-01e13165-a04f680a-54201-443-00-64521-DEBDA8FBC39511 35ED28B45CFD0FAB8B-VI01.http-1.MSG | 0.032s
				13:50:47.808 | INFO  | [    MSGWorker-1] | .m.a.PrivacyAnalysis. 102 | [REG_DONE] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | B | NUMBER:15\s
				13:50:47.838 | INFO  | [    MSGWorker-1] | stractLogVaultWorker. 223 | [BDY_SEND] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | /data01/attach/20251016/13/50/20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG/20251016135045-01e1316 5-a04f680a-54201-443-00-64521-DEBDA8FBC3951135ED28B45CFD0FAB8B-VI01.http-1.txt (274 Byte) | 0.001s
				13:50:47.960 | INFO  | [    MSGWorker-1] |  c.x.l.m.w.MSGWorker.  77 | [MG_INDEX] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | 0.122s
				13:50:47.961 | INFO  | [    MSGWorker-1] | x.l.m.a.AlertService.  19 | [ALT_SEND] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | 0.000s
				13:50:47.961 | INFO  | [    MSGWorker-1] | x.l.m.c.ClearService.  38 | [DEL_FILE] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | 0.000s
				13:50:47.962 | INFO  | [    MSGWorker-1] | c.x.l.m.l.LogService.  37 | [MSG_DONE] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | ICLS | BODY:true (274 Byte) US | AT_CNT:0 | EXIST_CNT:0 (0 KB) | 구매관리팀 | 184 | 라수빈 부장 | 1.225.49.101:54201 > 160.79.104.10:443 | https://claude.ai/api/organizations/a9b8b59d-2c82-4c6c-94ea-282c5fe59353/chat_conversations/2e3e2597 -3742-4449-8f76-5cd724045ee2/completion | Windows Chrome | 0.588s
				13:53:08.467 | INFO  | [    MSGWorker-1] | stractLogVaultWorker. 172 | [MG_START] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | /users/las/msg/info/wmail/34/WMAIL20251016135257-01e13165-a04f680a-54200-443-00-64543-DEBDA8FBC39511 35ED28B45CFD0FAB8B-VI01.http-1.MSG | 0.001s
				13:53:08.481 | INFO  | [    MSGWorker-1] | .m.a.PrivacyAnalysis. 102 | [REG_DONE] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | B | NUMBER:34\s
				13:53:08.482 | INFO  | [    MSGWorker-1] | stractLogVaultWorker. 223 | [BDY_SEND] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | /data01/attach/20251016/13/52/20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG/20251016135257-01e1316 5-a04f680a-54200-443-00-64543-DEBDA8FBC3951135ED28B45CFD0FAB8B-VI01.http-1.txt (6.3 KB) | 0.000s
				13:53:08.492 | INFO  | [    MSGWorker-1] |  c.x.l.m.w.MSGWorker.  77 | [MG_INDEX] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | 0.010s
				13:53:08.493 | INFO  | [    MSGWorker-1] | x.l.m.a.AlertService.  19 | [ALT_SEND] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | 0.000s
				13:53:08.493 | INFO  | [    MSGWorker-1] | x.l.m.c.ClearService.  38 | [DEL_FILE] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | 0.000s
				13:53:08.493 | INFO  | [    MSGWorker-1] | c.x.l.m.l.LogService.  37 | [MSG_DONE] 20251016135257.WEOBOGDJJRR3ZAFKNFXQ6A3WIAYWONJG | ICLS | BODY:true (6.3 KB) KR | AT_CNT:0 | EXIST_CNT:0 (0 KB) | 구매관리팀 | 184 | 라수빈 부장 | 1.225.49.101:54200 > 160.79.104.10:443 | https://claude.ai/api/organizations/a9b8b59d-2c82-4c6c-94ea-282c5fe59353/chat_conversations/2e3e2597 -3742-4449-8f76-5cd724045ee2/completion | Windows Chrome | 0.027s
				""";

		TextThumbnail tn = new TextThumbnail();
		String out = tn.execute(text, 100, 100);
		log.info("{}", out);
	}
}
