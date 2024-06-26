package com.ssafy.seas.member.repository;

import static com.ssafy.seas.member.entity.QMember.*;

import static com.ssafy.seas.quiz.entity.QSolvedQuiz.*;
import static com.ssafy.seas.ranking.entity.QTier.*;

import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.seas.common.constants.ErrorCode;
import com.ssafy.seas.member.dto.MemberDto;
import com.ssafy.seas.member.dto.QMemberDto_MyInfoResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
public class MemberRepositoryImpl implements MemberRepositoryCustom {

	private final JPAQueryFactory queryFactory;

	@Override

	public MemberDto.MyInfoResponse getMyInfoResponse(Integer memberId) {

		MemberDto.MyInfoResponse info = queryFactory.select(
				new QMemberDto_MyInfoResponse(member.nickname, member.point, tier.name))
			.from(member)
			.where(member.id.eq(memberId))
			.innerJoin(tier)
			.on(member.point.between(tier.minScore, tier.maxScore))
			.fetchOne();

		if (info == null) {
			log.error(ErrorCode.MEMBER_NOT_FOUND.getMessage());
			throw new EntityNotFoundException(ErrorCode.MEMBER_NOT_FOUND.getMessage());
		}

		NumberExpression<Double> failedCountSum = new CaseBuilder().when(solvedQuiz.failedCount.sum().isNull())
			.then(0.0)
			.otherwise(solvedQuiz.failedCount.sum().doubleValue());

		NumberExpression<Double> correctCountSum = new CaseBuilder().when(solvedQuiz.correctCount.sum().isNull())
			.then(0.0)
			.otherwise(solvedQuiz.correctCount.sum().doubleValue());

		NumberExpression<Double> tryCountSum = failedCountSum.add(correctCountSum);

		NumberExpression<Double> correctRate = new CaseBuilder().when(tryCountSum.eq(0.0))
			.then(0.0)
			.otherwise(correctCountSum.divide(tryCountSum).multiply(100));

		MemberDto.MyInfoResponse stat = queryFactory.select(
				new QMemberDto_MyInfoResponse(solvedQuiz.count().intValue(), correctRate))
			.from(solvedQuiz)
			.where(solvedQuiz.member.id.eq(memberId))
			.fetchOne();

		return MemberDto.MyInfoResponse.builder()
			.nickname(info.getNickname())
			.point(info.getPoint())
			.tier(info.getTier())
			.solvedCount(stat.getSolvedCount())
			.correctRate(stat.getCorrectRate())
			.build();
	}
}
