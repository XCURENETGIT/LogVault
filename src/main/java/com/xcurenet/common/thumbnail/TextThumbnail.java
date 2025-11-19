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

		byte[] jpeg = toJpegBytes(img);
		if (jpeg == null) {
			return null;
		}
		return Common.toBase64(jpeg);
	}

	/**
	 * 리소스에 포함된 NotoSansKR-Regular.ttf 폰트 로드
	 */
	private Font loadResourceFont() {
		try (InputStream in = new ClassPathResource(FONT_RESOURCE_PATH).getInputStream()) {
			Font f = Font.createFont(Font.TRUETYPE_FONT, in);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
			log.debug("FONT | Loaded resource font: {}", FONT_RESOURCE_PATH);
			return f.deriveFont(Font.PLAIN, FONT_SIZE);
		} catch (Exception e) {
			log.error("FONT | Failed to load resource font: {}", e.getMessage());
			return new Font("Dialog", Font.PLAIN, (int) FONT_SIZE);
		}
	}

	/**
	 * 줄바꿈 처리하며 텍스트 출력
	 */
	private void drawWrappedText(Graphics2D g, String text, int width, int height) {
		float drawWidth = Math.max(1, width - PADDING * 2);
		FontRenderContext frc = g.getFontRenderContext();
		float y = PADDING;

		String[] paragraphs = text.replace("\r\n", "\n").split("\n", -1);
		for (String p : paragraphs) {
			if (p.isEmpty()) {
				y += g.getFontMetrics().getHeight();
				if (y > height - PADDING) break;
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
				if (y > height - PADDING) return;

				layout.draw(g, PADDING, y);
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
			log.warn("THUMNAIL | ImageIO write failed: {}", e.getMessage());
			return null;
		}
	}

	// 테스트용 main
	public static void main(String[] args) {
		String text = """
				/data01/attach/20251016/13/54/20251016135437.GCFTMEDJWI7MWJNBA2R6MXTQ5EV2Y2EY/20251016135437-01e13165-a04f680a-54207-443-00-64560-DEBDA8FBC3951135ED28B45CFD0FAB8B-VI01.http-1.txt",
				            "text": ""\"13:50:47.405 | INFO  | [    MSGWorker-1] | stractLogVaultWorker. 172 | [MG_START] 20251016135045.IOOAR7FWCCIMBXD34YGGDFRQE7SLNPLG | /users/las/msg/info/wmail/54/WMAIL20251016135045-01e13165-a04f680a-54201-443-00-64521-DEBDA8FBC39511 35ED28B45CFD0FAB8B-VI01.http-1.MSG | 0.032s
				...
				""";

		TextThumbnail tn = new TextThumbnail();
		String out = tn.execute(text, 100, 100);
		log.info("{}", out);
	}
}
