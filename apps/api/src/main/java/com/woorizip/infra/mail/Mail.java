package com.woorizip.infra.mail;

/** 실제로 보낼 메일 한 통. */
public record Mail(String to, String subject, String body) {
}
