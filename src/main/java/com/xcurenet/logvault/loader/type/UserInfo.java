package com.xcurenet.logvault.loader.type;

import com.xcurenet.common.types.IP;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.Alias;
import org.springframework.data.elasticsearch.annotations.Field;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@Alias("UserInfo")
@ToString
public class UserInfo implements Serializable {

	@Serial
	private static final long serialVersionUID = -2658349107028320614L;

	private String userId;

	private String name;

	private String deptCd;

	private String deptNm;

	private String jikgubCd;

	private String jikgubNm;

	private String ceo;

	private final List<String> emails = new ArrayList<>();

	private final List<IP> ips = new ArrayList<>();

	public void addEmail(final String email) {
		final String trimEmail = StringUtils.trimToNull(email);
		if (trimEmail != null) emails.add(trimEmail);
	}

	public String getEmail() {
		return emails.isEmpty() ? null : emails.get(0);
	}

	public void addIp(final IP ip) {
		ips.add(ip);
	}
}
