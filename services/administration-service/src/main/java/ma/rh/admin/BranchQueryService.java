package ma.rh.admin;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class BranchQueryService {
    private final BranchRepository repository;
    public BranchQueryService(BranchRepository repository) { this.repository = repository; }

    public List<Branch> list(Authentication authentication) {
        return isOwner(authentication) ? repository.findAll() : repository.findAssigned(authentication.getName());
    }

    public Branch get(UUID id, Authentication authentication) {
        var branches = list(authentication);
        var assigned = new HashSet<>(branches.stream().map(Branch::id).toList());
        if (!BranchAccess.canRead(isOwner(authentication), assigned, id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch access denied");
        }
        return branches.stream().filter(branch -> branch.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    private boolean isOwner(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
    }
}
