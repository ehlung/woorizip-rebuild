package com.woorizip.infra.mail;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailJobRepository extends JpaRepository<MailJob, Long> {

	/** 보낼 차례인 작업을 잠그고 꺼낸다. 다른 서버가 잠근 행은 건너뛴다. */
	@Query(value = """
			SELECT * FROM mail_jobs
			WHERE status = 'PENDING' AND next_attempt_at <= :now
			ORDER BY next_attempt_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<MailJob> lockDue(@Param("now") Instant now, @Param("limit") int limit);

	/** 커밋 직후 바로 보낼 때 그 작업만 잠근다. 스케줄러가 이미 잡았으면 비어 있다. */
	@Query(value = "SELECT * FROM mail_jobs WHERE id = :id AND status = 'PENDING' FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<MailJob> lockPending(@Param("id") long id);

	long countByRecipientAndKindInAndCreatedAtAfter(String recipient, Collection<MailKind> kinds, Instant after);

	List<MailJob> findByRecipientOrderByIdAsc(String recipient);

}
