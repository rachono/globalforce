package us.globalforce.services;

public class AuthToken {
	final String accessToken;

	public AuthToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getHeader() {
		return "OAuth " + accessToken;
	}

}
