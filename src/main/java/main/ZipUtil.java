package main;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ZipUtil {
	/**
	 * 使用gzip进行压缩
	 */
	public static String gzip(String primStr) throws Exception {
		if (primStr == null || primStr.length() == 0) {
			return primStr;
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			GZIPOutputStream gzip = new GZIPOutputStream(out);
			gzip.write(primStr.getBytes());
			gzip.close();
			return Base64.getEncoder().encodeToString(out.toByteArray());
		}
	}

	/**
	 * 使用gzip进行解压缩
	 */
	public static String gunzip(String compressedStr) throws Exception {
		if (compressedStr == null) {
			return null;
		}
		byte[] compressed = Base64.getDecoder().decode(compressedStr);
		try (
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteArrayInputStream in = new ByteArrayInputStream(compressed);
			GZIPInputStream ginzip = new GZIPInputStream(in)
		) {
			byte[] buffer = new byte[1024];
			int offset;
			while ((offset = ginzip.read(buffer)) != -1) {
				out.write(buffer, 0, offset);
			}
			return out.toString();
		}
	}
}
