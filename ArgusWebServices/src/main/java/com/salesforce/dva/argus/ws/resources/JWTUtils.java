package com.salesforce.dva.argus.ws.resources;

import java.io.FileInputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import scala.Int;

/**
 * JWT Utility methods.
 *
 * @author  Bhinav Sura (bsura@salesforce.com), Gaurav Kumar (gaurav.kumar@salesforce.com)
 */
public class JWTUtils {
	
	//~ Static fields/initializers *******************************************************************************************************************
	
	private static final Logger LOGGER = LoggerFactory.getLogger(JWTUtils.class);
	private static final String ISSUER = "argus-api";
	private static final long ACCESS_TOKEN_EXPIRATION_MILLIS = 1 * 60 * 60 * 1000L; //1 hour
	private static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 30 * 24 * 60 * 60 * 1000L; //30 days
	private static final String SECRET_FILE = System.getProperty("argus.jwt.secret.file", "secret.key");
	private static String SECRET = "someKey";
	static {
		FileInputStream fis;
		try {
			fis = new FileInputStream(SECRET_FILE);
			int c;
			StringBuilder content = new StringBuilder(); 
			while((c = fis.read()) != -1) {
				content.append((char) c);
			}
			
			SECRET = content.toString();
		} catch (IOException e) {
			LOGGER.warn("Failed to load secret key from the provided file. Will proceed with default key.");
		}
	}
	
	//~ Static methods *******************************************************************************************************************************

	public static String generateAccessToken(String username) {
		String accessToken = Jwts.builder().setIssuer(JWTUtils.ISSUER)
				.setIssuedAt(new Date(System.currentTimeMillis()))
    	        .setExpiration(new Date(System.currentTimeMillis() + JWTUtils.ACCESS_TOKEN_EXPIRATION_MILLIS))
    	        .setSubject(username)
    	        .claim("type", TokenType.ACCESS)
    	        .signWith(SignatureAlgorithm.HS256, JWTUtils.SECRET)
    	        .compact();
		
		return accessToken;
	}
	
	public static String generateRefreshToken(String username) {
		String refreshToken = Jwts.builder().setIssuer(JWTUtils.ISSUER)
				.setIssuedAt(new Date(System.currentTimeMillis()))
    	        .setExpiration(new Date(System.currentTimeMillis() + JWTUtils.REFRESH_TOKEN_EXPIRATION_MILLIS))
    	        .setSubject(username)
    	        .claim("type", TokenType.REFRESH)
    	        .signWith(SignatureAlgorithm.HS256, JWTUtils.SECRET)
    	        .compact();
		return refreshToken;
	}
	
	public static Tokens generateTokens(String username) {
		return new Tokens(generateAccessToken(username), generateRefreshToken(username));
	}
	
	public static String validateTokenAndGetSubj(String jwt, TokenType tokenType) throws ExpiredJwtException, UnsupportedJwtException, 
						MalformedJwtException, SignatureException, IllegalArgumentException {
		Jws<Claims> claims = Jwts.parser()
				.requireIssuer(JWTUtils.ISSUER)
				.setSigningKey(JWTUtils.SECRET)
				.parseClaimsJws(jwt);
		
		TokenType actual = TokenType.valueOf(claims.getBody().get("type", String.class));
		if(!tokenType.equals(actual)) {
			throw new IllegalArgumentException(MessageFormat.format("Expected Token type was <{0}> but actual token type was <{1}>", 
					tokenType, actual));
		}
		
    	return claims.getBody().getSubject();
	}

	/**
	 * Expiry time of a given token
	 * @param token JWT Token
	 * @return JWT Token Expiry Time
	 */
	public static int getTokenExpiry(String token) {
		String accessTokenPayload = token.substring(token.indexOf(".") + 1, token.lastIndexOf(".") );
		byte[] decoded = Base64.getMimeDecoder().decode(accessTokenPayload);
		String output = new String(decoded);
		Map<String,Object> myMap = new HashMap<String, Object>();
		int result = -1;

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			myMap = objectMapper.readValue(output, HashMap.class);
			if(myMap.get("exp") instanceof Integer) {
				result = (Integer) myMap.get("exp");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

    /**
     * Username from a given JWT Token
     * @param token JWT Token
     * @return  Username from JWT Token.
     */
	public static String getUsername(String token) {
		String accessTokenPayload = token.substring(token.indexOf(".") + 1, token.lastIndexOf(".") );
		byte[] decoded = Base64.getMimeDecoder().decode(accessTokenPayload);
		String output = new String(decoded);
		Map<String,Object> myMap = new HashMap<String, Object>();
		String result = "unknown";

		ObjectMapper objectMapper = new ObjectMapper();
		try {
			myMap = objectMapper.readValue(output, HashMap.class);
			if(myMap.get("sub") instanceof String) {
				result = (String) myMap.get("sub");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	static class Tokens {
		public String accessToken;
		public String refreshToken;
		
		public Tokens(String accessToken, String refreshToken) {
			this.accessToken = accessToken;
			this.refreshToken = refreshToken;
		}
	}
	
	public static enum TokenType {
		
		ACCESS(),
		REFRESH();
		
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */