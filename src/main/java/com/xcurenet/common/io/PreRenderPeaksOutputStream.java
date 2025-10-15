package com.xcurenet.common.io;

import com.xcurenet.common.utils.CommonUtil;
import com.xcurenet.crypto.GrowBufferdOutputStream;
import lombok.Getter;

import java.io.IOException;
import java.io.OutputStream;

public class PreRenderPeaksOutputStream extends GrowBufferdOutputStream {
	private final int width;
	@Getter
	private PcmHeader header = null;

	private Peak[] peaks;
	private int peaksOffset = 0;
	private int sampleSize = 0;
	private int sampleOffset = 0;

	public PreRenderPeaksOutputStream(final int width) {
		this(null, width);
	}

	public PreRenderPeaksOutputStream(final OutputStream out, final int width) {
		super(out);
		if (width < 1) {
			throw new IllegalArgumentException("width must be greater than 0: " + width);
		}
		this.width = width;
	}

	@Override
	protected void flushBuffer() throws IOException {
		final int offset = render();
		if (offset > 0 && out != null) {
			out.write(buf, 0, offset);
		}
	}

	private int render() {
		int offset = 0;
		// 헤더 생성
		if (header == null) {
			if (count < PcmHeader.HEADER_SIZE) {
				// 아직 헤더 사이즈 만큼 데이터가 없을 경우
				return 0;
			}
			header = new PcmHeader(buf, 0);
			final int bytePerSample = header.bitPerSample / 8;
			sampleSize = (int) Math.ceil((double) header.chunkSize / bytePerSample / width);
			final int peaksLength = (int) Math.ceil((double) header.chunkSize / bytePerSample / sampleSize);
			peaks = new Peak[peaksLength];
			offset = PcmHeader.HEADER_SIZE;
		}

		// 샘플링
		for (; offset <= count - header.blockAlign; offset += header.blockAlign) {
			for (int i = 0; i < header.channels; i++) {
				final double value = ((buf[offset + 1] << 8) | buf[offset]) / 32767.0;
				final Peak peak = getPeak();
				if (value > peak.max) {
					peak.max = (float) value;
				}
				if (value < peak.min) {
					peak.min = value;
				}

				// 샘플링 사이즈 초과시 다음 피크로 이동
				sampleOffset++;
				if (sampleOffset >= sampleSize) {
					// 마지막 피크는 잔여 데이터를 모두 포함
					if (peaksOffset < peaks.length - 1) {
						peaksOffset++;
					}
					sampleOffset = 0;
				}
			}
		}

		final int remain = count - offset;
		if (remain > 0) {
			// 잔여 데이터 버퍼 앞으로 이동
			System.arraycopy(buf, count, buf, 0, remain);
		}
		count = remain;
		return offset;
	}

	private Peak getPeak() {
		if (peaks[peaksOffset] == null) {
			peaks[peaksOffset] = new Peak();
		}
		return peaks[peaksOffset];
	}

	public synchronized byte[] toByteArray() throws IOException {
		flushBuffer();
		final byte[] output = new byte[peaks.length * 8];
		for (int i = 0; i < peaks.length; i++) {
			final Peak peak = peaks[i];
			CommonUtil.putFloat(output, i * 8, (float) peak.max);
			CommonUtil.putFloat(output, i * 8 + 4, (float) peak.min);
		}
		return output;
	}

	public synchronized String toJsonString() throws IOException {
		flushBuffer();
		final StringBuilder sb = new StringBuilder().append("[");
		for (int i = 0; i < peaks.length; i++) {
			final Peak peak = peaks[i];
			if (i > 0) {
				sb.append(",");
			}
			sb.append(peak.getStringMax()).append(",");
			sb.append(peak.getStringMin());
		}
		return sb.append("]").toString();
	}

	@Override
	public String toString() {
		try {
			return toJsonString();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static class PcmHeader {
		public static final int HEADER_SIZE = 44;

		public final int channels;
		public final int sampleRate;
		public final int byteRate;
		public final int blockAlign;
		public final int bitPerSample;
		public final int chunkSize;

		public PcmHeader(final byte[] header, final int offset) {
			channels = CommonUtil.toIntFromLittle(header, offset + 22, 2);
			sampleRate = CommonUtil.toIntFromLittle(header, offset + 24, 4);
			byteRate = CommonUtil.toIntFromLittle(header, offset + 28, 4);
			blockAlign = CommonUtil.toIntFromLittle(header, offset + 32, 2);
			bitPerSample = CommonUtil.toIntFromLittle(header, offset + 34, 2);
			chunkSize = CommonUtil.toIntFromLittle(header, offset + 40, 4);
		}

		@Override
		public String toString() {
			return "channels:" + channels + "\n" +
					"sampleRate:" + sampleRate + "\n" +
					"byteRate:" + byteRate + "\n" +
					"blockAlign:" + blockAlign + "\n" +
					"bitPerSample:" + bitPerSample + "\n" +
					"chunkSize:" + chunkSize;
		}
	}

	private static class Peak {
		public double max;
		public double min;

		public String getStringMax() {
			return getString(max);
		}

		public String getStringMin() {
			return getString(min);
		}

		private String getString(final double value) {
			return value == 0 ? "0" : String.format("%.4f", value);
		}
	}
}
