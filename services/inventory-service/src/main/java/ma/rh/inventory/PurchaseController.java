package ma.rh.inventory;
import static ma.rh.inventory.PurchaseModels.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/purchases")
public class PurchaseController {
 private final Purchases purchases;
 public PurchaseController(Purchases purchases){this.purchases=purchases;}
 @GetMapping public Models.Page<Order> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return purchases.list(jwt,q,page,size);}
 @GetMapping("/{id}") public Order get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return purchases.get(jwt,id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Order create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody OrderInput input){return purchases.create(jwt,key,input);}
 @PostMapping("/{id}/receipts") @ResponseStatus(HttpStatus.CREATED) public Delivery receive(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody ReceiptInput input){return purchases.receive(jwt,key,id,input);}
 @PostMapping("/{id}/receipts/{receiptId}/inspections") @ResponseStatus(HttpStatus.CREATED) public Delivery inspect(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@PathVariable UUID receiptId,@Valid @RequestBody InspectionInput input){return purchases.inspect(jwt,key,id,receiptId,input);}
 @PostMapping("/{id}/close") @ResponseStatus(HttpStatus.CREATED) public Order close(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody CloseInput input){return purchases.close(jwt,key,id,input);}
}
