package ma.rh.inventory;

import static ma.rh.inventory.Models.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class InventoryController {
 private final Catalogue catalogue;private final StockService stock;private final Transfers transfers;private final Access access;
 public InventoryController(Catalogue catalogue,StockService stock,Transfers transfers,Access access){this.catalogue=catalogue;this.stock=stock;this.transfers=transfers;this.access=access;}
 @GetMapping("/catalog/products") public Page<Product> products(@AuthenticationPrincipal Jwt user,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return catalogue.products(user,q,page,size);}
 @GetMapping("/catalog/variants") public Page<Variant> variants(@AuthenticationPrincipal Jwt user,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return catalogue.variants(user,q,page,size);}
 @PostMapping("/catalog/products") @ResponseStatus(HttpStatus.CREATED) public Product createProduct(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody ProductInput input){return catalogue.create(user,key,input);}
 @PutMapping("/catalog/products/{id}") public Product updateProduct(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody ProductUpdate input){return catalogue.update(user,key,id,input);}
 @PostMapping("/catalog/variants") @ResponseStatus(HttpStatus.CREATED) public Variant createVariant(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody VariantInput input){return catalogue.create(user,key,input);}
 @PutMapping("/catalog/variants/{id}") public Variant updateVariant(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody VariantUpdate input){return catalogue.update(user,key,id,input);}
 @GetMapping("/stock/balances") public Page<StockRow> balances(@AuthenticationPrincipal Jwt user,@RequestParam(required=false) UUID branchId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return stock.balances(user,branchId,q,page,size);}
 @GetMapping("/stock/movements") public Page<MovementRow> movements(@AuthenticationPrincipal Jwt user,@RequestParam(required=false) UUID branchId,@RequestParam(required=false) UUID variantId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return stock.movements(user,branchId,variantId,page,size);}
 @PostMapping("/stock/openings") @ResponseStatus(HttpStatus.CREATED) public Operation opening(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Opening input){return stock.opening(user,key,input);}
 @PostMapping("/stock/adjustments") @ResponseStatus(HttpStatus.CREATED) public Operation adjustment(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Adjustment input){return stock.adjust(user,key,input);}
 @GetMapping("/stock/destinations") public List<Site> destinations(@AuthenticationPrincipal Jwt user){return access.directory(user);}
 @GetMapping("/stock/transfers") public Page<Transfer> transfers(@AuthenticationPrincipal Jwt user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return transfers.list(user,page,size);}
 @GetMapping("/stock/transfers/{id}") public Transfer transfer(@AuthenticationPrincipal Jwt user,@PathVariable UUID id){return transfers.get(user,id);}
 @PostMapping("/stock/transfers") @ResponseStatus(HttpStatus.CREATED) public Transfer dispatch(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody TransferInput input){return transfers.dispatch(user,key,input);}
 @PostMapping("/stock/transfers/{id}/receipts") @ResponseStatus(HttpStatus.CREATED) public Transfer receipt(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody Receipt input){return transfers.receive(user,key,id,input,false);}
 @PostMapping("/stock/transfers/{id}/losses") @ResponseStatus(HttpStatus.CREATED) public Transfer loss(@AuthenticationPrincipal Jwt user,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody Receipt input){return transfers.receive(user,key,id,input,true);}
}
