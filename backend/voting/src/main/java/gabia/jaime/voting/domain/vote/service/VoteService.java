package gabia.jaime.voting.domain.vote.service;

import gabia.jaime.voting.domain.vote.dto.request.VoteCreateRequest;
import gabia.jaime.voting.domain.issue.entity.Issue;
import gabia.jaime.voting.domain.issue.reposioty.IssueRepository;
import gabia.jaime.voting.domain.member.entity.Member;
import gabia.jaime.voting.domain.member.repository.MemberRepository;
import gabia.jaime.voting.domain.vote.dto.response.VoteCreateResponse;
import gabia.jaime.voting.domain.vote.entity.Vote;
import gabia.jaime.voting.domain.vote.repository.VoteRepository;
import gabia.jaime.voting.global.exception.forbidden.AdminCanNotVoteException;
import gabia.jaime.voting.global.exception.conflict.AlreadyVoteException;
import gabia.jaime.voting.global.exception.conflict.ClosedIssueException;
import gabia.jaime.voting.global.exception.notfound.IssueNotFoundException;
import gabia.jaime.voting.global.exception.notfound.MemberNotFoundException;
import gabia.jaime.voting.global.security.MemberDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static gabia.jaime.voting.domain.issue.entity.IssueStatus.CLOSE;
import static gabia.jaime.voting.domain.issue.entity.IssueType.LIMITED;
import static gabia.jaime.voting.domain.member.entity.Role.ROLE_SHAREHOLDER;

@Slf4j
@Service
@Transactional(readOnly = true)
public class VoteService {

    private final IssueRepository issueRepository;

    private final MemberRepository memberRepository;

    private final VoteRepository voteRepository;

    public VoteService(final IssueRepository issueRepository, final MemberRepository memberRepository, final VoteRepository voteRepository) {
        this.issueRepository = issueRepository;
        this.memberRepository = memberRepository;
        this.voteRepository = voteRepository;
    }

    @Transactional
    public VoteCreateResponse vote(final MemberDetails memberDetails, final VoteCreateRequest voteCreateRequest, final Long issueId) {
        // 비관적 락
        final Issue issue = findIssueWithAgendaSelectForUpdate(issueId);
        final Member member = findMember(memberDetails.getEmail());

        // OPEN 된 이슈와 동시에 주주만 투표할 수 있다.
        validateIssue(issue);

        // 종료 시간 이후에 투표를 진행하면 상태값을 변경한다.
        // TODO: 리팩 토링 ..
        // 이렇게 되면 트랜잭션은 반영이 되지만, 예외처리를 올바르게 하지 않은 것 같다.
        if (issue.getEndAt().isBefore(LocalDateTime.now())) {
            issue.close();
            return VoteCreateResponse.fail();
        }
        validateVoter(member);
        validateAlreadyVote(issue, member);

        // 선착순 투표
        if (issue.getIssueType() == LIMITED) {
            int availableCount = issue.getAvailableCount();
            int voteCount = Math.min(availableCount, member.getVoteRightCount());
            issue.addVoteCount(voteCreateRequest.getVoteType(), voteCount);

            if (canCloseIssue(issue)) {
                issue.close();
                return VoteCreateResponse.fail();
            }

            final Vote vote = Vote.of(voteCreateRequest.getVoteType(), voteCount, issue, member);
            Long savedId = voteRepository.save(vote).getId();
            log.info("투표 ID: {}, 사용자 ID: {}, 현안 ID: {}", savedId, member.getId(), issue.getId());
            return VoteCreateResponse.success(savedId);
        }

        // 제한이 없는 투표
        issue.addVoteCount(voteCreateRequest.getVoteType(), member.getVoteRightCount());
        Long savedId = voteRepository.save(
                Vote.of(voteCreateRequest.getVoteType(), member.getVoteRightCount(), issue, member)).getId();
        log.info("투표 ID: {}, 사용자 ID: {}, 현안 ID: {}", savedId, member.getId(), issue.getId());
        return VoteCreateResponse.success(savedId);
    }

    private void validateIssue(final Issue issue) {
        if (issue.getIssueStatus() == CLOSE) {
            throw new ClosedIssueException();
        }
    }

    private void validateVoter(final Member member) {
        if (member.getRole() != ROLE_SHAREHOLDER) {
            throw new AdminCanNotVoteException();
        }
    }

    private void validateAlreadyVote(final Issue issue, final Member member) {
        voteRepository.findByIssueAndMember(issue, member).ifPresent(it -> {
            throw new AlreadyVoteException();
        });
    }

    private boolean canCloseIssue(final Issue issue) {
        return issue.getTotalVoteCount() == Issue.VALID_VOTE_COUNT;
    }

    private Issue findIssueWithAgendaSelectForUpdate(final Long issueId) {
        return issueRepository.findWithAgendaByIdSelectForUpdate(issueId)
                .orElseThrow(IssueNotFoundException::new);
    }

    private Member findMember(final String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(MemberNotFoundException::new);
    }

}
