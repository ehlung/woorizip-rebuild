package com.woorizip.user.domain;

/** 가입 때 받는 동의 (user-flows A-04). 0단계는 모두 필수다(docs/plans/auth.md). */
public enum ConsentKind {

	TERMS_OF_SERVICE, PRIVACY_POLICY, AI_DATA_TRANSFER, AGE_14_OR_OLDER

}
