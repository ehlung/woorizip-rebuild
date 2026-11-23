package com.woorizip.family.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.woorizip.TestcontainersConfiguration;
import com.woorizip.auth.domain.UserIdentity;
import com.woorizip.auth.domain.UserIdentityRepository;
import com.woorizip.user.domain.ScreenSize;
import com.woorizip.user.domain.User;
import com.woorizip.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** 가족 소속 규칙을 DB 제약이 지키는지 확인한다 (ADR 0010의 검증 항목). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MemberConstraintsTest {

	private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

	@Autowired
	UserRepository users;

	@Autowired
	UserIdentityRepository identities;

	@Autowired
	FamilyRepository families;

	@Autowired
	MemberRepository members;

	@Test
	void 한_사용자가_활성_상태로_두_가족에_속할_수_없다() {
		User user = users.save(new User("할머니", ScreenSize.LARGE));
		Family first = families.save(new Family("우리집", "ABCD2345"));
		Family second = families.save(new Family("큰집", "EFGH6789"));
		members.saveAndFlush(Member.owner(first, user, "할머니", NOW));

		assertThatThrownBy(() -> members.saveAndFlush(Member.member(second, user, "할머니", NOW)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("members_one_active_family_per_user");
	}

	@Test
	void 가족을_나간_기록이_있어도_새_가족에_들어갈_수_있다() {
		User user = users.save(new User("아빠", ScreenSize.MEDIUM));
		Family first = families.save(new Family("우리집", "ABCD2345"));
		Family second = families.save(new Family("새집", "EFGH6789"));
		Member left = members.saveAndFlush(Member.member(first, user, "아빠", NOW));
		left.leave(NOW.plusSeconds(60));
		members.saveAndFlush(left);

		Member joined = members.saveAndFlush(Member.owner(second, user, "아빠", NOW.plusSeconds(120)));

		assertThat(joined.getStatus()).isEqualTo(MemberStatus.ACTIVE);
		assertThat(members.count()).isEqualTo(2);
	}

	@Test
	void 한_가족에_활성_방장은_한_명이다() {
		User mom = users.save(new User("엄마", ScreenSize.MEDIUM));
		User dad = users.save(new User("아빠", ScreenSize.MEDIUM));
		Family family = families.save(new Family("우리집", "ABCD2345"));
		members.saveAndFlush(Member.owner(family, mom, "엄마", NOW));

		assertThatThrownBy(() -> members.saveAndFlush(Member.owner(family, dad, "아빠", NOW)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("members_one_active_owner_per_family");
	}

	@Test
	void 구성원_이름의_처음_값은_가입_때_받은_이름이다() {
		User user = users.save(new User("막내", ScreenSize.SMALL));
		Family family = families.save(new Family("우리집", "ABCD2345"));

		Member member = members.saveAndFlush(Member.owner(family, user, "딸", NOW));

		assertThat(member.getName()).isEqualTo("막내");
		assertThat(member.getRelation()).isEqualTo("딸");
	}

	@Test
	void 초대_코드에_헷갈리는_글자가_들어가면_거부한다() {
		assertThatThrownBy(() -> families.saveAndFlush(new Family("우리집", "ABCD0O1I")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("families_invite_code_check");
	}

	@Test
	void 대소문자만_다른_같은_이메일로_두_계정을_만들_수_없다() {
		User first = users.save(new User("첫째", ScreenSize.MEDIUM));
		User second = users.save(new User("둘째", ScreenSize.MEDIUM));
		identities.saveAndFlush(UserIdentity.email(first, "Family@Example.com", "hash"));

		assertThatThrownBy(() -> identities.saveAndFlush(UserIdentity.email(second, "family@example.com ", "hash")))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("user_identities_provider_subject_key");
	}

	@Test
	void 이메일_로그인에는_비밀번호_해시가_있어야_한다() {
		User user = users.save(new User("엄마", ScreenSize.MEDIUM));

		assertThatThrownBy(() -> identities.saveAndFlush(UserIdentity.email(user, "mom@example.com", null)))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("user_identities_check");
	}

}
