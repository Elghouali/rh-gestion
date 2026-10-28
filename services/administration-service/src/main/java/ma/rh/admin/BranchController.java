package ma.rh.admin;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class BranchController {
 private final BranchQueryService queries;
 public BranchController(BranchQueryService queries){this.queries=queries;}
 @GetMapping("/branches") public List<Branch> list(Authentication authentication){return queries.list(authentication);}
 @GetMapping("/branches/{id}") public Branch get(@PathVariable UUID id,Authentication authentication){return queries.get(id,authentication);}
 @GetMapping("/branch-directory") public List<Branch> destinations(Authentication authentication){return queries.directory(authentication);}
}