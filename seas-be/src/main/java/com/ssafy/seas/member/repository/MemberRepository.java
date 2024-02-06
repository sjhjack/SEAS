package com.ssafy.seas.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ssafy.seas.member.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Integer>, MemberRepositoryCustom {
	Member findByMemberIdAndPassword(String memberId, String Password);
}
