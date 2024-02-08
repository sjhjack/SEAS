package com.ssafy.seas.member.service;

import org.springframework.stereotype.Service;

import com.ssafy.seas.member.dto.MemberDto;
import com.ssafy.seas.member.entity.Member;
import com.ssafy.seas.member.mapper.MemberMapper;
import com.ssafy.seas.member.repository.MemberRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberService {
	private final MemberRepository memberRepository;
	private final MemberMapper memberMapper;

	@Transactional
	public String signup(MemberDto.Post memberDto) {
		memberRepository.save(memberMapper.MemberDtoToMember(memberDto));
		return "";
	}

	@Transactional
	public MemberDto.Response signin(MemberDto.Post memberDto) {
		Member member = memberRepository.findByMemberIdAndPassword(memberDto.getMemberId(), memberDto.getPassword());
		if(member == null) {
			return new MemberDto.Response();
		}
		return memberMapper.MemberToMemberDtoResponse(member);
	}
}