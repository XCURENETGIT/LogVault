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

	@Field("USERID")
	private String userId;

	@Field("NAME")
	private String name;

	@Field("SABUN")
	private String sabun;

	@Field("DEPTCD")
	private String deptCd;

	@Field("DEPTNM")
	private String deptNm;

	@Field("JIKGUBCD")
	private String jikgubCd;

	@Field("JIKGUBNM")
	private String jikgubNm;

	@Field("CEO")
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
