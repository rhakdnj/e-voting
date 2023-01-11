package gabia.jaime.voting.domain.agenda.web;

import gabia.jaime.voting.domain.agenda.dto.request.AgendaCreateRequest;
import gabia.jaime.voting.domain.agenda.service.AgendaAdminService;
import gabia.jaime.voting.global.security.MemberDetails;
import gabia.jaime.voting.global.util.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@RestController
public class AgendaAdminApiController {

    private final AgendaAdminService agendaAdminService;

    public AgendaAdminApiController(final AgendaAdminService agendaAdminService) {
        this.agendaAdminService = agendaAdminService;
    }

    @PostMapping("/api/v1/admin/agendas")
    public ResponseEntity<Void> create(@RequestBody AgendaCreateRequest agendaCreateRequest, Authentication authentication) {
        final MemberDetails memberDetails = ClassUtils.getSafeCastInstance(authentication.getPrincipal(), MemberDetails.class);

        log.info("{}----------------{}", agendaCreateRequest.getAgendaStatus(), agendaCreateRequest.getIssueType());
        final Long savedId = agendaAdminService.save(memberDetails, agendaCreateRequest);
        return ResponseEntity.created(URI.create("/api/v1/agendas/" + savedId))
                .build();
    }
}