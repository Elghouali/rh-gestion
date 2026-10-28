package ma.rh.admin;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchQueryService queries;
    public BranchController(BranchQueryService queries) { this.queries = queries; }
    @GetMapping
    public List<Branch> list(Authentication authentication) { return queries.list(authentication); }
    @GetMapping("/{id}")
    public Branch get(@PathVariable UUID id, Authentication authentication) { return queries.get(id, authentication); }
}
